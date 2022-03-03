(ns uapatron.utils
  (:require [aleph.http :as http]
            [uapatron.config :as config]
            [cheshire.core :as json])
  (:import [java.util UUID]))

(defn uuid [] (UUID/randomUUID))


(defn remove-nil-vals
  [smth]
  (into {} (for [[k v] smth
                 :when (not (nil? v))]
             [k v])))


(defn json-http!
  ([method url] (json-http! method url nil))
  ([method url body]
   (->> {:method             method
         :url                url
         :headers            {}
         :body               (json/encode body)
         :request-timeout    (config/TIMEOUT)
         :pool-timeout       (config/TIMEOUT)
         :connection-timeout (config/TIMEOUT)}
     @(http/request method) :body (json/decode))))
