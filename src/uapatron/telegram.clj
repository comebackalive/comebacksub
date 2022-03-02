(ns uapatron.telegram
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clojure.core.strint :refer [<<]]
            [org.httpkit.client :as http]
            [cheshire.core :as json]

            [uapatron.config :as config]
            [uapatron.auth :as auth]))


(set! *warn-on-reflection* true)


(defn -req!
  "Inner function to make API calls in case you need more than just parsed
  response (i.e. status code etc)"
  [method url opts]
  (let [{:keys [error] :as res} @(http/request
                                   (assoc opts
                                     :url    url
                                     :method method))]
    (if error
      (throw (ex-info "http error" {:response res} error))
      res)))


(defn req!
  "Make Telegram API calls"
  [method api-name params]
  (let [url  (format "https://api.telegram.org/bot%s/%s"
               (config/TGTOKEN)
               api-name)
        res  (-req! method url (if (= method :get)
                                 {:query-params params}
                                 {:body    (json/generate-string params)
                                  :headers {"Content-Type" "application/json"}}))
        data (-> res
                 :body
                 (json/parse-string keyword))]
    (if (:error_code data)
      (throw (ex-info (:description data) {:data     data
                                           :response res}))
      (with-meta data {:response res}))))


(defn req-file!
  "Get a file from Telegram"
  [file-path]
  (let [url (format "https://api.telegram.org/file/bot%s/%s"
              (config/TGTOKEN)
              file-path)]
    (-req! :get url nil)))


(defn reply [message text & [{:keys [nopreview
                                     forcereply
                                     edit]}]]
  (let [chat-id (if (map? message)
                  (-> message :chat :id)
                  message)
        opts    (cond-> {:chat_id    chat-id
                         :message_id edit
                         :parse_mode "HTML"
                         :text       text}
                  nopreview  (assoc :disable_web_page_preview true)
                  forcereply (assoc :reply_markup {:force_reply true}))]
    (log/info "reply" opts)
    (if (:message_id opts)
      (req! :post "editMessageText" opts)
      (req! :post "sendMessage" opts))))


(defn parse-mapping [mapping]
  (let [[pat dest] (str/split mapping #"=>" 2)]
    [(re-pattern (str/trim pat))
     (str/trim dest)]))


;;; logic

(defn start-setup [message]
  (let [user auth/*user*]
    (reply (-> message :chat :id)
      (str (<< "Hey ~(:github user)!")
        (when (or (:repo user) (:path user))
          (<< " Your current settings: repo - <code>~(:repo user)</code>, path - <code>~(:path user)</code>."))
         " Please tell me your target repo name.")
      {:forcereply true})))


(defn start-auth [message]
  (let [world "World"]
    (reply message (<< "Hello ~{world}")
      {:nopreview true}))
  #_
  (let [url (auth/oauth-url {:chat_id  (-> message :chat :id)
                             :telegram (-> message :chat :username)})]
    (reply message
      (format "<a href=\"%s\">Login with Github</a>" url)
      {:nopreview true})))


;;; routing

(defn reply-to? [message re]
  (and (:reply_to_message message)
       (->> message
            :reply_to_message
            :text
            (re-find re))))


(defn process-update [upd]
  (log/info ::process upd)
  (let [message (:message upd)
        user    (auth/user-by-chat (-> message :chat :id))]
    (auth/with-user user
      (cond
        (= (-> message :text) "/start")                (start-auth message)
        (= (-> message :text) "/setup")                (start-setup message)
        ;; (-> message :document :file_id)                (process-bundle message)
        ;; (reply-to? message #"\brepo\b")                (process-repo message)
        ;; (reply-to? message #"where to put your posts") (process-path message)
        ;; (reply-to? message #"mapping")                 (process-mapping message)
        :else
        (reply message
          "Can't understand you. Please reply to a question or, if you want to restart, send <code>/setup</code>")))))


(defn get-updates-or-else [update-id]
  (try
    (req! :get "getUpdates"
      (cond-> {:timeout 60}
        ;; https://core.telegram.org/bots/api#getupdates
        ;; "must be greater by one than the highest ..."
        update-id (assoc :offset (inc update-id))))
    (catch Exception _
      (Thread/sleep 10))))


(defn start-poll []
  (let [stop (atom false)
        t    (Thread.
               (fn []
                 (loop [update-id nil]
                   (log/debug "poll" {:update-id update-id})
                   (if @stop
                     (log/info "stop")
                     (let [updates (get-updates-or-else update-id)
                           new-id  (-> updates :result last :update_id)]
                       (run! process-update (:result updates))
                       (recur (or new-id update-id)))))))]
    (.start t)
    #(reset! stop true)))
