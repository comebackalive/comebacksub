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
(def RECURRING-URL "https://pay.fondy.eu/api/recurring")

(def DESC "Допомога savelife.in.ua")

(def DESC-RECUR "Допомога savelife.in.ua (рекурентний платіж)")


(defn sign [ctx]
  (let [s      (->> (sort ctx)
                 (map val)
                 (filter (comp not empty? str))
                 (str/join \|))
        full-s (str (config/MERCHANT-KEY) "|" s)]
    (assoc ctx :signature (sha1/sha1 full-s))))


(defn verify! [ctx]
  (when-not (= (:signature (sign (dissoc ctx :signature :response_signature_string)))
              (:signature ctx))
    (throw (ex-info "Bad signature, check credentials" ctx))))


(defn make-order-id [uid] (str uid ":" (utils/uuid)))
(defn oid->uid [order-id] (utils/parse-int (first  (str/split order-id #":"))))
(defn oid->tx  [order-id] (utils/parse-uuid (second (str/split order-id #":"))))


(defn get-ctx-for-recurrent-payment-q
  [uid]
  {:from   [[:users :u]]
   :join   [[:payment_settings :ps] [:= :ps.user_id :u.id]
            [:cards :c] [:= :ps.default_card_id :c.id]]
   :select [[:u.id :user_id]
            [:u.email :user_email]
            :ps.default_currency
            :ps.frequency
            :ps.default_payment_amount
            :c.token]
   :where  [:and [:= :u.id uid]
            #_[:= :c.is_deleted nil]]})


(defn make-link-ctx
  [user amount freq]
  {:order_id            (make-order-id (:id user))
   :order_desc          DESC
   :merchant_id         (config/MERCHANT-ID)
   :currency            (first ["UAH" "RUB" "USD" "EUR" "GBP" "CZK"])
   :amount              (str (* amount 100))
   :merchant_data       (pr-str {:freq freq})
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


(defn make-recurrent-payment-ctx
  [{:keys [user_id
           user_email
           default_currency
           frequency
           default_payment_amount
           token]}]
  {:order_id            (make-order-id user_id)
   :order_desc          DESC-RECUR
   :merchant_id         (config/MERCHANT-ID)
   :currency            default_currency
   :amount              default_payment_amount
   :merchant_data       (pr-str {:recurrent true :freq frequency})
   :required_rectoken   "Y"
   :rectoken            token
   :sender_email        user_email
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
  (let [ctx (make-link-ctx user amount freq)
        _   (log/debug "fondy ctx" (pr-str ctx))
        res (-> (utils/json-http! :post POST-URL {:request (sign ctx)})
                :response)]
    (prn res)
    (if (= "success" (:response_status res))
      (:checkout_url res)
      (throw (ex-info "Error getting payment link" res)))))

#_(println (get-payment-link {:id    "1" :email "pmapcat@gmail.com"} "1" "weekly"))

(def FREQ-MAP
  {"day"  1
   "week" 7})


(defn calculate-next-payment-at
  [now freq]
  (t/+days now (get FREQ-MAP freq)))


(defn already?
  [order-id status]
  (db/one {:from   [:transaction_log]
           :select [1]
           :where  [:and [:= :type (db/->transaction-type status)]
                    [:= :transaction (oid->tx order-id)]]}))


(defn process-approved!
  [{:keys [amount
           actual_currency
           rectoken
           #_rectoken_lifetime
           masked_card
           merchant_data
           order_id]
    :as   resp}]
  (db/tx
    (let [card-id  (when rectoken (:id (db/one (upsert-card-q  {:user_id  (oid->uid order_id)
                                                                :token    rectoken
                                                                :card_pan masked_card}))))
          our-data (read-string merchant_data)]
      (db/tx (db/q (save-transaction-q {:transaction (utils/parse-uuid (oid->tx order_id))
                                        :amount      (utils/parse-int amount)
                                        :order_id    order_id
                                        :card_id     card-id
                                        :user_id     (oid->uid order_id)
                                        :type        (db/->transaction-type :Approved)
                                        :currency    (db/->currency-type actual_currency)
                                        :data        (db/as-jsonb resp)}))
        (when rectoken
          (db/q (upsert-settings-q (utils/remove-nils
                                     {:user_id                (oid->uid order_id)
                                      :default_card_id        card-id
                                      :frequency              (:freq our-data)
                                      :next_payment_at       (calculate-next-payment-at
                                                               (t/now)
                                                               (:freq our-data))
                                      :default_payment_amount (utils/parse-int amount)
                                      :default_currency       (db/->currency-type actual_currency)}))))))))


(defn write-processing!
  [status {:keys [amount
                  actual_currency
                  order_id]
           :as   resp}]
  (db/q (save-transaction-q {:transaction (utils/parse-uuid (oid->tx order_id))
                             :amount      (when-not (empty? amount)
                                            (utils/parse-int amount))
                             :order_id    order_id
                             :user_id     (oid->uid order_id)
                             :type        (db/->transaction-type status)
                             :currency    (when-not (empty? actual_currency)
                                            (db/->currency-type actual_currency))
                             :data        (db/as-jsonb resp)})))


(def TRANSACT-MAPPING
  {:created    (partial write-processing! :Created      )
   :processing (partial write-processing! :InProcessing )
   :declined   (partial write-processing! :Declined     )
   :reversed   (partial write-processing! :Voided       )
   :expired    (partial write-processing! :Expired      )
   :approved   process-approved!})


(defn process-transaction!
  [{:keys [order_status]
    :as   req}]
  (verify! req)
  (if-let [processor (get TRANSACT-MAPPING (keyword order_status))]
    (processor req)
    (log/warn "Unknown status type: " order_status)))


(defn process-recurrent-payment!
  [uid]
  (when-let [payment-params (db/one (get-ctx-for-recurrent-payment-q uid))]
    (let [ctx (make-recurrent-payment-ctx payment-params)
          _   (log/debug "fondy ctx" (pr-str ctx))
          res (-> (utils/json-http! :post RECURRING-URL {:request (sign ctx)})
                :response)]
      (prn res)
      (if (= "success" (:response_status res))
        (process-transaction! res)
        (throw (ex-info "Recurrent payment error" res))))))

;; todo: fix invalid signature
#_(process-recurrent-payment! 1)
