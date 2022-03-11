(ns uapatron.email
  (:require [org.httpkit.client :as http]
            [cheshire.core :as json]

            [uapatron.config :as config]
            [uapatron.auth :as auth]
            [uapatron.time :as t]))


(defn send! [{:keys [to template data]}]
  @(http/request
     {:method  :post
      :url     "https://api.postmarkapp.com/email/withTemplate"
      :headers {"Accept"                  "application/json"
                "Content-Type"            "application/json"
                "X-Postmark-Server-Token" (config/POSTMARK)}
      :body    (json/generate-string
                 {:From          "a@solovyov.net"
                  :To            to
                  :TemplateAlias template
                  :TemplateModel data
                  :MessageStream "outbound"})}))


(defn email-auth! [email]
  (let [url (format "https://%s/login/%s"
              (config/DOMAIN)
              (auth/email->token email))]
    (send! {:to       email
            :template "login"
            :data     {:action_url    url
                       :support_email "support@comebackalive.in.ua"
                       :schedule      (when nil
                                        {:amount    100
                                         :currency  "UAH"
                                         :frequency "month"})}})))


(defn receipt! [email {:keys [amount currency next_payment_at]}]
  (send! {:to       email
          :template "receipt"
          :data     {:support_email   "support@comebackalive.in.ua"
                     :amount          amount
                     :currency        currency
                     :next_payment_at (t/short next_payment_at)}}))


(comment
  (email-auth! "alexander@solovyov.net"))
