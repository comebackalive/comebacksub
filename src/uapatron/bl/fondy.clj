(ns uapatron.bl.fondy
  (:require [clojure.string :as str]
            [pandect.algo.sha1 :as sha1]
            [clojure.tools.logging :as log]
            [next.jdbc :as jdbc]

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


(defn sign [ctx]
  (let [s      (->> (sort ctx)
                    (map val)
                    (remove (comp empty? str))
                    (str/join \|))
        full-s (str (config/MERCHANT-KEY) "|" s)]
    (assoc ctx :signature (sha1/sha1 full-s))))


(defn verify! [ctx]
  (when-not (= (:signature (sign (dissoc ctx :signature :response_signature_string)))
               (:signature ctx))
    (throw (ex-info "Bad signature, check credentials" ctx))))


(defn make-order-id [] (utils/uuid))


(defn make-desc
  ([freq amount currency] (make-desc freq amount currency nil))
  ([freq amount currency recurrent]
   (let [fmt (if recurrent
               "Donation to Come Back Alive (%s %s %sly) - regular payment"
               "Donation to Come Back Alive (%s %s %sly)")]
     (format fmt amount currency freq))))


(defn recurrent-payment-q
  [uid]
  {:from   [[:users :u]]
   :join   [[:payment_settings :ps] [:= :ps.user_id :u.id]
            [:cards :c] [:= :ps.card_id :c.id]]
   :select [[:u.id :user_id]
            [:u.email :user_email]
            :ps.currency
            :ps.frequency
            :ps.begin_charging_at
            :ps.next_payment_at
            :ps.paused_at
            :ps.amount
            :c.token]
   :where  [:and [:= :u.id uid]
            #_[:= :c.is_deleted nil]]})


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
   :merchant_data       (pr-str {:user_id   user_id
                                 :freq      frequency
                                 :id        id
                                 :recurrent true})
   :rectoken            token
   :sender_email        user_email
   :server_callback_url (str "https://" (config/DOMAIN) "/api/payment-callback")})


(defn save-transaction-q
  [payment-ctx]
  {:insert-into :transaction_log
   :values      [payment-ctx]
   :returning   [:transaction]})


(defn upsert-card-q
  [card]
  {:insert-into   :cards
   :values        [card]
   :on-conflict   [:user_id :card_pan]
   :do-update-set (keys card)
   :returning     [:id]})


(defn upsert-settings-q
  [settings-ctx]
  {:insert-into   :payment_settings
   :values        [settings-ctx]
   :on-conflict   [:user_id]
   :do-update-set (keys settings-ctx)})


(defn update-settings-q
  [ctx]
  {:update :payment_settings
   :set    ctx
   :where  [:and
            [:= :id (:id ctx)]
            [:= :user_id (:user_id ctx)]]})


(defn get-payment-link
  [user config]
  (let [ctx (make-link-ctx user config)
        _   (log/debug "fondy ctx" (pr-str ctx))
        res (-> (utils/post! POST-URL {:request (sign ctx)})
                :response)]
    (prn res)
    (if (= "success" (:response_status res))
      (:checkout_url res)
      (throw (ex-info "Error getting payment link" res)))))


(comment
  (println (get-payment-link {:id "1" :email "pmapcat@gmail.com"} "1" "weekly")))


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
           :where  [:and [:= :type (db/->transaction-type status)]
                    [:= :transaction order-id]]}))


