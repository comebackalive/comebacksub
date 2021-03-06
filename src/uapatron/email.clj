(ns uapatron.email
  (:require [org.httpkit.client :as http]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]

            [uapatron.config :as config]
            [uapatron.auth :as auth]
            [uapatron.time :as t]))


(defn send! [{:keys [to template data] :as cfg}]
  (log/info "sending email" cfg)
  @(http/request
     {:method  :post
      :url     "https://api.postmarkapp.com/email/withTemplate"
      :headers {"Accept"                  "application/json"
                "Content-Type"            "application/json"
                "X-Postmark-Server-Token" (config/POSTMARK)}
      :body    (json/generate-string
                 {:From          "support@comebackalive.in.ua"
                  :To            to
                  :TemplateAlias template
                  :TemplateModel data
                  :MessageStream "outbound"})}))


(defn email-auth! [email data]
  (let [token (auth/make-token (assoc data :email email))
        url   (format "https://%s/login/%s"
                (config/DOMAIN)
                token)]
    (send! {:to       email
            :template "login"
            :data     {:action_url    url
                       :support_email "support@comebackalive.in.ua"
                       :schedule      (when nil
                                        {:amount    100
                                         :currency  "UAH"
                                         :frequency "month"})}})))


(defn receipt! [email {:keys [amount currency masked_card next_payment_at]}]
  (send! {:to       email
          :template "receipt"
          :data     {:support_email   "support@comebackalive.in.ua"
                     :amount          amount
                     :currency        currency
                     :masked_card     masked_card
                     :next_payment_at (t/short next_payment_at)}}))


(defn decline! [email {:keys [amount currency masked_card next_payment_at]}]
  (send! {:to       email
          :template "decline"
          :data     {:support_email   "support@comebackalive.in.ua"
                     :amount          amount
                     :currency        currency
                     :masked_card     masked_card
                     :next_payment_at (t/short next_payment_at)}}))


(comment
  (email-auth! "alexander@solovyov.net" nil))
