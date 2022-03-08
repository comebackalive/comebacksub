(ns uapatron.main
  (:gen-class)
  (:require [mount.core :as mount]
            [org.httpkit.client :as http]
            [org.httpkit.sni-client :as sni-client]
            [sentry-clj.core :as sentry]

            [uapatron.httpd]
            [uapatron.bl.schedule]
            [uapatron.db]
            [uapatron.config :as config]))


(set! *warn-on-reflection* true)
(alter-var-root #'http/*default-client* (fn [_] sni-client/default-client))


(when (seq (config/SENTRY))
  (sentry/init! (config/SENTRY)))


(mount/defstate schedule
  :start (uapatron.bl.schedule/run-schedule)
  :stop  (schedule))


(defn -main [& args]
  (mount/start))
