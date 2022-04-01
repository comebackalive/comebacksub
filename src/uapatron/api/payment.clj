(ns uapatron.api.payment
  (:require [com.akovantsev.blet.core :refer [blet]]

            [uapatron.bl.fondy :as bl.fondy]
            [uapatron.auth :as auth]
            [uapatron.utils :as utils]))


(defn payment-callback [req]
  (try
    (bl.fondy/process-transaction! (:body req))
    {:status  200
     :headers {"Content-Type" "application/json"}
     :body    {:message "ok"}}
    (catch Exception e
      (if (::bl.fondy/invalid-signature (ex-data e))
        {:status 400
         :body   "Invalid signature"}
        (throw e)))))


(defn go-to-payment
  {:parameters {:form [:map
                       [:freq     [:enum "day" "week" "month"]]
                       [:amount   int?]
                       [:currency {:optional true} [:enum "UAH" "EUR" "USD"]]]}}

  [{:keys [form-params]}]

  (blet [config {:freq     (:freq form-params)
                 :amount   (str (:amount form-params))
                 :currency (:currency form-params "UAH")}
         ;; TODO: process exception here (maybe rework exception to Option)
         link   (try (bl.fondy/get-payment-link (auth/user) config)
                     (catch Exception e
                       {:error e}))]
    (cond
      (not (auth/user))
      (utils/redir (utils/route "/login" config))

      (:error link)
      {:status  200
       :headers {"Content-Type" "text/plain"}
       :body    (pr-str (:error link))}

      :else
      {:status  302
       :headers {"Location" link}})))


