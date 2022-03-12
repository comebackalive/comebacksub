(ns uapatron.auth
  (:require [clojure.edn :as edn]
            [hashids.core :as h]

            [uapatron.config :as config]
            [uapatron.db :as db]
            [uapatron.time :as t]))


(set! *warn-on-reflection* true)

(def SEP 31)
(def ^:dynamic *uid* :unbound)
(def ^ThreadLocal *user (ThreadLocal.))


;;; Signing

(defn encode [n]
  (h/encode {:salt (config/SECRET) :min-length 4} n))


(defn decode [s]
  (first (h/decode {:salt (config/SECRET) :min-length 4} s)))


;;; Queries

(defn upsert-user-q [email]
  {:insert-into   :users
   :values        [{:email      email
                    :updated_at (t/now)}]
   :returning     [:id :email]
   :on-conflict   [:email]
   :do-update-set [:updated_at]})


;; API

(defmacro with-uid [uid & body]
  `(binding [*uid* ~uid]
     ~@body))

(defn uid []
  (when (= *uid* :unbound)
    (throw (Exception. "auth/*uid* unbound")))
  *uid*)


(defn id->user [id]
  (db/one {:from   [:users]
           :select [:id :email]
           :where  [:= :id id]}))


(defn email->user [email]
  (let [user (db/one {:from   [:users]
                      :select [:id :email]
                      :where  [:= :email email]})]
    (if user
      (db/one {:update :users
               :set    {:updated_at (t/now)}
               :where  [:= :id (:id user)]})
      (db/one {:insert-into :users
               :values      [{:email      email
                              :updated_at (t/now)}]
               :returning   [:id :email]}))))


(defn user []
  (let [id (uid)]
    (or (.get *user)
        (do (.set *user (id->user id))
            (.get *user)))))


(defn wrap-auth [handler]
  (fn [req]
    (binding [*uid* (-> req :session :user_id)]
      (let [res (handler req)]
        (.set *user nil)
        res))))


(defn make-token [data]
  (let [id (:id (db/one {:insert-into [:tokens]
                         :values      [{:data (pr-str data)}]
                         :returning   [:id]}))]
    (encode id)))


(defn token->data [token]
  (let [id (decode token)]
    (-> (db/one {:from   [:tokens]
                 :select [:data]
                 :where  [:and
                          [:> :created_at
                           [:raw "now() - interval '30 min'"]]
                          [:= :id id]]})
        :data
        edn/read-string)))
