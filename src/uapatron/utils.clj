(ns uapatron.utils
  (:import [java.util UUID])
  (:require [clojure.walk :as walk]
            [org.httpkit.client :as http]
            [cheshire.core :as json]

            [uapatron.config :as config]))


(set! *warn-on-reflection* true)


(defn uuid [] (UUID/randomUUID))


(defn remove-nils
  "remove pairs of key-value that has nil value from a (possibly nested)
  map. also transform map to nil if all of its value are nil"
  [nm]
  (walk/postwalk
    (fn [el]
      (if (map? el)
        (let [m (into {} (remove (comp nil? second) el))]
          (when (seq m)
            m))
        el))
    nm))


(defn json-http!
  ([method url] (json-http! method url nil))
  ([method url body]
   (->> {:method  method
         :url     url
         :body    (when body (json/encode body))
         :timeout (config/TIMEOUT)}
        http/request
        deref
        :body
        json/decode)))
