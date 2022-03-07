(ns uapatron.ui.index
  (:require [clojure.tools.logging :as log]

            [uapatron.email :as email]
            [uapatron.auth :as auth]
            [uapatron.db :as db]
            [uapatron.ui.base :as base]
            [uapatron.bl.fondy :as bl.fondy]
            [uapatron.ui.payment :as ui.payment]))


(defn LoginSent [{:keys [email]}]
  (base/wrap
    [:p
     "Authentication link has been sent to "
     email
     ". Please open the link to log in - it's going to be valid for 5 minutes."]))


(defn Index []
  (base/wrap
    [:form {:method "post" :action "/login"}
     [:label "Email"
      [:input {:type "email" :name "email" :required true}]]
     [:button {:name "login"} "Login"]]))


;;; HTTP views


(defn index [_req]
  (if (auth/uid)
    {:status  302
     :headers {"Location" "/dash"}}
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body    (Index)}))


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
