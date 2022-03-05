(ns uapatron.bl.schedule
  (:require [uapatron.db :as db]
            [uapatron.bl.fondy :as fondy]))


(def BATCH 100)


(defn uids-to-charge-q
  []
  {:from     [[:payment_settings :ps]]
   :select   [:ps.id]
   :where    [:and [:< :next_payment_at
                    [:raw "current_date + 1"]]
              [:not= :ps.amount nil]
              [:not= :ps.card_id nil]]
   :order-by [[:ps.id :desc]]
   :limit    100})


(defn process-scheduled!
  []
  (let [ids (db/q (uids-to-charge-q))]
    (for [item ids]
      (fondy/process-recurrent-payment! (:id item)))
    (when (= (count ids) 100)
      (recur))))
