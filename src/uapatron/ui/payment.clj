(ns uapatron.ui.payment
  (:require [uapatron.auth :as auth]
            [uapatron.ui.base :as base]
            [uapatron.db :as db]
            [uapatron.time :as t]))

;;; queries

(defn user-cards-q []
  {:from   [:cards]
   :select [:card_pan
            :created_at]
   :where  [:and
            [:= :user_id (auth/uid)]
            [:not :is_deleted]]})


(defn user-schedule-q []
  {:from   [[:payment_settings :ps]]
   :join   [[:cards :c] [:= :c.id :ps.card_id]]
   :select [:c.card_pan
            :ps.id
            :ps.next_payment_at
            :ps.frequency
            :ps.paused_at
            :ps.amount
            :ps.currency
            :ps.created_at]
   :where  [:and
            [:= :ps.user_id (auth/uid)]
            #_[:not :is_deleted]]})


(defn card-fmt [s]
  (->> (re-seq #".{4}" s)
       (interpose " ")
       (apply str)))


;;; test cards: https://docs.fondy.eu/en/docs/page/2/

(defn PayButton [{:keys [amount freq]}]
  [:form {:method "post"
          :action "/api/go-to-payment"
          :style  "display: inline-block"}
   [:fieldset
    [:input (if amount
              {:type "hidden" :name "amount" :value (* 100 amount)}
              {:type "text" :name "amount" :placeholder "Your sum"})]
    [:input {:type "hidden" :name "freq" :value freq}]
    [:button (if amount
               (str amount " UAH")
               "Subscribe")]]])


(defn ScheduleItem [item]
  [:div.card.col-4
   [:h4
    (int (/ (:amount item) 100)) " "
    (:currency item)
    " every " (:frequency item)]
   [:p "Next payment at " (t/short (:next_payment_at item))]
   [:p "From " (card-fmt (:card_pan item))]
   [:form {:method "post" :action "/pause"}
    [:button {:name "id" :value (:id item)} "Pause schedule"]]])


(defn SubscriptionPaused [item]
  [:div.card.col-4
   [:h4
    (int (/ (:amount item) 100)) " "
    (:currency item)
    " every " (:frequency item)]
   #_[:p "Next payment at " (t/short (:next_payment_at item))]
   [:p "Payment is paused "]
   [:p "From " (card-fmt (:card_pan item))]
   [:form {:method "post" :action "/resume"}
    [:button {:name "id" :value (:id item)} "Resume payment"]]])


(defn Dash []
  (base/wrap
    [:h1 "Hello, " (:email (auth/user))]

    (when-let [items (seq (db/q (user-schedule-q)))]
      [:section
       [:h2 "Your schedule"]
       [:div.row
        (for [item items]
          (if (:paused_at item)
            (SubscriptionPaused item)
            (ScheduleItem item)))]])

    (when-let [cards (seq (db/q (user-cards-q)))]
      [:section
       [:h2 "Your cards"]
       [:div
        (for [card cards]
          [:div (card-fmt (:card_pan card)) " at " (:created_at card)])]])

    [:h2 "Once a day"]
    [:div
     (PayButton {:freq "day" :amount 100})
     (PayButton {:freq "day"})]

    [:h2 "Once a week"]
    [:div
     (PayButton {:freq "week" :amount 500})
     (PayButton {:freq "week"})]))


(defn success-t []
  (base/wrap
    [:h1 "Payment is successful, " (:email (auth/user))]))


(defn set-paused-t []
  (base/wrap
    [:h1 "Subscription is paused, " (:email (auth/user))]))


(defn set-resumed-t []
  (base/wrap
    [:h1 "Subscription is resumed, " (:email (auth/user))]))
