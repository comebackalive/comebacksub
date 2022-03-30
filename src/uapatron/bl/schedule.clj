(ns uapatron.bl.schedule
  (:import [java.time LocalTime])
  (:require [clojure.tools.logging :as log]
            [sentry-clj.core :as sentry]

            [uapatron.db :as db]
            [uapatron.bl.fondy :as fondy]))


(def BATCH 100)
(def NEXT-DAY-CHARGE
  "Time when we start charging for the next day"
  (LocalTime/of 9 0))


(defn ids-to-charge-q [which-day]
  {:from     [[:payment_settings :ps]]
   :select   [:ps.id]
   :where    [:and [:< :next_payment_at
                    (case which-day
                      :yesterday [:raw "current_date"]
                      :today     [:raw "current_date + 1"]
                      :tomorrow  [:raw "current_date + 2"])]
              [:not= :ps.amount nil]
              [:not= :ps.card_id nil]
              [:= :ps.paused_at nil]]
   :order-by [[:ps.id :desc]]
   :limit    100})


(defn process-scheduled!
  ([]
   (process-scheduled!
     (if (.isBefore (LocalTime/now) NEXT-DAY-CHARGE)
       :yesterday
       :today)))
  ([which-day]
   (let [ids (db/q (ids-to-charge-q which-day))]
     (doseq [item ids]
       (fondy/process-recurrent-payment! (:id item))))))


(defn run-schedule []
  (let [stop (atom false)
        id   (format "s%x" (mod (System/currentTimeMillis)
                             1000000))
        t    (Thread.
               (fn []
                 (if @stop
                   (log/infof "schedule %s: stop signal" id)

                   (do
                     (log/debugf "schedule %s" id)
                     (try
                       (process-scheduled!)
                       (catch Exception e
                         (log/error e "cron error")))
                     (try
                       (Thread/sleep 300000)
                       (catch InterruptedException _
                         (log/infof "schedule %s: sleep interrupt" id)))
                     (recur)))))]
    (log/infof "schedule %s: start" id)
    (.start t)
    (fn []
      (reset! stop true)
      (.interrupt t))))
