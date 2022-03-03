(ns uapatron.email
  (:require [org.httpkit.client :as http]
            [uapatron.config :as config]
            [cheshire.core :as json]
            [uapatron.auth :as auth]))


(defn send! [to subj text]
  @(http/request
     {:method  :post
      :url     "https://api.postmarkapp.com/email"
      :headers {"Accept"                  "application/json"
                "Content-Type"            "application/json"
                "X-Postmark-Server-Token" (config/POSTMARK)}
      :body    (json/generate-string
                 {:From          "o.solovyov@modnakasta.ua"
                  :To            to
                  :Subject       subj
                  :TextBody      text
                  :MessageStream "outbound"})}))


(defn email-auth! [email]
  (let [msg   (format "Please open this link to login: https://%s/login/%s"
                (config/DOMAIN)
                (auth/email->token email))]
    (send! email "Come Back Alive - Login" msg)))


(comment
  (email-auth! "alexander@solovyov.net")
  (send! "test@blackhole.postmarkapp.com"
    "Login"
    "Please login with this link: xxx"))
