(ns uapatron.auth
  (:import [java.util Arrays])
  (:require [clojure.edn :as edn]
            [buddy.core.mac :as mac]
            [alphabase.base58 :as base58]

            [uapatron.config :as config]
            [uapatron.db :as db]
            [uapatron.time :as t]))


(set! *warn-on-reflection* true)

(def SEP 31)
(def ^:dynamic *uid* :unbound)
(def ^ThreadLocal *user (ThreadLocal.))


;;; Signing

(defn -ba-split [^bytes ba needle]
  (let [l      (alength ba)
        needle (int needle)
        idx    (loop [idx 0]
                 (cond
                   (>= idx l)              (dec l)
                   (= needle (nth ba idx)) idx
                   :else                   (recur (inc idx))))]
    [(Arrays/copyOfRange ba 0 ^long idx)
     (Arrays/copyOfRange ba (inc ^long idx) l)]))


(defn sign [value]
  (let [value (pr-str value)
        sig   (mac/hash value {:key (config/SECRET)
                               :alg :hmac+sha256})]

    (-> (concat (.getBytes value "UTF-8") [SEP] sig)
        byte-array
        base58/encode)))


(defn verify [^String s]
  (let [[^bytes value ^bytes sig] (-> s
                                      base58/decode
                                      (-ba-split SEP))]
    (when (mac/verify value sig
            {:key (config/SECRET)
             :alg :hmac+sha256})
      (-> value
          (String. "UTF-8")
          edn/read-string))))


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


(defn user []
  (let [id (uid)]
    (or (.get *user)
        (do (.set *user (db/one {:from   [:users]
                                 :select [:id :email]
                                 :where  [:= :id id]}))
            (.get *user)))))


(defn wrap-auth [handler]
  (fn [req]
    (let [uid (-> req :session :user_id)]
      (binding [*uid* uid]
        (let [res (handler req)]
          (.set *user nil)
          res)))))


(defn email->token [email]
  (sign {:email email
         :ms    (System/currentTimeMillis)}))


(defn token->email [token]
  (when-let [data (verify token)]
    (when (> (:ms data 0)
             (- (System/currentTimeMillis)
                300000))
      (:email data))))
