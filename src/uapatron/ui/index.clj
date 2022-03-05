(ns uapatron.ui.index
  (:require [clojure.tools.logging :as log]

            [uapatron.email :as email]
            [uapatron.auth :as auth]
            [uapatron.db :as db]
            [uapatron.ui.base :as base]
            [uapatron.bl.fondy :as bl.fondy]
            [uapatron.ui.payment :as ui.payment]))


(defn login-sent-t [{:keys [email]}]
  (base/wrap
    [:p
     "Authentication link has been sent to "
     email
     ". Please open the link to log in - it's going to be valid for 5 minutes."]))


(defn anon-t []
  (base/wrap
    [:form {:method "post" :action "/login"}
     [:label "Email"
      [:input {:type "email" :name "email" :required true}]]
     [:button {:name "login"} "Login"]]))


;;; HTTP views


(defn page [_req]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    (if (auth/uid)
              (ui.payment/Dash)
              (anon-t))})


(defn start-login [{:keys [request-method form-params] :as req}]
  (if (not= request-method :post)
    {:status 405
     :body   "Method Not Allowed"}
    (if-let [email (get form-params "email")]
      (do
        (email/email-auth! email)
        {:status  200
         :headers {"Content-Type" "text/html"}
         :body    (login-sent-t {:email email})})
      {:status  302
       :headers {"Location" "/?error=email-empty"}})))


(defn payment-result [{:keys [request-method params] :as req}]
  (if (not= request-method :post)
    {:status 405
     :body   "Method Not Allowed"}
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body    (do (bl.fondy/process-transaction! params)
                  (ui.payment/success-t))}))


(defn pause [{:keys [request-method params] :as req}]
  (if (not= request-method :post)
    {:status 405
     :body   "Method Not Allowed"}
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body    (do (bl.fondy/set-paused! (auth/uid))
                  (ui.payment/set-paused-t))}))


(defn resume [{:keys [request-method params] :as req}]
  (if (not= request-method :post)
    {:status 405
     :body   "Method Not Allowed"}
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body    (do (bl.fondy/set-resumed! (auth/uid))
                  (ui.payment/set-resumed-t))}))


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
