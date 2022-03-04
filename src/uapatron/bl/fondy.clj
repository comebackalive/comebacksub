(ns uapatron.bl.fondy
  (:require [clojure.string :as str]
            [pandect.algo.sha1 :as sha1]

            [uapatron.db :as db]
            [uapatron.config :as config]
            [uapatron.utils :as utils]
            [clojure.tools.logging :as log]))


(set! *warn-on-reflection* true)


(def POST-URL "https://pay.fondy.eu/api/checkout/url/")

(def DESC "Допомога savelife.in.ua")


(def TRANSACTION-LOG
  [:transaction
   :amount
   :type
   :user_id])


(def CARD-FIELDS
  [:user_id
   :token
   :card_pan
   :card_info
   :is_deleted])


(def SETTINGS-FIELDS
  [:user_id
   :default_card_id
   :schedule_offset])


(defn sign [ctx]
  (let [s      (->> (sort ctx)
                    (map val)
                    (str/join \|))
        full-s (str (config/MERCHANT-KEY) "|" s)]
    (log/debug "signature string" s)
    (assoc ctx :signature (sha1/sha1 full-s))))


(defn verify [ctx]
  (= (sign (dissoc ctx :signature))
     (:signature ctx)))


(defn make-order-id [uid] (str uid ":" (utils/uuid)))
(defn oid->uid [order-id] (first  (str/split order-id \:)))
(defn oid->tx  [order-id] (second (str/split order-id \:)))


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
   :sender_email        (:email user)
   :response_url        (str "https://" (config/DOMAIN) "/payment-result")
   :server_callback_url (str "https://" (config/DOMAIN) "/api/payment-callback")})


(defn save-transaction-q
  [payment-ctx]
  {:insert-into :transaction_log
   :values      [(select-keys payment-ctx TRANSACTION-LOG)]
   :returning   [:transaction]})


(defn upsert-card-q
  [card-ctx]
  (let [cctx (utils/remove-nils (select-keys card-ctx CARD-FIELDS))]
    {:insert-into :cards
     :values      [cctx]
     :upsert      {:on-conflict   [:user_id :card_pan]
                   :do-update-set {:fields (keys cctx)}}
     :returning   [:id]}))


(defn upsert-settings-q
  [settings-ctx]
  (let [ctx (utils/remove-nils (select-keys settings-ctx SETTINGS-FIELDS))]
    {:insert-into :settings
     :values      [ctx]
     :upsert      {:on-conflict   [:user_id]
                   :do-update-set {:fields (keys ctx)}}}))


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


(defn handle-callback!
  [{:keys [order_id
           amount
           status
           card-info]}]
  (let [extracted-uid (oid->uid order_id)
        now nil #_(t/now)]
    (db/tx (db/q (save-transaction-q {:transaction order_id
                                            :amount      amount
                                            :user_id     extracted-uid
                                            :type        status
                                            :data        {}}))
      (db/q (upsert-settings-q {:user_id         extracted-uid
                                :default_card_id (:id (db/one (upsert-card-q  card-info)))}))
      (schedule-new-order! extracted-uid now))))

(def some-sample-callback {:amount               "4400",
                           :settlement_currency  "",
                           :fee                  "",
                           :actual_currency      "UAH",
                           :response_description "",
                           :rectoken_lifetime    "",
                           :settlement_date      "",
                           :product_id           "",
                           :rrn                  "",
                           :settlement_amount    "0",
                           :signature            "e9093a930250d5cc8b7ed1e530c4d27d62924c38"
                           :merchant_id          "1397120",
                           :sender_cell_phone    "",
                           :card_bin             "444455",
                           :order_status         "approved",
                           :merchant_data        "",
                           :payment_system       "card",
                           :approval_code        "123456",
                           :parent_order_id      "",
                           :response_code        "",
                           :currency             "UAH",
                           :tran_type            "purchase",
                           :reversal_amount      "0",
                           :order_id             "1:6a49f23d-d57f-4f1f-bc0d-f6c6f3bff68c",
                           :order_time           "04.03.2022 17:59:22",
                           :response_status      "success",
                           :rectoken             "",
                           :actual_amount        "4400",
                           :payment_id           "497061094",
                           :sender_email         "pmapcat@gmail.com",
                           :masked_card          "444455XXXXXX1111",
                           :sender_account       "",
                           :verification_status  "",
                           :eci                  "5",
                           :card_type            "VISA"})

(defn save-result
  [req])


