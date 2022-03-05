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

(def DESC-RECUR "Допомога savelife.in.ua (регулярний платіж)")


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


(defn make-order-id [uid] (str uid ":" (utils/uuid)))
(defn oid->uid [order-id] (utils/parse-int (first  (str/split order-id #":"))))
(defn oid->tx  [order-id] (utils/parse-uuid (second (str/split order-id #":"))))


(defn get-ctx-for-recurrent-payment-q
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
  [user amount freq]
  {:order_id            (make-order-id (:id user))
   :order_desc          DESC
   :merchant_id         (config/MERCHANT-ID)
   :currency            (first ["UAH" "RUB" "USD" "EUR" "GBP" "CZK"])
   :amount              (str amount)
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
           currency
           frequency
           amount
           token]}]
  {:order_id            (make-order-id user_id)
   :order_desc          DESC-RECUR
   :merchant_id         (config/MERCHANT-ID)
   :currency            currency
   :amount              amount
   :merchant_data       (pr-str {:recurrent true :freq frequency})
   :rectoken            token
   :sender_email        user_email
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
        res (-> (utils/post! POST-URL {:request (sign ctx)})
                :response)]
    (prn res)
    (if (= "success" (:response_status res))
      (:checkout_url res)
      (throw (ex-info "Error getting payment link" res)))))

#_(println (get-payment-link {:id    "1" :email "pmapcat@gmail.com"} "1" "weekly"))

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
                    [:= :transaction (oid->tx order-id)]]}))


(defn process-approved!
  [{:keys [amount
           actual_currency
           rectoken
           rectoken_lifetime
           masked_card
           merchant_data
           order_id]
    :as   resp}]
  (db/tx
    (let [card-id  (when rectoken
                     (:id (db/one (upsert-card-q
                                    {:user_id          (oid->uid order_id)
                                     :token            rectoken
                                     :token_expires_at (t/parse-dd-MM-yyyy-HH-mm-ss rectoken_lifetime)
                                     :card_pan         masked_card}))))
          our-data (read-string merchant_data)]
      (db/tx
        (db/q (save-transaction-q
                {:transaction (utils/parse-uuid (oid->tx order_id))
                 :amount      (utils/parse-int amount)
                 :order_id    order_id
                 :card_id     card-id
                 :user_id     (oid->uid order_id)
                 :type        (db/->transaction-type :Approved)
                 :currency    (db/->currency-type actual_currency)
                 :data        (db/as-jsonb resp)}))
        (when rectoken
          (db/q (upsert-settings-q
                  (utils/remove-nils
                    {:user_id         (oid->uid order_id)
                     :card_id         card-id
                     :frequency       (:freq our-data)
                     :next_payment_at (calculate-next-payment-at
                                               (t/now)
                                               (:freq our-data))
                     :amount          (utils/parse-int amount)
                     :currency        (db/->currency-type actual_currency)})))
          (db/q (upsert-settings-q
                  {:user_id         (oid->uid order_id)
                   :paused_at       nil})))))))


(defn write-processing!
  [status {:keys [amount
                  actual_currency
                  order_id]
           :as   resp}]
  (db/q (save-transaction-q
          {:transaction (utils/parse-uuid (oid->tx order_id))
           :amount      (when-not (empty? amount)
                          (utils/parse-int amount))
           :order_id    order_id
           :user_id     (oid->uid order_id)
           :type        (db/->transaction-type status)
           :currency    (when-not (empty? actual_currency)
                          (db/->currency-type actual_currency))
           :data        (db/as-jsonb resp)})))


(def TRANSACT-MAPPING
  {:created    (partial write-processing! :Created)
   :processing (partial write-processing! :InProcessing)
   :declined   (partial write-processing! :Declined)
   :reversed   (partial write-processing! :Voided)
   :expired    (partial write-processing! :Expired)
   :approved   process-approved!})


(defn set-begin-charging!
  [uid]
  (db/one (upsert-settings-q {:user_id           uid    
                              :begin_charging_at (t/now)})))


(defn set-paused!
  [uid]
  (db/one (upsert-settings-q {:user_id   uid    
                              :paused_at (t/now)})))


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


(doseq [[[planned      last-started] result]
        {["2021-12-31" "2021-12-30"] false
         ["2021-12-30" "2021-12-31"] true
         ["2021-12-30" "2021-12-30"] true
         ["2021-12-30" nil]          false}]
  (assert (= (double-charge?
               (t/parse-yyyy-MM-dd planned)
               (t/parse-yyyy-MM-dd last-started)) result)))


(defn process-transaction!
  [{:keys [order_status]
    :as   req}]
  (verify! req)
  (if-let [processor (get TRANSACT-MAPPING (keyword order_status))]
    (processor req)
    (log/warn "Unknown status type: " order_status)))


(defn process-recurrent-payment! [uid]
  (let [payment-params (db/one (get-ctx-for-recurrent-payment-q uid))]
    (cond
      (not payment-params)
      (log/info "not payment params for user" uid)

      (paused? payment-params)
      (log/info "payment is paused, for user" uid)

      (double-charge? (:begin_charging_at payment-params) (:next_payment_at payment-params))
      (log/error "Double charge: " 
        {:uid         uid
         :last-charge (:begin_charging_at payment-params)
         :planned     (:next_payment_at payment-params)})
      
      :else
      (let [ctx (make-recurrent-payment-ctx payment-params)
            _   (log/debug "fondy ctx" (pr-str ctx))
            _   (set-begin-charging! uid)
            res (-> (utils/post! RECURRING-URL {:request (sign ctx)})
                  :response)]
        (if (= "success" (:response_status res))
          (process-transaction! res)
          (throw (ex-info "Recurrent payment error" res)))))))
