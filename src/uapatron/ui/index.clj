(ns uapatron.ui.index
  (:require [uapatron.email :as email]
            [uapatron.auth :as auth]
            [uapatron.db :as db]
            [uapatron.ui.base :as base]
            [uapatron.utils :as utils]
            [uapatron.config :as config]))


(defn LoginSent [{:keys [email]}]
  (base/wrap
    [:div.subscribe.container
     [:div.subscribe__main
      [:div.subscribe__sent
       [:div #t "Authentication link has been sent to"]
       [:div email]
       [:div.subscribe__message #t "Please open the link to log in - it's going to be valid for 5 minutes."]]]
     [:div.subscribe__side]]))


(defn IndexPage []
  (base/wrap
    [:div
     [:div.subscribe.container
      [:div.subscribe__main
       [:div
        [:label.subscribe__label {:for "email"} #t "Enter your email"]
        [:form.subscribe__form {:method "post" :action "/login"}
         [:input.subscribe__input {:type "email" :name "email" :id "email" :required true :placeholder "Email"}]
         [:button.subscribe__button {:name "login"} #t "Login"]]]]
      [:div.subscribe__side]]
     [:div.support-us
      #t [:h2 "IT'S NOT TOO LATE." [:br] "WE NEED YOUR SUPPORT NOW MORE THAN EVER"]]]))


;;; HTTP views


(defn index [_req]
  (if (auth/uid)
    {:status  302
     :headers {"Location" "/dash"}}
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body    (IndexPage)}))


(defn start-login
  {:methods    #{:post}
   :parameters {:form {:email string?}}}

  [{:keys [form-params]}]

  (if-let [email (:email form-params)]
    (do
      (email/email-auth! email)
      {:status  200
       :headers {"Content-Type" "text/html"}
       :body    (LoginSent {:email email})})
    {:status  302
     :headers {"Location" "/?error=email-empty"}}))


(defn process-login [{:keys [path-params]}]
  (if-let [email (auth/token->email (:token path-params))]
    (let [user (db/one (auth/upsert-user-q email))]
      {:status  302
       :session {:user_id (:id user)}
       :headers {"Location" "/"}})
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
