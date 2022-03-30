(ns uapatron.bl.fondy
  (:require [clojure.string :as str]
            [pandect.algo.sha1 :as sha1]
            [clojure.tools.logging :as log]
            [next.jdbc :as jdbc]
            [cheshire.core :as json]

            [uapatron.db :as db]
            [uapatron.time :as t]
            [uapatron.config :as config]
            [uapatron.utils :as utils]
            [clojure.edn :as edn]
            [uapatron.email :as email]
            [uapatron.auth :as auth]))


(set! *warn-on-reflection* true)


(def POST-URL "https://pay.fondy.eu/api/checkout/url/")
(def RECURRING-URL "https://pay.fondy.eu/api/recurring")
(def REFUND-URL "https://pay.fondy.eu/api/reverse/order_id")


(defn sign [ctx]
  (let [s      (->> (sort ctx)
                    (map val)
                    (remove (comp empty? str))
                    (str/join \|))
        full-s (str (config/MERCHANT-KEY) "|" s)]
    (assoc ctx :signature (sha1/sha1 full-s))))


(defn verify! [ctx]
  (let [signed (sign (dissoc ctx :signature :response_signature_string))]
    (when-not (= (:signature signed) (:signature ctx))
      (throw (ex-info "Bad signature, check credentials"
               {:theirs             (:signature ctx)
                :ours               (:signature sign)
                ::invalid-signature true})))))


(defn make-order-id []
  (str "SUB-" (utils/uuid)))


(defn make-desc
  ([freq amount currency] (make-desc freq amount currency nil))
  ([freq amount currency recurrent]
   (let [fmt (if recurrent
               "Donation to Come Back Alive (%s %s %sly) - regular payment"
               "Donation to Come Back Alive (%s %s %sly)")]
     (format fmt amount currency freq))))


(defn make-link-ctx
  [user {:keys [freq amount currency]}]
  {:order_id            (make-order-id)
   :order_desc          (make-desc freq amount currency)
   :merchant_id         (config/MERCHANT-ID)
   :currency            currency
   :amount              (* amount 100)
   :merchant_data       (pr-str {:user_id (:id user)
                                 :freq    freq})
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
   :response_url        (str "https://" (config/DOMAIN) "/payment/result")
   :server_callback_url (str "https://" (config/DOMAIN) "/api/payment-callback")})


(defn make-recurrent-payment-ctx
  [{:keys [id
           user_id
           user_email
           currency
           frequency
           amount
           token]}]
  {:order_id            (make-order-id)
   :order_desc          (make-desc frequency amount currency true)
   :merchant_id         (config/MERCHANT-ID)
   :currency            currency
   :amount              (* amount 100)
   :merchant_data       (pr-str {:id        id
                                 :user_id   user_id
                                 :freq      frequency
                                 :recurrent true})
   :rectoken            token
   :sender_email        user_email
   :server_callback_url (str "https://" (config/DOMAIN) "/api/payment-callback")})


(defn make-refund-ctx [{:keys [order_id amount currency msg]}]
  {:order_id    order_id
   :currency    currency
   :amount      (* amount 100)
   :comment     msg
   :merchant_id (config/MERCHANT-ID)})


(defn save-transaction-q
  [payment-ctx]
  {:insert-into :transaction_log
   :values      [payment-ctx]
   :returning   [:id :type :order_id]})


(defn upsert-card-q
  [card]
  {:insert-into   :cards
   :values        [card]
   :on-conflict   [:user_id :card_pan]
   :do-update-set (keys card)
   :returning     [:id :card_label]})


(defn save-settings-q
  [ctx]
  (assert (:user_id ctx) "user_id is required")
  (if (:id ctx)
    {:update :payment_settings
     :set    ctx
     :where  [:and
              [:= :id (:id ctx)]
              [:= :user_id (:user_id ctx)]]}

    {:insert-into   :payment_settings
     :values        [ctx]
     :on-conflict   [:user_id]
     :do-update-set (keys ctx)}))


(defn get-payment-link
  [user config]
  (let [ctx (make-link-ctx user config)
        _   (log/debug "fondy ctx" (pr-str ctx))
        res (-> (utils/post! POST-URL {:request (sign ctx)})
                :response)]
    (if (= "success" (:response_status res))
      (:checkout_url res)
      (throw (ex-info "Error getting payment link" res)))))


