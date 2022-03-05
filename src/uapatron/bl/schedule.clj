(ns uapatron.bl.schedule
  (:import [java.time LocalTime])
  (:require [clojure.tools.logging :as log]

            [uapatron.db :as db]
            [uapatron.bl.fondy :as fondy]))


(def BATCH 100)
(def NEXT-DAY-CHARGE
  "Time when we start charging for the next day"
  (LocalTime/of 12 0))


(defn uids-to-charge-q [which-day]
  {:from     [[:payment_settings :ps]]
   :select   [:ps.id]
   :where    [:and [:< :next_payment_at
                    (case which-day
                      :yesterday [:raw "current_date"]
                      :today     [:raw "current_date + 1"]
                      :tomorrow  [:raw "current_date + 2"])]
              [:not= :ps.amount nil]
              [:not= :ps.card_id nil]]
   :order-by [[:ps.id :desc]]
   :limit    1000})


(defn process-scheduled!
  ([]
   (process-scheduled!
     (if (.isBefore (LocalTime/now) NEXT-DAY-CHARGE)
       :yesterday
       :today)))
  ([which-day]
   (let [ids (db/q (uids-to-charge-q which-day))]
     (for [item ids]
       (fondy/process-recurrent-payment! (:id item))))))


(defn run-schedule []
  (let [stop (atom false)
        t (Thread.
            (fn []
              (log/debug "schedule")
              (if @stop
                (log/info "stop")

                (do
                  (try
                    (process-scheduled!)
                    (catch Exception e
                      (log/error e "cron error")))
                  (try
                    (Thread/sleep 300000)
                    (catch InterruptedException _
                      (log/info "stop")))
                  (recur)))))]
    (.start t)
    (fn []
      (reset! stop true)
      (.interrupt t))))
