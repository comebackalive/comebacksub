(ns uapatron.main
  (:gen-class)
  (:require [mount.core :as mount]
            [org.httpkit.client :as http]
            [org.httpkit.sni-client :as sni-client]

            [uapatron.httpd]
            [uapatron.db]))


(set! *warn-on-reflection* true)
(alter-var-root #'http/*default-client* (fn [_] sni-client/default-client))


(defn -main [& args]
  (mount/start))
