(ns uapatron.bl.fondy
  (:require [clojure.string :as str]
            [pandect.algo.sha1 :as sha1]

            [uapatron.db :as db]
            [uapatron.time :as t]
            [uapatron.config :as config]
            [uapatron.utils :as utils]
            [clojure.tools.logging :as log]))


(set! *warn-on-reflection* true)


(def POST-URL "https://pay.fondy.eu/api/checkout/url/")

(def DESC "Допомога savelife.in.ua")


(defn sign [ctx]
  (let [s      (->> (sort ctx)
                 (map val)
                 (filter (comp not empty?))
                 (str/join \|))
        full-s (str (config/MERCHANT-KEY) "|" s)]
    (log/debug "signature string" s)
    (assoc ctx :signature (sha1/sha1 full-s))))


(defn verify! [ctx]
  (when-not (= (:signature (sign (dissoc ctx :signature :response_signature_string)))
              (:signature ctx))
    (throw (ex-info "Bad signature, check credentials" ctx))))


(defn make-order-id [uid] (str uid ":" (utils/uuid)))
(defn oid->uid [order-id] (utils/parse-int (first  (str/split order-id #":"))))
(defn oid->tx  [order-id] (utils/parse-uuid (second (str/split order-id #":"))))


(defn user-with-settings-and-default-card-q
  [uid]
  {:from   [[:users :u]]
   :join   [[:payment_settings :ps] [:= :ps.user_id :u.id]
            [:users :u] [:= :u.id :tl.user_id]
            [:cards :c] [:= :ps.default_card_id :c.id]]
   :select [:ps.schedule_offset
            :u.name
            :u.email
            :u.phone
            :ps.default_payment_amount
            :c.token
            :c.card_pan
            :c.card_info]
   :where  [:and
            [:= :u.id uid]
            [:= :c.is_deleted nil]]})


(defn make-link-ctx
  [user amount]
  {:order_id            (make-order-id (:id user))
   :order_desc          "Test payment"
   :merchant_id         (config/MERCHANT-ID)
   :currency            (first ["UAH" "RUB" "USD" "EUR" "GBP" "CZK"])
   :amount              (str (* amount 100))
   :required_rectoken   "Y"
   :lang                (first ["uk"
                                "en"
                                "lv"
                                "fr"
                                "cs"
                                "ro"
                                "ru"
                                "it"
                                "sk"
                                "pl"
                                "es"
                                "hu"
                                "de"])
   :sender_email        (:email user)
   :response_url        (str "https://" (config/DOMAIN) "/payment-result")
   :server_callback_url (str "https://" (config/DOMAIN) "/api/payment-callback")})


(defn save-transaction-q
  [payment-ctx]
  {:insert-into :transaction_log
   :values      [payment-ctx]
   :returning   [:transaction]})


(defn upsert-card-q
  [card-ctx]
  {:insert-into   :cards
   :values        [card-ctx]
   :on-conflict   [:user_id :card_pan]
   :do-update-set (into [] (keys card-ctx))
   :returning     [:id]})


(defn upsert-settings-q
  [settings-ctx]
  {:insert-into   :payment_settings
   :values        [settings-ctx]
   :on-conflict   [:user_id]
   :do-update-set {:fields (keys settings-ctx)}})


(defn get-payment-link
  [user amount freq]
  (let [ctx (make-link-ctx user amount)
        _   (log/debug "fondy ctx" (pr-str ctx))
        res (-> (utils/json-http! :post POST-URL {:request (sign ctx)})
                :response)]
    (prn res)
    (if (= "success" (:response_status res))
      (:checkout_url res)
      (throw (ex-info "Error getting payment link" res)))))

#_(println (get-payment-link {:id    "1"
                              :email "pmapcat@gmail.com"} "1" "weekly"))



(defn calculate-next-charge-date
  [now offset]
  ;; todo
  (+ now offset))


(defn schedule-new-order!
  [uid now]
  (let [new-order (make-order-id uid)
        settings  (db/one (user-with-settings-and-default-card-q uid))]
    (db/q (save-transaction-q {:transaction   (oid->tx new-order)
                               :order_id      new-order
                               :amount        (:default_payment_amount settings)
                               :type          :Scheduled
                               :scheduled_for (calculate-next-charge-date now (:schedule_offset settings))
                               :data          {}
                               :user_id       uid}))))


(defn process-approved!
  [{:keys [amount
           actual_currency
           rectoken
           #_rectoken_lifetime
           masked_card
           order_id]
    :as   resp}]
  (db/tx
    (let [card-id (when rectoken
                    (:id (db/one (upsert-card-q  {:user_id  (oid->uid order_id)
                                                  :token    rectoken
                                                  :card_pan masked_card}))))]
      (db/tx (db/q (save-transaction-q {:transaction (utils/parse-uuid (oid->tx order_id))
                                        :amount      (utils/parse-int amount)
                                        :order_id    order_id
                                        :card_id     card-id
                                        :user_id     (oid->uid order_id)
                                        :type        (db/->transaction-type :Approved)
                                        :currency    (db/->currency-type actual_currency)
                                        :data        (db/as-jsonb resp)}))
        (when rectoken
          (db/q (upsert-settings-q {:user_id         (oid->uid order_id)
                                    :default_card_id card-id}))
          #_(schedule-new-order! (oid->uid order_id) (t/now)))))))


(defn write-processing!
  [status {:keys [amount
                  actual_currency
                  order_id]
           :as   resp}]
  (db/q (save-transaction-q {:transaction (utils/parse-uuid (oid->tx order_id))
                             :amount      (utils/parse-int amount)
                             :order_id    order_id
                             :user_id     (oid->uid order_id)
                             :type        (db/->transaction-type status)
                             :currency    (db/->currency-type actual_currency)
                             :data        (db/as-jsonb resp)})))


(def TRANSACT-MAPPING
  {:created    (partial :Created      write-processing!)
   :processing (partial :InProcessing write-processing!)
   :declined   (partial :Declined     write-processing!)
   :reversed   (partial :Voided       write-processing!)
   :expired    (partial :Expired      write-processing!)
   :approved   process-approved!})


(defn process-transaction!
  [{:keys [order_status]
    :as   req}]
  (verify! req)
  (if-let [processor (get TRANSACT-MAPPING (keyword order_status))]
    (processor req)
    (log/warn "Unknown status type: " order_status)))


