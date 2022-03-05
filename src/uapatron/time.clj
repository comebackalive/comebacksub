(ns uapatron.time
  (:refer-clojure :exclude [short])
  (:import [java.time Instant ZoneOffset LocalDateTime LocalDate]
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
(def yyyy-MM-dd (DateTimeFormatter/ofPattern "yyyy-MM-dd"))

(defn -parse-time [fmt smth]
  (when (string? smth) (.toInstant (LocalDateTime/parse smth  fmt) ZoneOffset/UTC)))

(defn -parse-date [fmt smth]
  (when (string? smth) (.toInstant (.atStartOfDay (LocalDate/parse smth fmt)) ZoneOffset/UTC)))


(def parse-dd-MM-yyyy-HH-mm-ss (partial -parse-time dd-MM-yyyy-HH-mm-ss))
#_(parse-dd-MM-yyyy-HH-mm-ss "01.04.2022 00:00:00")

(def parse-yyyy-MM-dd (partial -parse-date yyyy-MM-dd))

#_(parse-yyyy-MM-dd "2021-12-21")
 
(defn at-midnight
  [^Instant t]
  (.truncatedTo t ChronoUnit/DAYS))


(defn compare-times
  [time-1 time-2]
  (cond (.isBefore time-1 time-2) :<
        (.isAfter  time-1 time-2) :>
        (= time-1 time-2)         :=))
