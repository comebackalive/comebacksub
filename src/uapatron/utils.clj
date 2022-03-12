(ns uapatron.utils
  (:import [java.util UUID])
  (:require [clojure.walk :as walk]
            [org.httpkit.client :as http]
            [cheshire.core :as json]
            [uapatron.config :as config]
            [ring.util.codec :as codec]))


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


(defn parse-uuid
  [smth]
  (cond (string? smth) (try (java.util.UUID/fromString smth)
                            (catch Exception _ nil))
        (uuid? smth) smth))


(defn parse-int [value]
  (cond
    (integer? value) value
    (string? value)  (try (Long/parseLong ^String value 10)
                          (catch Exception _ nil))))

;;; Context

(def ^:dynamic *ctx* nil)


(defmacro ctx [ctx-data & body]
  `(binding [*ctx* (merge *ctx* ~ctx-data)]
     ~@body))


;;; HTTP


(defn route [path qs]
  (if (empty? qs)
    path
    (str path "?" (codec/form-encode qs))))


(defn post!
  ([url] (post! url nil))
  ([url body]
   (-> @(http/request {:method  :post
                       :url     url
                       :headers {"Content-Type" "application/json"}
                       :body    (when body (json/encode body))
                       :timeout (config/TIMEOUT)})
       :body
       (json/parse-string true))))


(defn redir [url]
  {:status 302
   :headers {"Location" url}})


(defn with-cookie [res cookie-name value]
  (assoc-in res [:cookies cookie-name]
    {:value     value
     :path      "/"
     :max-age   (* 3600 24 3650)
     :http-only false}))


(defn msg-redir
  ([message] (msg-redir "/" message))
  ([url message]
   (redir (str url "?message=" message))))