(defn process-approved!
  [{:keys [amount
           actual_currency
           rectoken
           rectoken_lifetime
           masked_card
           merchant_data
           order_id]
    :as   resp}]
  (let [amount          (int (/ (utils/parse-int amount) 100))
        payload         (edn/read-string merchant_data)
        card            {:user_id          (:user_id payload)
                         :token            rectoken
                         :token_expires_at (when (not-empty rectoken_lifetime)
                                             (t/parse-dt rectoken_lifetime))
                         :card_pan         masked_card}
        next-payment-at (calculate-next-payment-at
                          (t/now)
                          (:freq payload))]
    (db/tx
      (let [card-id  (when rectoken
                       (:id (db/one (upsert-card-q card))))
            settings {:user_id         (:user_id payload)
                      :card_id         card-id
                      :frequency       (:freq payload)
                      :next_payment_at next-payment-at
                      :paused_at       nil
                      :amount          amount
                      :currency        (db/->currency-type actual_currency)}]
        (db/tx
          (db/q (save-transaction-q
                  {:transaction (utils/parse-uuid order_id)
                   :amount      amount
                   :order_id    order_id
                   :card_id     card-id
                   :user_id     (:user_id payload)
                   :type        (db/->transaction-type :Approved)
                   :currency    (db/->currency-type actual_currency)
                   :data        (db/as-jsonb resp)}))
          (when card-id
            (if (:id payload)
              (db/one (update-settings-q (assoc settings :id (:id payload))))
              ;; we're allowing only one schedule per user right now
              (db/one (upsert-settings-q settings)))))
        (email/receipt! (:email (auth/id->user (:user_id payload)))
          {:amount          amount
           :currency        actual_currency
           :next_payment_at next-payment-at})))))


(defn write-processing!
  [status {:keys [amount
                  actual_currency
                  order_id
                  merchant_data]
           :as   resp}]
  (let [payload (edn/read-string merchant_data)
        amount  (when (seq amount)
                  (int (/ (utils/parse-int amount) 100)))]
    (db/q (save-transaction-q
            {:transaction (utils/parse-uuid order_id)
             :amount      amount
             :order_id    order_id
             :user_id     (:user_id payload)
             :type        (db/->transaction-type status)
             :currency    (when-not (empty? actual_currency)
                            (db/->currency-type actual_currency))
             :data        (db/as-jsonb resp)}))))


(def TRANSACT-MAPPING
  {"created"    (partial write-processing! :Created)
   "processing" (partial write-processing! :InProcessing)
   "declined"   (partial write-processing! :Declined)
   "reversed"   (partial write-processing! :Voided)
   "expired"    (partial write-processing! :Expired)
   "approved"   process-approved!})


(defn set-begin-charging!
  [uid id]
  (->> (db/one (update-settings-q {:id                id
                                   :user_id           uid
                                   :begin_charging_at (t/now)}))
       ::jdbc/update-count
       pos?))


(defn set-paused!
  [uid id]
  (->> (db/one (update-settings-q {:id        id
                                   :user_id   uid
                                   :paused_at (t/now)}))
       ::jdbc/update-count
       pos?))


(defn set-resumed!
  [uid id]
  (prn uid id)
  (->> (db/one (update-settings-q {:id        id
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


(defn process-transaction!
  [{:keys [order_status]
    :as   params}]
  (verify! params)
  (let [processor (get TRANSACT-MAPPING order_status)]
    (processor params)))


(defn process-recurrent-payment! [uid]
  (let [payment-params (db/one (recurrent-payment-q uid))]
    (cond
      (not payment-params)
      (log/info "not payment params for user" uid)

      (paused? payment-params)
      (log/info "payment is paused, for user" uid)

      (double-charge?
        (:next_payment_at payment-params)
        (:begin_charging_at payment-params))
      (log/error "Double charge: "
        {:uid         uid
         :last-charge (:begin_charging_at payment-params)
         :planned     (:next_payment_at payment-params)})

      :else
      (let [ctx (make-recurrent-payment-ctx payment-params)
            _   (log/debug "fondy ctx" (pr-str ctx))
            _   (set-begin-charging! uid (:id payment-params))
            res (-> (utils/post! RECURRING-URL {:request (sign ctx)})
                  :response)]
        (if (= "success" (:response_status res))
          (process-transaction! res)
          (throw (ex-info "Recurrent payment error" res)))))))
