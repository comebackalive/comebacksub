(ns uapatron.httpd
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [ring.middleware.defaults :as defaults]
            [ring.middleware.json :as json]
            [ring.middleware.session.cookie :as session-cookie]
            [ring.util.response :as response]
            [reitit.core :as reitit]
            [reitit.coercion.malli]
            [reitit.coercion :as coercion]
            [cheshire.generate]
            [sentry-clj.ring :as sentry]
            [mount.core :as mount]
            [org.httpkit.server :as httpd]
            [kasta.i18n]

            [uapatron.config :as config]
            [uapatron.auth :as auth]
            [uapatron.ui.index :as ui.index]
            [uapatron.ui.payment :as ui.payment]
            [uapatron.api.payment :as api.payment]
            [uapatron.ui.message :as message])
  (:import [hiccup.util RawString]))


(set! *warn-on-reflection* true)


(extend-protocol reitit/Expand
  clojure.lang.Var
  (expand [this _]
    (let [conf (dissoc (meta this)
                 :arglists :line :column :file :name :ns)]
      (assoc conf :handler (deref this)))))


(defn static [{{:keys [path]} :path-params}]
  (-> (response/resource-response path {:root "public"})
      (assoc-in [:headers "Cache-Control"] "max-age=3600")))


(defn version [_req]
  {:status  200
   :headers {"Content-Type" "text/plain"}
   :body    @(requiring-resolve 'uapatron.main/VERSION)})


(defn routes []
  [["/" #'ui.index/index]
   ["/dash" #'ui.payment/dash]
   ["/login" #'ui.index/start-login]
   ["/login/:token" #'ui.index/process-login]
   ["/logout" #'ui.index/logout]
   ["/lang/:lang" #'ui.index/set-lang]
   ["/currency/:currency" #'ui.index/set-currency]
   ["/payment/pause" #'ui.payment/pause]
   ["/payment/resume" #'ui.payment/resume]
   ["/payment/result" #'ui.payment/result]
   ["/api/payment-callback" #'api.payment/payment-callback]
   ["/api/go-to-payment" #'api.payment/go-to-payment]
   ["/version" #'version]
   ["/static/{*path}" #'static]])


(def dev-router #(reitit/router (routes)
                   {:data    {:coercion reitit.coercion.malli/coercion}
                    :compile coercion/compile-request-coercers}))
(def prod-router (reitit/router (routes)
                   {:data    {:coercion reitit.coercion.malli/coercion}
                    :compile coercion/compile-request-coercers}))


(defn maybe-redirect [router req]
  (let [uri ^String (:uri req)
        uri (if (str/ends-with? uri "/")
                 (.substring uri 0 (-> uri count (- 2)))
                 (str uri "/"))]
    (when (reitit/match-by-path router uri)
      {:status  (if (= (:request-method req) :get) 301 308)
       :headers {"Location" uri}})))


(defn access-log [handler]
  (fn [req]
    (try
      (let [res (handler req)]
        (log/debug (:request-method req) {:status (:status res) :uri (:uri req)})
        res)
      (catch Exception e
        (log/error (:request-method req) {:uri (:uri req) :error e})
        (throw e)))))


(defn -app [req]
  (let [method   (:request-method req)
        data     (-> req :match :data)
        handler  (:handler data)
        methods  (:methods data)
        redirect (when-not (:match req)
                   (maybe-redirect (:router req) req))]
    (cond
      (and methods
           (not (contains? methods method))) {:status 405
                                              :body   "Method Not Allowed"}
      handler                                (handler req)
      redirect                               redirect
      :else                                  {:status 404
                                              :body   "Not Found"})))


(defn coerce [handler]
  (fn [req]
    (try
      (let [m        (:match req)
            coercers (if (= (:request-method req) :get)
                       (select-keys (:result m) [:query])
                       (:result m))
            coerced  (coercion/coerce-request coercers req)
            data     (into {} (for [[k v] coerced]
                               (case k
                                 :form      [:form-params v]
                                 :query     [:query-params v]
                                 :path      [:path-params v]
                                 :multipart [:multipart-params v])))]
        (handler (merge req data)))
      (catch clojure.lang.ExceptionInfo e
        (let [data (ex-data e)]
          (if-let [status (case (:type data)
                            ::coercion/request-coercion  400
                            ::coercion/response-coercion 500
                            nil)]
            {:status status
             :body   (coercion/encode-error data)}
            (throw e)))))))


(defn reitit-route [handler]
  (fn [req]
    (let [router (if (config/DEV)
                     (dev-router)
                     prod-router)
          m      (reitit/match-by-path router (:uri req))]
      (handler (assoc req
                 :match m
                 :path-params (:path-params m)
                 :router router)))))


(defn render-html [handler]
  (fn [req]
    (let [res (handler req)]
      (if (instance? RawString (:body res))
        (update res :body str)
        res))))


(defn settings-mw [handler]
  (fn [req]
    (let [lang     (or (config/LANGS (get-in req [:cookies "lang" :value]))
                       "en")
          currency (get {"en" "USD", "uk" "UAH"} lang)]
      (binding [kasta.i18n/*lang* lang
                config/*currency* currency]
        (handler req)))))


(defn make-app []
  (-> -app
      (render-html)
      (settings-mw)
      (coerce)
      (reitit-route)
      (message/message-mw)
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
