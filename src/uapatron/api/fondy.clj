(ns uapatron.api.fondy
  (:require [uapatron.bl.fondy :as bl.fondy]
            [uapatron.auth :as auth]
            [clojure.tools.logging :as log]))


(defn payment-callback [_req]
  (bl.fondy/handle-callback! _req)
  {:status  200
   :headers {"Content-Type" "application/json"}
   :body    {:message "ok"}})


(defn go-to-payment [{:keys [form-params]}]
  (try
    (let [{:strs [freq amount]} form-params

          link (bl.fondy/get-payment-link (auth/user) freq amount)]
      {:status  302
       :headers {"Location" link}})
    (catch Exception e
      (log/error e)
      {:status  302
       :headers {"Location" "/?error=payment-link-receiving-error"}})))


