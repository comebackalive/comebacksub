(ns uapatron.ui.base
  (:require [hiccup2.core :as hi]
            [hiccup.page :refer [doctype]]
            [uapatron.auth :as auth]))

(set! *warn-on-reflection* true)


(defn head []
  (hi/html
    [:head
     [:meta {:charset "utf-8"}]
     [:title "Save lives in Ukraine | Come Back Alive"]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]

     [:link {:rel "stylesheet" :href "https://classless.de/classless.css"}]]))


(defn header []
  (hi/html
    [:nav
     [:ul
      [:li [:a {:href "/"} "COME BACK ALIVE"]]
      (when (auth/uid)
        [:li {:class "float-right"} [:a {:href "/logout"} "Logout"]])]]))


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
  `(str (-wrap
          (hi/html ~@content))))