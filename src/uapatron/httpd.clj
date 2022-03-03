(ns uapatron.httpd
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [ring.middleware.defaults :as defaults]
            [ring.middleware.json :as json]
            [ring.middleware.session.cookie :as session-cookie]
            [ring.util.response :as response]
            [reitit.core :as reitit]
            [cheshire.generate]
            [sentry-clj.ring :as sentry]
            [mount.core :as mount]
            [org.httpkit.server :as httpd]

            [uapatron.config :as config]
            [uapatron.auth :as auth]
            [uapatron.ui.index :as ui.index]))


(set! *warn-on-reflection* true)


(defn static [{{:keys [path]} :path-params}]
  (response/resource-response path {:root "public"}))


(defn routes []
  [["/" ui.index/page]
   ["/login" ui.index/start-login]
   ["/login/:token" ui.index/process-login]
   ["/logout" ui.index/logout]
   ["/static/{*path}" static]])


(def dev-router #(reitit/router (routes)))
(def prod-router (reitit/router (routes)))


(defn maybe-redirect [router req]
  (let [uri ^String (:uri req)
        uri (if (str/ends-with? uri "/")
                 (.substring uri 0 (-> uri count (- 2)))
                 (str uri "/"))]
    (when (reitit/match-by-path router uri)
      {:status  (if (= (:request-method req) :get) 301 308)
       :headers {"Location" uri}})))


(defn -app [req]
  (let [router   (if (config/DEV)
                   (dev-router)
                   prod-router)
        m        (reitit/match-by-path router (:uri req))
        redirect (when-not m
                   (maybe-redirect router req))]
    (cond
      m        ((:result m) (assoc req :path-params (:path-params m)))
      redirect redirect
      :else    (do (log/info "unknown request" req)
                   {:status 400
                    :body   "Unknown URL"}))))


(defn access-log [handler]
  (fn [req]
    (try
      (let [res (handler req)]
        (log/info (:request-method req) {:status (:status res) :uri (:uri req)})
        res)
      (catch Exception e
        (log/error (:request-method req) {:uri (:uri req) :error e})
        (throw e)))))


(defn make-app []
  (-> -app
      (access-log)
      (auth/wrap-auth)
      (json/wrap-json-response)
      (json/wrap-json-body {:keywords? true})
      (defaults/wrap-defaults
        {:params    {:urlencoded true
                     :keywordize true
                     :multipart  true}
         :cookies   true
         :session   {:store (session-cookie/cookie-store
                              {:key (.getBytes ^String (config/SECRET) "UTF-8")})}
         :responses {:not-modified-responses true
                     :content-types          true
                     :default-charset        "utf-8"}})
      (sentry/wrap-report-exceptions nil)))


(mount/defstate server
  :start (let [port (config/PORT)]
           (println (str "Listening to http://127.0.0.1:" port))
           (httpd/run-server (make-app) {:port port}))
  :stop (server))