(comment
  (println (get-payment-link {:id "1" :email "pmapcat@gmail.com"}
             {:freq "weekly" :amount 1 :currency "UAH"})))


(def FREQ-MAP
  {"day"   (partial t/+days 1)
   "week"  (partial t/+days 7)
   "month" (partial t/+months 1)})


(defn calculate-next-payment-at
  [now freq]
  (let [inc-date (get FREQ-MAP freq)]
    (inc-date now)))


(defn already?
  [order-id status]
  (db/one {:from   [:transaction_log]
           :select [1]
           :where  [:and
                    [:= :type (db/->transaction-type status)]
                    [:= :transaction order-id]]}))

(defn res->payload [res]
  (or (:payload res)
      (edn/read-string (:merchant_data res))))


(def STATUS-MAPPING
  {"created"    :Created
   "processing" :InProcessing
   "declined"   :Declined
   "reversed"   :Voided
   "expired"    :Expired
   "approved"   :Approved
   "refunded"   :Refunded})


(defn make-card [{:keys [rectoken rectoken_lifetime masked_card] :as res}]
  (when rectoken
    (let [payload        (res->payload res)
          additional     (some-> res :additional_info json/parse-string)
          payment_method (get additional "payment_method")]
      {:user_id          (:user_id payload)
       :token            rectoken
       :token_expires_at (when (not-empty rectoken_lifetime)
                           (t/parse-dt rectoken_lifetime))
       :card_pan         masked_card
       :card_label       (case payment_method
                           "apple"     "applepay"
                           "googlepay" "googlepay"
                           "card"      masked_card
                           nil         masked_card
                           payment_method)})))


(defn write-transaction! [{:keys [order_status amount currency order_id]
                           :as   res}
                          & [{:keys [card-id processed?]}]]
  (let [payload (res->payload res)
        status  (or (get STATUS-MAPPING order_status)
                    (throw (ex-info "Uknown status" res)))
        amount  (when (seq amount)
                  (int (/ (utils/parse-int amount) 100)))]
    (db/one (save-transaction-q
              {:amount    amount
               :order_id  order_id
               :card_id   card-id
               :user_id   (:user_id payload)
               :type      (db/->transaction-type status)
               :currency  (when-not (empty? currency)
                            (db/->currency-type currency))
               :data      (db/as-jsonb (dissoc res :payload))
               :processed processed?}))))


(defn process-approved! [{:keys [amount currency masked_card]
                          :as   res}]
  (db/tx
    (let [payload         (res->payload res)
          amount          (int (/ (utils/parse-int amount) 100))
          next-payment-at (calculate-next-payment-at (t/now) (:freq payload))
          card            (some-> (make-card res) upsert-card-q db/one)
          settings        (cond-> {:user_id         (:user_id payload)
                                   :card_id         (:id card)
                                   :frequency       (:freq payload)
                                   :next_payment_at next-payment-at
                                   :paused_at       nil
                                   :amount          amount
                                   :currency        (db/->currency-type currency)}
                            (:id payload)
                            (assoc :id (:id payload)))]
      (when card
        ;; we're allowing only one schedule per user right now
        (db/one (save-settings-q settings)))
      (write-transaction! res
        {:card-id    (:id card)
         :processed? true})
      (email/receipt! (:email (auth/id->user (:user_id payload)))
        {:amount          amount
         :currency        currency
         :card_label      (:card_label card)
         :masked_card     masked_card
         :next_payment_at next-payment-at}))))


(defn process-declined! [{:keys [amount currency]
                          :as   res}]
  (db/tx
    (let [payload         (res->payload res)
          amount          (int (/ (utils/parse-int amount) 100))
          ;; there is an id in payload when it's recurring payment
          next-payment-at (when (:id payload)
                            (calculate-next-payment-at (t/now) "day"))
          card            (some-> (make-card res) upsert-card-q db/one)]
      (when (:id payload)
        (db/one (save-settings-q {:id              (:id payload)
                                  :user_id         (:user_id payload)
                                  :next_payment_at next-payment-at})))
      (write-transaction! res
        {:card-id    (:id card)
         :processed? true})
      (email/decline! (:email (auth/id->user (:user_id payload)))
        {:amount          amount
         :currency        currency
         :masked_card     (:card_label card)
         :next_payment_at next-payment-at}))))


