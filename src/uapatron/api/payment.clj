(ns uapatron.api.payment
  (:require [com.akovantsev.blet.core :refer [blet]]

            [uapatron.bl.fondy :as bl.fondy]
            [uapatron.auth :as auth]
            [uapatron.utils :as utils]))


(defn payment-callback [_req]
  (bl.fondy/process-transaction! (:body _req))
  {:status  200
   :headers {"Content-Type" "application/json"}
   :body    {:message "ok"}})


(defn go-to-payment
  {:parameters {:form [:map
                       [:freq     [:enum "day" "week" "month"]]
                       [:amount   int?]
                       [:currency {:optional true} [:enum "UAH" "EUR" "USD"]]]}}

  [{:keys [form-params]}]

  (blet [freq   (:freq form-params)
         amount (:amount form-params)
         ;; TODO: process exception here (maybe rework exception to Option)
         link   (try (bl.fondy/get-payment-link (auth/user)
                       {:freq     freq
                        :amount   amount
                        :currency (:currency form-params "UAH")})
                     (catch Exception e
                       {:error e}))]
    (cond
      (not (auth/user))
      (utils/msg-redir "unauthenticated")

      (not (and freq amount))
      (utils/msg-redir "no-data")

      (:error link)
      {:status  200
       :headers {"Content-Type" "text/plain"}
       :body    (pr-str (:error link))}

      :else
      {:status  302
       :headers {"Location" link}})))


