(ns uapatron.ui.payment
  (:require [hiccup2.core :as hi]

            [uapatron.auth :as auth]
            [uapatron.ui.base :as base]
            [uapatron.db :as db]
            [uapatron.time :as t]
            [uapatron.bl.fondy :as bl.fondy]
            [uapatron.utils :as utils]
            [uapatron.config :as config]
            [clojure.edn :as edn]))


(def PRESETS
  {:week  {"UAH" [50 200]
           "USD" [5 20]
           "EUR" [5 20]}
   :month {"UAH" [100 200 500 1000]
           "USD" [10 50 100 300]
           "EUR" [10 50 100 300]}})


;;; queries

(defn user-cards-q []
  {:from   [:cards]
   :select [:card_label
            :created_at]
   :where  [:and
            [:= :user_id (auth/uid)]
            [:not :is_deleted]]})


(defn user-schedule-q
  ([] (user-schedule-q nil))
  ([id]
   {:from   [[:payment_settings :ps]]
    :join   [[:cards :c] [:= :c.id :ps.card_id]]
    :select [:c.card_label
             :ps.id
             :ps.next_payment_at
             :ps.frequency
             :ps.paused_at
             :ps.amount
             :ps.currency
             :ps.created_at]
    :where  [:and
             [:= :ps.user_id (auth/uid)]
             (when id [:= :ps.id id])
             #_[:not :is_deleted]]}))


(defn user-transactions-q []
  {:from     [[:transaction_log :t]]
   :join     [[:cards :c] [:= :c.id :t.card_id]]
   :select   [:t.order_id
              :t.amount
              :t.currency
              :c.card_label
              :t.type]
   :where    [:in :t.id {:from     [[:transaction_log :t]]
                         :select   [[[:max :id] :id]]
                         :where    [:= :t.user_id (auth/uid)]
                         :group-by [:t.order_id]}]
   :order-by [[:t.id :desc]]
   :limit    50})


(defn card-fmt [s]
  (case s
    "applepay" "Apple Pay"
    "googlepay" "Google Pay"
    (->> (re-seq #".{4}" s)
         (interpose " ")
         (apply str))))


;;; test cards: https://docs.fondy.eu/en/docs/page/2/

(defn PayButton [{:keys [freq amount currency continue]}]
  [:div.payments__item
   [:form {:method "post"
           :action "/api/go-to-payment"}
    [:div.payments__item-wrap
     [:div {:class (cond-> "payments__item-icon"
                     (nil? amount)
                     (str " no-amount"))}]
     (if amount
       [:div
        [:h4.payments__item-amount amount " " currency]
        [:input {:type "hidden" :name "amount" :value amount}]]

       [:input.payments__item-input {:type "text" :name "amount" :required true :placeholder #t "Your sum"}])

     [:span.payments__item-interval
      [:span (case freq
               "day"   #t "per day"
               "week"  #t "per week"
               "month" #t "per month")]]
     [:input {:type "hidden" :name "currency" :value currency}]
     [:input {:type "hidden" :name "freq" :value freq}]
     [:button.payments__item-btn
      (if continue
        #t "Continue to payment"
        #t "Subscribe")]]]])


(def UK-DURATION
  {"day"   "день"
   "week"  "тиждень"
   "month" "місяць"})


(defn -ScheduleItem [item]
  (let [paused? (boolean (:paused_at item))
        amount  (:amount item)]
    (hi/html
      [:div {:class (cond-> "subscription"
                      paused?
                      (str " paused"))}
       [:div.subscription__wrap
        [:div.subscription__icon]
        [:div.subscription__main
         [:div.subscription__frequency
          [:span
           (case (:frequency item)
             "day"   #t "Your daily support"
             "week"  #t "Your weekly support"
             "month" #t "Your monthly support")]]
         [:div.subscription__amount amount " " (:currency item)]]]
       [:div.subscription__details
        (if paused?
          [:p #t "Subscription is paused"]
          [:p #t "Next payment at " (t/short (:next_payment_at item))])
        [:p #t "From card " (card-fmt (:card_label item))]]

       (if paused?
         [:form {:method "post"
                 :action "/payment/resume"}
          [:button {:name "id" :value (:id item)} #t "Resume subscription"]]
         [:form {:method "post"
                 :action "/payment/pause"}
          [:button {:name "id" :value (:id item)} #t "Pause subscription"]])])))


(defn ScheduleItem [id]
  (let [item (db/one (user-schedule-q id))]
    (-ScheduleItem item)))


(defn SomeError []
  (hi/html
    [:div.card.col-4
     [:h4 #t "Some error happened"]
     [:p #t "Please contact developers"]]))


(defn PaymentSection []
  (hi/html
    [:section.payment-section.container
     [:div
      [:h2 #t "Subscribe for weekly payment"]
      (let [currency config/*currency*
            preset   (get-in PRESETS [:week currency])]
        [:div.payments
         (for [amount preset]
           (PayButton {:freq "week" :amount amount :currency currency}))

         (PayButton {:freq "week" :currency currency})])

      [:h2 #t "Subscribe for monthly payment"]
      (let [currency config/*currency*
            preset   (get-in PRESETS [:month currency])]
        [:div.payments
         (for [amount preset]
           (PayButton {:freq "month" :amount amount :currency currency}))

         (PayButton {:freq "month" :currency currency})])

      [:div.payment-section__message
       [:p.t-bold #t "Subscription is charged automatically."]
       [:p #t "You can cancel the automatic renewal or change your payment at any time."]]]]))

(defn Transactions []
  (when-let [history (not-empty (db/q (user-transactions-q)))]
    (hi/html
      [:section
       [:h2 #t "Transaction history"]
       [:table {:style "width: 100%; border-spacing: 10px"}
        [:thead [:th " "] [:th "ID"] [:th "Amount"] [:th "Card"]]
        (for [trans history]
          [:tr
           [:td (if (= (:type trans) "Approved") "✅" "❌")]
           [:td (:order_id trans)]
           [:td {:style "text-align: right"}
            (:amount trans) " " (:currency trans)]
           [:td (card-fmt (:card_label trans))]])]])))


(defn DashPage [config]
  (let [schedule (db/one (user-schedule-q))]
    (base/wrap
      [:div.payment-page.container
       #t [:h1 "Hello, " (:email (auth/user))]

       (when config
         [:div {:style {:margin-bottom "36px"}}
          (PayButton config)])

       (when schedule
         [:section
          (-ScheduleItem schedule)])

       #_(when-let [cards (seq (db/q (user-cards-q)))]
           [:section
            [:h2 #t "Your cards"]
            [:div
             (for [card cards]
               [:div (card-fmt (:card_pan card))
                " — "
                (t/short (:created_at card))])]])

       (when (or (not schedule)
                 (:paused_at schedule))
         (PaymentSection))

       (when (:daily utils/*ctx*)
         [:section.payment-section
          [:h2 "Once a day (debug)"]
          [:div.payments
           (PayButton {:freq "day" :amount 100 :currency "UAH"})
           (PayButton {:freq "day"})]])

       (Transactions)])))


;;; Handlers

(defn dash
  {:parameters {:query [:map
                        [:config {:optional true} string?]
                        [:daily {:optional true} any?]]}}

  [{:keys [query-params]}]

  (utils/ctx {:daily (contains? query-params :daily)}
    (let [config (try (some-> (edn/read-string (:config query-params))
                        (assoc :continue true))
                      (catch Exception _
                        nil))]
      {:status  200
       :headers {"Content-Type" "text/html"}
       :body    (DashPage config)})))


(defn result
  {:methods #{:post}}

  [{:keys [params]}]

  (bl.fondy/write-transaction! params)
  (utils/msg-redir "/dash" "successful-payment"))


(defn pause
  {:methods    #{:post}
   :parameters {:form {:id int?}}}

  [{:keys [form-params]}]

  (if (bl.fondy/set-paused! (auth/uid) (:id form-params))
    (utils/redir "/dash")
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body    (SomeError)}))


(defn resume
  {:methods    #{:post}
   :parameters {:form {:id int?}}}

  [{:keys [form-params]}]

  (if (bl.fondy/set-resumed! (auth/uid) (:id form-params))
    (utils/redir "/dash")
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body    (SomeError)}))
