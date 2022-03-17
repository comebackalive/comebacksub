(ns uapatron.utils
  (:import [java.util UUID])
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [org.httpkit.client :as http]
            [cheshire.core :as json]
            [ring.util.codec :as codec]

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


(defn parse-int [value]
  (cond
    (integer? value) value
    (string? value)  (try (Long/parseLong ^String value 10)
                          (catch Exception _ nil))))

(defn -parse-accept-element [^String s]
  (let [[lang q]      (.split s ";q=")
        [lang locale] (.split ^String lang "-")]
    {:lang   lang
     :locale locale
     :q      (or (try (Double/parseDouble q)
                      (catch Exception _ nil))
                 1.0)}))


(defn parse-accept-language [^String s]
  (when (seq s)
    (->> (.split s ",")
         (map -parse-accept-element)
         (sort-by :q >))))

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
