(ns uapatron.auth)


(def ^:dynamic *user* nil)


(defmacro with-user [user & body]
  `(binding [*user* ~user]
     ~@body))


(defn user-by-chat [id]
  {:id id}
  #_ (db/one (assoc (get-user-q nil)
               :where [:= :chat_id (str id)])))


(defn wrap-auth [handler]
  (fn [req]
    (with-user nil
      (handler req))))
