(ns uapatron.api.fondy
  (:require [uapatron.bl.fondy :as bl.fondy]
            [uapatron.utils :as utils]
            [uapatron.auth :as auth]))


(defn payment-callback [_req]
  (bl.fondy/handle-callback! _req)
  {:status  200
   :headers {"Content-Type" "application/json"}
   :body    {:message "ok"}})


(defn go-to-payment [{:keys [form-params]}]
  (when form-params
    (let [{:keys [freq
                  amount]} (utils/keywordize-keys form-params)]
      (if-let [maybe-payment-link
               (bl.fondy/maybe-get-payment-link
                 (auth/user)
                 freq amount)]
        {:status  302
         :headers {"Location" maybe-payment-link}}
        {:status  302
         :headers {"Location" "/?error=payment-link-receiving-error"}}))))


