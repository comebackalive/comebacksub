(ns uapatron.main
  (:gen-class)
  (:require [mount.core :as mount]
            [org.httpkit.client :as http]
            [org.httpkit.sni-client :as sni-client]

            ;;[uapatron.telegram :as telegram]
            [uapatron.httpd]
            [uapatron.db]))


(set! *warn-on-reflection* true)
(alter-var-root #'http/*default-client* (fn [_] sni-client/default-client))


;; (mount/defstate poller
;;   :start (telegram/start-poll)
;;   :stop (poller))


(defn -main [& args]
  (mount/start))
