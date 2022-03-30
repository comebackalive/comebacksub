(ns uapatron.ui.index
  (:require [uapatron.config :as config]
            [uapatron.db :as db]
            [uapatron.utils :as utils]
            [uapatron.email :as email]
            [uapatron.auth :as auth]
            [uapatron.ui.base :as base]
            [uapatron.ui.payment :as ui.payment]))


(defn LoginSent [{:keys [email]}]
  (base/wrap
    [:div.subscribe.container
     [:div.subscribe__main
      [:div.subscribe__sent
       [:div #t "Authentication link has been sent to"]
       [:div email]
       [:div.subscribe__message #t "Please open the link to log in - it's going to be valid for 30 minutes."]]]
     [:div.subscribe__side]]))


(defn LoginPage [& [{:keys [config]}]]
  (base/wrap
    [:div
     [:div.subscribe.container
      [:div.subscribe__main
       [:div
        [:label.subscribe__label {:for "email"} #t "Enter your email"]
        [:form.subscribe__form {:method "post" :action "/login"}
         [:input {:type "hidden" :name "config" :value config}]
         [:input.subscribe__input {:type        "email"
                                   :pattern     "[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,4}$"
                                   :title       #t "a valid email address"
                                   :name        "email"
                                   :id          "email"
                                   :required    true
                                   :placeholder "Email"}]
         [:button.subscribe__button {:name "login"} #t "Login"]]
        [:p.subscribe__inform
         #t "We promise to never spam you. This is for identification so you can manage your subscription, plus for receipts when we charge you."]]]
      [:div.subscribe__side]]
     [:div.support-us.upper
      #t [:h2 "It's not too late." [:br] "We need your support now more than ever."]]]))


(defn IndexPage []
  (base/wrap
    (ui.payment/PaymentSection)))


;;; HTTP views


(defn index [_req]
  (if (auth/uid)
    {:status  302
     :headers {"Location" "/dash"}}
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body    (IndexPage)}))


(defn start-login
  {:parameters {:form  [:map
                        [:email string?]
                        [:config {:optional true} string?]]
                :query [:map [:config {:optional true} string?]]}}

  [{:keys [form-params query-params request-method]}]

  (if (= request-method :post)
    (let [config (:config form-params)]
      (email/email-auth! (:email form-params)
        (when config {:config config}))
      {:status  200
       :headers {"Content-Type" "text/html"}
       :body    (LoginSent {:email (:email form-params)})})
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body    (LoginPage {:config (:config query-params)})}))


(defn process-login [{:keys [path-params]}]
  (if-let [data (auth/token->data (:token path-params))]
    (let [_    (assert (:email data) "IDK who are you")
          user (db/one (auth/upsert-user-q (:email data)))]
      {:status  302
       :session {:user_id (:id user)}
       :headers {"Location" (utils/route "/dash"
                              {:config (:config data)})}})
    {:status  302
     :headers {"Location" "/?error=token-invalid"}}))


(defn logout [_req]
  {:status  302
   :session nil
   :headers {"Location" "/"}})


(defn set-lang
  {:parameters {:path {:lang (into [:enum] config/LANGS)}}}

  [req]

  (-> (utils/redir "/")
      (utils/with-cookie "lang" (-> req :path-params :lang))))


(defn set-currency
  {:parameters {:path {:currency (into [:enum] config/CURRENCIES)}}}

  [req]

  (-> (utils/redir "/")
      (utils/with-cookie "currency" (-> req :path-params :currency))))
