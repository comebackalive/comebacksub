(ns uapatron.main
  (:gen-class)
  (:require [mount.core :as mount]
            [org.httpkit.client :as http]
            [org.httpkit.sni-client :as sni-client]
            [sentry-clj.core :as sentry]

            [uapatron.httpd]
            [uapatron.bl.schedule]
            [uapatron.db]
            [uapatron.config :as config]
            [clojure.java.io :as io]))


(set! *warn-on-reflection* true)
(alter-var-root #'http/*default-client* (fn [_] sni-client/default-client))


(def VERSION
  (or (some-> (io/resource "VERSION") slurp)
      "dev"))


(when (seq (config/SENTRY))
  (sentry/init! (config/SENTRY) {:release VERSION}))


(mount/defstate schedule
  :start (uapatron.bl.schedule/run-schedule)
  :stop  (schedule))


(defn -main [& args]
  (case (first args)
    nil  (mount/start)
    "-V" (println "uapatron" VERSION)))
