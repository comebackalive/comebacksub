(ns build
  (:import [java.time LocalTime])
  (:require [clojure.tools.build.api :as b]
            [clojure.java.io :as io]))


(defn minutes-today []
  (-> (LocalTime/now) .toSecondOfDay (/ 60) int))


(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def version (format "1.%s-%s" (b/git-count-revs nil) (minutes-today)))
;; (def uber-file (format "target/uapatron-%s.jar" version))
(def uber-file "target/uapatron.jar")


(defn clean [_]
  (b/delete {:path "target"}))


(defn uber [_]
  (clean nil)
  (b/copy-dir {:src-dirs   ["src" "resources"]
               :target-dir class-dir})
  (spit (io/file class-dir "VERSION") version)
  (b/compile-clj {:basis     basis
                  :src-dirs  ["src"]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis     basis
           :main      'uapatron.main}))
