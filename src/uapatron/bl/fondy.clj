(ns uapatron.bl.fondy
  (:require [clojure.string]
            [pandect.algo.sha1 :as sha1]
            [uapatron.db :as db]
            [uapatron.config :as config]
            [uapatron.utils :as utils]))

(def POST-URL "https://pay.fondy.eu/api/checkout/url/")

(def TRANSACTION-LOG [:transaction :amount :type :user_id])

(def CARD-FIELDS [:user_id :token :card_pan :card_info :is_deleted])

(def SETTINGS-FIELDS [:user_id :default_card_id :schedule_offset])

(defn sign [ctx] (assoc ctx :signature (sha1/sha1 (str (config/MERCHANT-KEY) "|" (clojure.string/join \| (sort (vals ctx)))))))

(defn verify [ctx] (= (sign (dissoc ctx :signature)) (:signature ctx)))


(defn make-order-id [uid] (str uid ":" (utils/uuid)))
(defn uid-from-order-id [order-id] (first (clojure.string/split order-id \:)))
(defn transaction-from-order-id [order-id] (second (clojure.string/split order-id \:)))


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
   :where  [:and [:= :u.id uid]
            [:= :c.is_deleted nil]]})


(defn make-link-ctx
  [uid]
  (sign {:order_id            (make-order-id uid)
         :order_desc          "Test payment"
         :merchant_id         (config/MERCHANT-ID)
         :currency            (first ["UAH" "RUB" "USD" "EUR" "GBP" "CZK"])
         :amount              "1"
         :response_url        (str (config/DOMAIN) "/payment-result")
         :server_callback_url (str (config/DOMAIN) "/api/payment-callback")}))


(defn save-transaction-q
  [payment-ctx]
  {:insert-into :transaction_log
   :values      [(select-keys payment-ctx TRANSACTION-LOG)]
   :returning   [:transaction]})


(defn upsert-card-q
  [card-ctx]
  (let [cctx (utils/remove-nil-vals (select-keys card-ctx CARD-FIELDS))]
    {:insert-into :cards
     :values      [cctx]
     :upsert      {:on-conflict   [:user_id :card_pan]
                   :do-update-set {:fields (keys cctx)}}
     :returning   [:id]}))


(defn upsert-settings-q
  [settings-ctx]
  (let [ctx (utils/remove-nil-vals (select-keys settings-ctx SETTINGS-FIELDS))]
    {:insert-into :settings
     :values      [ctx]
     :upsert      {:on-conflict   [:user_id]
                   :do-update-set {:fields (keys ctx)}}}))


(defn make-link!
  [uid]
  (let [{:keys [order_id]
         :as   ctx} (make-link-ctx uid)]
    (db/tx
      (let [status (utils/json-http! :post POST-URL (make-link-ctx uid))]
        (db/q (save-transaction-q {:transaction (transaction-from-order-id order_id)
                                   :amount      (:amount ctx)
                                   :type        status
                                   :data        {}
                                   :user_id     uid}))
        ;; TODO: think about it, transactions that are already scheduled
        ;; and default_amounts, must add it up thoroughly
        (db/q (upsert-settings-q {:default_payment_amount (:amount ctx)
                                  :user_id                uid}))
        (:url status)))))


(defn calculate-next-charge-date
  [now offset]
  ;; todo
  (+ now offset))


(defn schedule-new-order!
  [uid now]
  (let [new-order (make-order-id uid)
        settings  (db/one (user-with-settings-and-default-card-q uid))]
    (db/q (save-transaction-q {:transaction   (transaction-from-order-id new-order)
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
  (let [extracted-uid (uid-from-order-id order_id)
        now nil #_(t/now)]
    (db/tx (db/q (save-transaction-q {:transaction order_id
                                            :amount      amount
                                            :user_id     extracted-uid
                                            :type        status
                                            :data        {}}))
      (db/q (upsert-settings-q {:user_id         extracted-uid
                                      :default_card_id (:id (db/one (upsert-card-q  card-info)))}))
      (schedule-new-order! extracted-uid now))))

