(ns uapatron.time
  (:refer-clojure :exclude [short])
  (:import [java.time Instant ZoneOffset LocalDateTime]
           [java.time.temporal ChronoUnit]
           [java.time.format DateTimeFormatter]))

(set! *warn-on-reflection* true)

(defn now [] (Instant/now))


(defn +days   [^long amount ^Instant t] (.plus t amount ChronoUnit/DAYS))
(defn +months [^long amount ^Instant t] (.plus t amount ChronoUnit/MONTHS))



(def ^DateTimeFormatter short-fmt
  (-> (DateTimeFormatter/ofPattern "MMMM, d")
      (.withZone ZoneOffset/UTC)))


(defn short [t]
  (.format short-fmt t))

;;  useful formatters 
(def dd-MM-yyyy-HH-mm-ss (DateTimeFormatter/ofPattern "dd.MM.yyyy HH:mm:ss"))

(defn parse [fmt smth]
  (when (string? smth)
    (.toInstant (LocalDateTime/parse smth  fmt) ZoneOffset/UTC)))

(def parse-dd-MM-yyyy-HH-mm-ss (partial parse dd-MM-yyyy-HH-mm-ss))
#_(parse-dd-MM-yyyy-HH-mm-ss "01.04.2022 00:00:00")

