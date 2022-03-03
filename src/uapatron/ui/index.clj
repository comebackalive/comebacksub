(ns uapatron.ui.index
  (:require [uapatron.email :as email]
            [uapatron.ui.base :as base]
            [uapatron.auth :as auth]
            [uapatron.db :as db]))


(defn page [_req]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body
   (if (auth/uid)
     (base/wrap
       [:h1 "Hello, " (:email (auth/user))])
     (base/wrap
       [:form {:method "post" :action "/login"}
        [:label "Email"
         [:input {:type "email" :name "email" :required true}]]
        [:button {:name "login"} "Login"]]))})


(defn start-login [{:keys [request-method form-params] :as req}]
  (if (not= request-method :post)
    {:status 405
     :body "Method Not Allowed"}
    (if-let [email (get form-params "email")]
      (do
        (email/email-auth! email)
        {:status 200
         :headers {"Content-Type" "text/html"}
         :body (base/wrap
                 [:p
                  "Authentication link has been sent to "
                  email
                  ". Please open the link to log in - it's going to be valid for 5 minutes."])})
      {:status 302
       :headers {"Location" "/?error=email-empty"}})))


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
