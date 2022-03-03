(ns uapatron.api.fondy
  (:require [uapatron.bl.fondy :as bl.fondy]))


(defn payment-callback [_req]
  (bl.fondy/handle-callback! _req)
  {:status  200
   :headers {"Content-Type" "application/json"}
   :body {:message "ok"}})


