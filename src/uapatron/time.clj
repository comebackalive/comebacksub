(ns uapatron.time
  (:refer-clojure :exclude [short])
  (:import [java.time Instant ZoneOffset]
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
