(ns uapatron.time
  (:import [java.time Instant]
           [java.time.temporal ChronoUnit]))

(defn now [] (Instant/now))

(defn +days  [^Instant t ^Integer amount] (.plus t amount ChronoUnit/DAYS))
(defn +hours [^Instant t ^Integer amount] (.plus t amount ChronoUnit/HOURS))
