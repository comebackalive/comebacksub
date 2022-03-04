(ns uapatron.utils
  (:import [java.util UUID])
  (:require [clojure.walk :as walk]
            [org.httpkit.client :as http]
            [cheshire.core :as json]
            [uapatron.config :as config]))


(set! *warn-on-reflection* true)


(defn uuid [] (UUID/randomUUID))

(def keywordize-keys clojure.walk/keywordize-keys)


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
   (-> @(http/request {:method  method
                       :url     url
                       :headers {"Content-Type" "application/json"}
                       :body    (when body (json/encode body))
                       :timeout (config/TIMEOUT)})
       :body
       (json/parse-string true))))


(defn err-redir [errname]
  {:status  302
   :headers {"Location" (str "/?error=" errname)}})


(defn parse-uuid
  [smth]
  (cond (string? smth) (try (java.util.UUID/fromString smth) (catch Exception _ nil))
        (uuid? smth) smth))

(defn parse-int [value]
  (cond
    (integer? value) value
    (string? value)  (try (Long/parseLong ^String value 10)
                          (catch Exception _ nil))))
