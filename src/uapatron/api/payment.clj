(ns uapatron.api.payment
  (:require [com.akovantsev.blet.core :refer [blet]]

            [uapatron.bl.fondy :as bl.fondy]
            [uapatron.bl.solidgate :as bl.solidgate]
            [uapatron.auth :as auth]
            [uapatron.utils :as utils]
            [clojure.string :as str]))


(defn payment-callback [{:keys [uri body]}]
  (let [process (cond
                  (str/includes? uri "fondy")
                  bl.fondy/process-transaction!

                  (str/includes? uri "solidgate")
                  bl.solidgate/write-transaction!

                  :else
                  (throw (ex-info "Unknown payment provider" {:uri uri})))]
    (try
      (process body)
      {:status  200
       :headers {"Content-Type" "application/json"}
       :body    {:message "ok"}}
      (catch Exception e
        (if (:invalid-signature (ex-data e))
          {:status 400
           :body   "Invalid signature"}
          (throw e))))))


(defn go-to-payment
  {:parameters {:form [:map
                       [:freq     [:enum "day" "week" "month"]]
                       [:amount   int?]
                       [:currency {:optional true} [:enum "UAH" "EUR" "USD"]]]}}

  [{:keys [form-params]}]

  (blet [config {:freq     (:freq form-params)
                 :amount   (:amount form-params)
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


(defn one-time
  {:parameters {:query [:map
                        [:type {:optional true} [:enum "solidgate" "fondy"]]
                        [:amount int?]
                        [:currency {:optional true} [:enum "UAH" "EUR" "USD"]]
                        [:tag [:vector string?]]
                        [:hidden {:optional true} string?]
                        [:email {:optional true} string?]
                        [:next string?]]}}
  [{:keys [query-params]}]

  (let [config {:amount   (:amount query-params)
                :currency (:currency query-params "UAH")
                :tags     (utils/ensure-vec (:tag query-params))
                :hiddens  (utils/ensure-vec (:hidden query-params))
                :next     (:next query-params)
                :email    (:email query-params)}
        link (if (= (:type query-params) "fondy")
               (bl.fondy/one-time-link config)
               (bl.solidgate/one-time-link config))]
    {:status  302
     :headers {"Location" link}}))

