(ns uapatron.bl.schedule
  (:import [java.time Instant])
  (:require [clojure.string]
            [uapatron.db :as db]))


(def BATCH 100)

(defn complete-scheduled-q
  [uuid]
  {:update    :transaction_log
   :set       {:scheduled_for nil}
   :where     [:and [:= :transaction uuid]
               (db/->transaction-type :Scheduled)]
   :returning [:transaction]})


(defn get-scheduled-transactions-q
  [now]
  {:from   [[:transaction_log :tl]]
   :join   [[:cards :c] [:= :c.id :tl.card_id]
            [:users :u] [:= :u.id :tl.user_id]]
   :select [:tl.transaction
            :tl.amount
            :tl.type
            :c.card_type
            :c.token
            :c.card_pan
            :c.card_info]
   :where  [:and [:= :scheduled_for (db/call :date (db/call :timezone "UTC" now))]
            [:= :type (db/->transaction-type :Scheduled)]
            [:= :c.deleted_at nil]]
   :limit  BATCH})


(defn process-scheduled!
  [scheduled-processor]
  (let [now (Instant/now)]
    (loop [trans (db/q (get-scheduled-transactions-q now))]
      (when-not  (empty? trans)
        (doseq [t trans]
          (scheduled-processor t)
          (db/one (complete-scheduled-q (:transaction t))))
        (recur (db/q (get-scheduled-transactions-q now)))))))