(defn process-transaction!
  [{:keys [order_status order_id] :as res}]
  (log/info "incoming data" order_status order_id)
  (verify! res)
  (let [res (assoc res :payload (res->payload res))]
    (case order_status
      "approved" (process-approved! res)
      "declined" (process-declined! res)
      (write-transaction! res))))


(defn set-begin-charging!
  [uid id]
  (->> (db/one (save-settings-q {:id                id
                                   :user_id           uid
                                   :begin_charging_at (t/now)}))
       ::jdbc/update-count
       pos?))


(defn set-paused!
  [uid id]
  (->> (db/one (save-settings-q {:id        id
                                   :user_id   uid
                                   :paused_at (t/now)}))
       ::jdbc/update-count
       pos?))


(defn set-resumed!
  [uid id]
  (->> (db/one (save-settings-q {:id        id
                                   :user_id   uid
                                   :paused_at nil}))
       ::jdbc/update-count
       pos?))


(defn paused?
  [settings]
  (not (nil? (:paused_at settings))))


(defn double-charge?
  [planned last-started]
  (if (nil? last-started)
    false ;; never charged before
    (case (t/compare-times (t/at-midnight last-started) (t/at-midnight planned))
      := true
      :> true
      :< false)))

;; inline test 😁
(doseq [[[planned      last-started] result]
        {["2021-12-31" "2021-12-30"] false
         ["2021-12-30" "2021-12-31"] true
         ["2021-12-30" "2021-12-30"] true
         ["2021-12-30" nil]          false}]
  (assert (= (double-charge?
               (t/parse-date planned)
               (t/parse-date last-started)) result)))


(defn recurrent-payment-q
  [id]
  {:from   [[:payment_settings :ps]]
   :join   [[:users :u] [:= :ps.user_id :u.id]
            [:cards :c] [:= :ps.card_id :c.id]]
   :select [[:u.id :user_id]
            [:u.email :user_email]
            :ps.id
            :ps.currency
            :ps.frequency
            :ps.begin_charging_at
            :ps.next_payment_at
            :ps.paused_at
            :ps.amount
            :c.token]
   :where  [:and [:= :ps.id id]
            #_[:= :c.is_deleted nil]]
   :for    [:update :ps :nowait]})


(defn process-recurrent-payment! [id]
  (db/tx
    (let [payment-params (db/one (recurrent-payment-q id))
          uid            (:user_id payment-params)]
      (cond
        (paused? payment-params)
        (log/info "payment is paused, for user" (:user_id payment-params))

        (double-charge?
          (:next_payment_at payment-params)
          (:begin_charging_at payment-params))
        (log/error "double charge, skipping"
          {:id          id
           :uid         (:user_id payment-params)
           :last-charge (:begin_charging_at payment-params)
           :planned     (:next_payment_at payment-params)})

        :else
        (let [_        (log/info "charging user" (:user_id payment-params))
              ctx      (make-recurrent-payment-ctx payment-params)
              _        (log/debug "fondy ctx" (pr-str ctx))
              started? (set-begin-charging! uid (:id payment-params))
              res      (when started?
                         (utils/post! RECURRING-URL {:request (sign ctx)}))]
          (cond
            (not started?)
            (log/error "could not start" {:uid uid :id (:id payment-params)})

            (:response res)
            ;; we just store here and wait for callback for processing
            (write-transaction! (:response res))

            :else
            (let [original (or (-> res meta :original)
                               res)]
              (log/error "Recurrent payment error" original))))))))


(defn refund! [order-id]
  (let [order  (db/one {:from   [:transaction_log]
                        :select [:user_id
                                 :order_id
                                 :currency
                                 :amount]
                        :where  [:and
                                 [:= :order_id order-id]
                                 [:= :type (db/->transaction-type :Approved)]]})
        ctx    (make-refund-ctx order)
        res    (-> (utils/post! REFUND-URL {:request (sign ctx)})
                   :response)
        amount (when (seq (:reversal_amount res))
                 (int (/ (utils/parse-int (:reversal_amount res)) 100)))]
    (db/q (save-transaction-q
            {:amount    (or amount 0)
             :order_id  order-id
             :user_id   (:user_id order)
             :type      (db/->transaction-type :Refunded)
             :currency  (db/->currency-type (:currency order))
             :data      (db/as-jsonb res)
             :processed true}))
    res))
