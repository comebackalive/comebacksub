(ns uapatron.ui.payment
  (:require [uapatron.auth :as auth]
            [uapatron.ui.base :as base]))


(defn pay-button [{:keys [amount freq]}]
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


(defn dash-t []
  (base/wrap
    [:h1 "Hello, " (:email (auth/user))]

    [:h2 "Once a day"]
    [:div
     (pay-button {:freq "day" :amount 100})
     (pay-button {:freq "day"})]

    [:h2 "Once a week"]
    [:div
     (pay-button {:freq "week" :amount 500})
     (pay-button {:freq "week"})]))
