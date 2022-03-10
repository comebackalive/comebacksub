(ns uapatron.time
  (:refer-clojure :exclude [short])
  (:require [kasta.i18n])
  (:import [java.util Locale]
           [java.time Instant ZoneOffset LocalDateTime LocalDate]
           [java.time.temporal ChronoUnit]
           [java.time.format DateTimeFormatter]))

(set! *warn-on-reflection* true)

(defn now [] (Instant/now))


;;; Arithmetic

(defn +days   [^long amount ^Instant t] (.plus t amount ChronoUnit/DAYS))
(defn +months [^long amount ^Instant t] (.plus t amount ChronoUnit/MONTHS))


(defn ^Instant at-midnight
  [^Instant t]
  (.truncatedTo t ChronoUnit/DAYS))


(defn compare-times
  [^Instant time-1 ^Instant time-2]
  (cond (.isBefore time-1 time-2) :<
        (.isAfter  time-1 time-2) :>
        (= time-1 time-2)         :=))


;;; Formatting patterns

(def uk_UA (Locale. "uk" "UA"))


(def EN
  {"short" (-> (DateTimeFormatter/ofPattern "MMMM, d")
               (.withZone ZoneOffset/UTC))
   "full"  (-> (DateTimeFormatter/ofPattern "dd.MM.yyyy HH:mm:ss")
               (.withZone ZoneOffset/UTC))
   "ymd"   (-> (DateTimeFormatter/ofPattern "yyyy-MM-dd")
               (.withZone ZoneOffset/UTC))})


(def UK
  {"short" (-> (DateTimeFormatter/ofPattern "d MMMM")
               (.withZone ZoneOffset/UTC)
               (.withLocale uk_UA))
   "full"  (-> (DateTimeFormatter/ofPattern "dd.MM.yyyy HH:mm:ss")
               (.withZone ZoneOffset/UTC)
               (.withLocale uk_UA))
   "ymd"   (-> (DateTimeFormatter/ofPattern "yyyy-MM-dd")
               (.withZone ZoneOffset/UTC)
               (.withLocale uk_UA))})


;;; Formatting functions

(defn ^DateTimeFormatter get-fmt [fmt-name]
  (let [fmts (if (= kasta.i18n/*lang* "uk") UK EN)]
    (get fmts fmt-name)))


(defn -format [fmt-name value]
  (.format (get-fmt fmt-name) value))


(defn -parse-time [fmt-name value]
  (when value
    (.toInstant (LocalDateTime/parse value (get-fmt fmt-name)) ZoneOffset/UTC)))


(defn -parse-date [fmt-name value]
  (when value
    (let [date (LocalDate/parse value (get-fmt fmt-name))]
      (.toInstant (.atStartOfDay date) ZoneOffset/UTC))))


;;; Concrete formatters/parsers

(def short (partial -format "short"))
(def parse-dt (partial -parse-time "full"))
(def parse-date (partial -parse-date "ymd"))

(comment
  (parse-dt "01.04.2022 00:00:00")
  (parse-date "2021-12-21"))
