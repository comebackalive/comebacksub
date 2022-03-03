(ns uapatron.config
  (:require [clojure.string :as str]))


(set! *warn-on-reflection* true)


(defn get-env
  ([var-name desc]
   (get-env var-name nil desc))
  ([var-name default desc]
   (or (some-> (System/getenv var-name) str/trim)
       default
       (do
         (. (Runtime/getRuntime) addShutdownHook
           (Thread. #(binding [*out* *err*]
                       (println desc))))
         (System/exit 1)))))


(def DEV      #(-> (get-env "ENV" "dev"
                     "ENV set an app environment, dev/prod")
                   (not= "prod")))
(def PORT     #(-> (get-env "PORT" "1357"
                     "PORT to start on")
                   str/trim
                   Integer/parseInt))
(def TGTOKEN  #(get-env "TGTOKEN"
                 "TGTOKEN env var is empty, please set to Telegram bot token"))
(def PGURL    #(get-env "PGURL"
                 "PGURL env var is empty, please set to Postgres URL"))
(def DOMAIN   #(get-env "DOMAIN" "sanya.ngrok.io"
                 "DOMAIN env var is empty, please set to site domain"))
(def SECRET   #(get-env "SECRET"
                 "SECRET key to sign session cookies and other"))
(def POSTMARK #(get-env "POSTMARK"
                 "POSTMARK api key to send emails"))
;; (def SENTRY   #(get-env "SENTRY"
;;                  "Sentry DSN"))
