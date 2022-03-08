(ns uapatron.ui.payment
  (:require [hiccup2.core :as hi]

            [uapatron.auth :as auth]
            [uapatron.ui.base :as base]
            [uapatron.db :as db]
            [uapatron.time :as t]
            [uapatron.bl.fondy :as bl.fondy]
            [uapatron.utils :as utils]))

;;; queries

(defn user-cards-q []
  {:from   [:cards]
   :select [:card_pan
            :created_at]
   :where  [:and
            [:= :user_id (auth/uid)]
            [:not :is_deleted]]})


(defn user-schedule-q
  ([] (user-schedule-q nil))
  ([id]
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
             (when id [:= :ps.id id])
             #_[:not :is_deleted]]}))


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
              {:type "hidden" :name "amount" :value amount}
              {:type "text" :name "amount" :placeholder "Your sum"})]
    [:input {:type "hidden" :name "freq" :value freq}]
    [:button (if amount
               (str amount " UAH")
               "Subscribe")]]])


(defn -ScheduleItem [item]
  (let [paused? (boolean (:paused_at item))]
    (hi/html
      [:div.card.col-4
       [:h4
        (int (/ (:amount item) 100)) " "
        (:currency item)
        " every " (:frequency item)]
       (if paused?
         [:p "Subscription is paused"]
         [:p "Next payment at " (t/short (:next_payment_at item))])
       [:p "From " (card-fmt (:card_pan item))]
       (if paused?
         [:form {:method    "post"
                 :action    "/payment/resume"
                 :ts-req    ""
                 :ts-target "parent .card"}
          [:button {:name "id" :value (:id item)} "Resume subscription"]]
         [:form {:method    "post"
                 :action    "/payment/pause"
                 :ts-req    ""
                 :ts-target "parent .card"}
          [:button {:name "id" :value (:id item)} "Pause subscription"]])])))


(defn ScheduleItem [id]
  (let [item (db/one (user-schedule-q id))]
    (-ScheduleItem item)))


(defn SomeError []
  (hi/html
    [:div.card.col-4
     [:h4 "Some error happened"]
     [:p "Please contact developers"]]))


(defn DashPage []
  (base/wrap
    [:h1 "Hello, " (:email (auth/user))]

    (when-let [items (seq (db/q (user-schedule-q)))]
      [:section
       [:h2 "Your schedule"]
       [:div.row
        (for [item items]
          (-ScheduleItem item))]])

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


(defn PaymentSuccess []
  (base/wrap
    [:h1 "Payment is successful, " (:email (auth/user))]))


;;; Handlers

(defn dash [_req]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    (if (auth/uid)
              (DashPage)
              (utils/msg-redir "unauthenticated"))})


(defn result
  {:methods #{:post}}

  [{:keys [params]}]

  (bl.fondy/process-transaction! params)
  (utils/msg-redir "/dash" "successful-payment"))


(defn pause
  {:methods    #{:post}
   :parameters {:form {:id int?}}}

  [{:keys [form-params]}]

  (if (bl.fondy/set-paused! (auth/uid) (:id form-params))
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body    (ScheduleItem (:id form-params))}
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body    (SomeError)}))


(defn resume
  {:methods    #{:post}
   :parameters {:form {:id int?}}}

  [{:keys [form-params]}]

  (bl.fondy/set-resumed! (auth/uid) (:id form-params))
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    (ScheduleItem (:id form-params))})
