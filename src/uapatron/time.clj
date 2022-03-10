(ns uapatron.time
  (:refer-clojure :exclude [short])
  (:import [java.time Instant ZoneOffset LocalDateTime LocalDate]
           [java.time.temporal ChronoUnit]
           [java.time.format DateTimeFormatter]))

(set! *warn-on-reflection* true)

(defn now [] (Instant/now))


;;; Arithmetic

(defn +days   [^long amount ^Instant t] (.plus t amount ChronoUnit/DAYS))
(defn +months [^long amount ^Instant t] (.plus t amount ChronoUnit/MONTHS))


;;; Formatting patterns

(def ^DateTimeFormatter short-fmt
  (-> (DateTimeFormatter/ofPattern "MMMM, d")
      (.withZone ZoneOffset/UTC)))

(def dd-MM-yyyy-HH-mm-ss (DateTimeFormatter/ofPattern "dd.MM.yyyy HH:mm:ss"))
(def yyyy-MM-dd (DateTimeFormatter/ofPattern "yyyy-MM-dd"))


;;; Formatting functions

(defn -parse-time [fmt value]
  (when value
    (.toInstant (LocalDateTime/parse value fmt) ZoneOffset/UTC)))

(defn -parse-date [fmt value]
  (when value
    (.toInstant (.atStartOfDay (LocalDate/parse value fmt)) ZoneOffset/UTC)))

(defn short [t]
  (.format short-fmt t))

;;  useful formatters



(def parse-dt (partial -parse-time dd-MM-yyyy-HH-mm-ss))
(comment
  (parse-dt "01.04.2022 00:00:00"))

(def parse-yyyy-MM-dd (partial -parse-date yyyy-MM-dd))

#_(parse-yyyy-MM-dd "2021-12-21")

(defn ^Instant at-midnight
  [^Instant t]
  (.truncatedTo t ChronoUnit/DAYS))


(defn compare-times
  [^Instant time-1 ^Instant time-2]
  (cond (.isBefore time-1 time-2) :<
        (.isAfter  time-1 time-2) :>
        (= time-1 time-2)         :=))
