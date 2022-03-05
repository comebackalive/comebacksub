(ns uapatron.main
  (:gen-class)
  (:require [mount.core :as mount]
            [org.httpkit.client :as http]
            [org.httpkit.sni-client :as sni-client]

            [uapatron.httpd]
            [uapatron.bl.schedule]
            [uapatron.db]))


(set! *warn-on-reflection* true)
(alter-var-root #'http/*default-client* (fn [_] sni-client/default-client))


(mount/defstate schedule
  :start (uapatron.bl.schedule/run-schedule)
  :stop  (schedule))


(defn -main [& args]
  (mount/start))
