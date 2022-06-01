(ns uapatron.bl.solidgate
  (:import [javax.crypto Mac]
           [javax.crypto.spec SecretKeySpec])
  (:require [clojure.edn :as edn]
            [cheshire.core :as json]
            [org.httpkit.client :as http]
            [clojure.tools.logging :as log]
            [ring.util.codec :as codec]

            [uapatron.config :as config]
            [uapatron.utils :as utils]
            [uapatron.bl.fondy :as fondy]
            [uapatron.db :as db]))

(def BASE "https://payment-page.solidgate.com/api/v1")


(defn hmac-sha512 [^String key ^String s]
  (let [hmac (Mac/getInstance "HmacSHA512")
        spec (SecretKeySpec. (.getBytes key "UTF-8") "HmacSHA512")]
    (.init hmac spec)
    (.doFinal hmac (.getBytes s "UTF-8"))))


(defn sign [ctx-str]
  (-> (hmac-sha512 (config/SOLIDGATE-KEY)
        (str (config/SOLIDGATE-ID) ctx-str (config/SOLIDGATE-ID)))
      utils/hex
      utils/bytes
      codec/base64-encode))


(defn req! [url ctx]
  (let [json (json/generate-string ctx)
        res  @(http/request {:method  :post
                             :url     (str BASE url)
                             :headers {"Merchant"  (config/SOLIDGATE-ID)
                                       "Signature" (sign json)}
                             :body    json
                             :timeout (config/TIMEOUT)})
        data (-> res :body (json/parse-string true))]
    (log/debugf "req %s %s: %s" (:status res) url ctx)
    (with-meta (or data {}) {:original res})))


(defn one-time-link [config]
  (let [desc (str "Donation to Come Back Alive"
               (when (:tags config)
                 (str " #" (first (:tags config)))))
        data (req! "/init"
               {:order
                {:currency          (:currency config "UAH")
                 :amount            (* 100 (:amount config))
                 :order_id          (fondy/make-order-id
                                      (:tags config) (:hiddens config))
                 :order_description desc
                 :fail_url          (utils/route (:next config) {:payment_result "fail"})
                 :success_url       (utils/route (:next config) {:payment_result "success"})
                 :redirect_url      (utils/route (:next config) {:payment_result "redirect"})
                 :callback_url      (str "https://" (config/DOMAIN) "/api/payment/solidgate")}

                :page_customization
                {:public_name       "Come Back Alive"
                 :order_title       "Donation to Come Back Alive"
                 :order_description desc}})]
    (if (:error data)
      (throw (ex-info "Error getting payment link" data))
      (:url data))))

(comment
  (req! "/init"
    {:order {:currency "USD", :amount 100, :order_id "cba-180dbb25eb9b4915~#TEST~_", :order_description "Donation to Come Back Alive #TEST"}, :page_customization {:public_name "Come Back Alive", :order_title "Donation to Come Back Alive", :order_description "Donation to Come Back Alive #TEST"}}))


(defn res->payload [res]
  (or (:payload res)
      (edn/read-string "nil")))


(def STATUS-MAPPING
  {"created"    :Created
   "processing" :InProcessing
   "3ds_verify" :InProcessing
   "declined"   :Declined
   "approved"   :Approved
   "refunded"   :Refunded})


(defn write-transaction!
  "Docs: https://dev.solidgate.com/developers/documentation/introduction/webhooks#order_status_callback"
  [{:keys [order] :as res}]
  (let [status (or (get STATUS-MAPPING (:status order))
                   (throw (ex-info "Uknown status" res)))
        amount (quot (:amount order) 100)
        record {:amount    amount
                :order_id  (:order_id order)
                :card_id   nil
                :user_id   nil
                :type      (db/->transaction-type status)
                :currency  (db/->currency-type (:currency order))
                :data      (db/as-jsonb res)
                :processed nil}]
    (db/one {:insert-into :transaction_log
             :values      [record]
             :returning   [:id :type :order_id]})))
