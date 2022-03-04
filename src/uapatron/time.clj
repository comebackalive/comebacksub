(ns uapatron.time
  (:import [java.time Instant]))

(defn now [] (Instant/now))
