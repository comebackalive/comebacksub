(ns uapatron.ui.base
  (:require [hiccup2.core :as hi]
            [hiccup.page :refer [doctype]]

            [uapatron.auth :as auth]
            [uapatron.ui.message :as message]))

(set! *warn-on-reflection* true)


(defn head []
  (hi/html
    [:head
     [:meta {:charset "utf-8"}]
     [:title "Save lives in Ukraine | Come Back Alive"]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]

     [:link {:rel "shortcut icon" :type "image/png" :href "/static/favicon.png"}]
     [:link {:rel "stylesheet" :href "https://classless.de/classless.css"}]
     [:link {:rel "stylesheet" :href "/static/main.css"}]
     [:script {:src "/static/twinspark.js" :async true}]]))


(defn header []
  (hi/html
    [:header
     [:nav.header
      [:ul
       [:li [:a {:href "/"} "COME BACK ALIVE"]]
       (when (auth/uid)
         [:li {:class "float-right"} [:a {:href "/logout"} "Logout"]])]]
     (message/Messages)]))


(defn footer []
  (hi/html
    [:footer
     [:a {:href "https://www.comebackalive.in.ua/"} "Come Back Alive"]]))


(defn -wrap [content]
  (hi/html
    (:html5 doctype)
    [:html
     (head)
     [:body
      (header)
      [:main content]
      (footer)]]))


(defmacro wrap [& content]
  `(-wrap
     (hi/html ~@content)))
