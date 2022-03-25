(ns uapatron.ui.message
  (:require [hiccup2.core :as hi]))


(def ^:dynamic *messages* nil)


(defn ensure-vec [v]
  (cond
    (vector? v) v
    (nil? v)    nil
    :else       [v]))


(defn message-mw [handler]
  (fn [req]
    (binding [*messages* (ensure-vec (get-in req [:query-params "message"]))]
      (handler req))))


(def MESSAGES
  {"unauthenticated"    "Please log in to continue."
   "successful-payment" "Your payment was successfull!"
   "no-data"            "Insufficient data supplied, please retry."})


(defn Messages []
  (hi/html
    (for [message *messages*]
      [:p.message (get MESSAGES message message)])
    (when (seq *messages*)
      [:script (hi/raw "(function() {
        if (!location.search) return;
        var q = new URLSearchParams(location.search);
        q.delete('message');
        var qs = q.toString();
        var url = location.pathname + (qs ? '?' + qs : '');
        window.history.replaceState(null, null, url);
      })();")])))

