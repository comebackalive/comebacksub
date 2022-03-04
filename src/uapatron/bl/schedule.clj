(ns uapatron.bl.schedule
  (:require [clojure.string]
            [uapatron.db :as db]
            [uapatron.time :as t]))


(def BATCH 100)

(defn uids-to-charge-q
  [now]
  {:from     [[:payment_settings :ps]]
   :select   [:ps.id]
   :where    [:and [:= :next_payment_at
                    (db/call :date (db/call :timezone "UTC" now))]
              [:not= nil :ps.default_payment_amount]
              [:not= nil :ps.default_card_id]]
   :order-by [[:ps.id :desc]]
   :limit    100})


(defn process-scheduled!
  [scheduled-processor]
  (let [now (t/now)]
    (loop [ids (db/q (uids-to-charge-q now))]
      (when-not  (empty? ids)
        (doseq [{:keys [id]} ids]
          (scheduled-processor id))
        (recur (db/q (uids-to-charge-q now)))))))
