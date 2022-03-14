(ns uapatron.ui.base
  (:import [java.util.zip Adler32])
  (:require [hiccup2.core :as hi]
            [hiccup.page :refer [doctype]]
            [kasta.i18n]

            [uapatron.auth :as auth]
            [uapatron.ui.message :as message]
            [clojure.java.io :as io]))

(set! *warn-on-reflection* true)


(defn adler32 [^bytes ba]
  (-> (doto (Adler32.)
        (.update ba))
      .getValue))


(def static
  (memoize
    (fn [path]
      (let [data (-> (io/resource (str "public/" path))
                     io/input-stream
                     .readAllBytes)]
        (format "/static/%s?v=%s" path (adler32 data))))))


(def SOCIAL
  [["facebook.com" "f.png" "https://www.facebook.com/backandalive"]
   ["twitter.com" "t.png" "https://twitter.com/BackAndAlive"]
   ["instagram.com" "i.png" "https://www.instagram.com/savelife.in.ua/"]
   ["tiktok.com" "tk.png" "https://vm.tiktok.com/ZMeJ7ffef/"]])

(defn head []
  (hi/html
    [:head
     [:meta {:charset "utf-8"}]
     [:title "Save lives in Ukraine | Come Back Alive"]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]

     [:link {:rel "shortcut icon" :type "image/png" :href (static "favicon.png")}]
     [:link {:rel "stylesheet" :href "https://cdnjs.cloudflare.com/ajax/libs/normalize/8.0.1/normalize.min.css"}]
     [:link {:rel "stylesheet" :href "https://fonts.googleapis.com/css?family=Roboto"}]
     [:link {:rel "stylesheet" :href (static "main.css")}]
     #_[:script {:src (static "twinspark.js") :async true}]]))


(defn header []
  (hi/html
    [:header.header
     [:div.container
      [:nav
       [:ul
        [:li
         [:a.header__logo {:href "/" :title "COME BACK ALIVE"}
          [:img {:src (static "img/logo.png")}]]]
        [:li.header__by
         [:span #t "DEFEND UKRAINE TOGETHER"]
         [:span "by "]
         [:a {:href "https://comebackalive.in.ua/" :target "_blank"} "comebackalive.in.ua"]]
        [:li.header__contacts
         [:span.header__contacts-wrap
          [:span #t "For appeals from the military:"]
          [:span "068 500 88 00"]]
         [:span.header__contacts-wrap
          [:span #t "About cooperation:"]
          [:span
           "068 359 63 80 (Kyivstar)" [:br]
           "063 597 15 31 (Lifecell)" [:br]
           "095 055 34 37 (Vodafone)"]]]
        [:li.header__logout
         (if (auth/uid)
           [:a.header__logout-btn {:href "/logout"} #t "Logout"]
           [:a.header__logout-btn {:href "/login"} #t "Login"])
         [:div.header__select-lang
          (if (= kasta.i18n/*lang* "uk")
            [:a.header__lang-item {:href "/lang/en"} "EN"]
            [:a.header__lang-item {:href "/lang/uk"} "UK"])]]]]]]))


(defn footer []
  (hi/html
    [:footer
     [:div.container
      [:div.footer__inner
       [:div.footer__copy
        [:div
         [:a.footer__logo {:href "/" :title "COME BACK ALIVE"}
          [:img {:src (static "img/logo.png")}]]
         [:span "©2022 by "]
         [:a {:href "https://www.comebackalive.in.ua/" :target "_blank"} "comebackalive.in.ua"]
         [:span " NGO"]]]
       [:div.footer__social
        [:p #t "The Come Back Alive Foundation is no different from ordinary Ukrainians. We, like everyone else, are people who in 2014 had to change their way of life."]
        [:ul.footer__icons
         (for [[title img link] SOCIAL]
           [:li [:a {:href link :target "_blank"}
                 [:img {:title title
                        :src   (static (str "img/" img))}]]])]]]]]))


(defn -wrap [content]
  (hi/html
    (:html5 doctype)
    [:html
     (head)
     [:body
      (header)
      [:main
       (message/Messages)
       content]
      (footer)]]))


(defmacro wrap [& content]
  `(-wrap
     (hi/html ~@content)))
