(ns uapatron.bl.payment
  (:require [clojure.string]
            [pandect.algo.md5 :as md5]
            [uapatron.config :as config]
            [clj-time.coerce :as tc]))

(def PRODUCT-NAME "Благодійність, відправлення фонду Повернись живим")

(defn service-url [] (str (config/DOMAIN) "/api/service-cb"))
(defn success-url [] (str (config/DOMAIN) "/ui/success"))
(defn merchant-domain [] (config/DOMAIN))


(defn *sign [request auth-data]
  (let [v (->> request
            (map second)
            (flatten)
            (clojure.string/join \;))]
    (md5/md5-hmac v auth-data)))


(defn sign
  ([ctx auth-data fields] (sign ctx auth-data fields :merchantSignature))
  ([ctx auth-data fields sign-key]
   (let [values    (map #(get ctx %) fields)
         request   (map vector fields values)
         signature (*sign request auth-data)]
     (assoc ctx sign-key signature))))


(defn make-payment-stub
  []
  {:}
  
  )


(defn make-payment-ctx [{:keys [last-name
                                first-name
                                phone
                                email]}]
  (let [order-ref (str "uapatreon" ":" (uuid))]
    (sign {:merchantAccount               config/MERCHANT
           :merchantDomainName            (merchant-domain)
           :merchantTransactionSecureType "AUTO"
           :merchantTransactionType       "AUTH"
           :language                      "UA"
           :currency                      "UAH"
           :serviceUrl                    (service-url)
           :returnUrl                     (success-url)
           :orderReference                order-ref
           :orderDate                     (str (tc/to-long (:created_at intent)))
           :clientFirstName               first-name
           "productName[0]"               PRODUCT-NAME
           "productCount[0]"              (str 1)
           "productPrice[0]"              (str (:amount intent))
           :clientLastName                last-name
           :clientEmail                   email
           :clientPhone                   phone
           :amount                        (str (:amount intent))
           :transactionType               "PURCHASE"
           :apiVersion                    1
           :clientCountry                 "UKR"
           :recToken                      (or (:token (fetch-card selected-card-id)) "")}
      config/MERCHANT-TOKEN
      [:merchantAccount :merchantDomainName :orderReference :orderDate
       :amount :currency "productName[0]" "productCount[0]" "productPrice[0]"])))


(defn make-recurrent-payment [{:keys [last-name
                                      first-name
                                      phone
                                      email]}]
  (let [order-ref (str "uapatreon" ":" (uuid))]
    (sign {:merchantAccount               config/MERCHANT
           :merchantDomainName            (merchant-domain)
           :merchantTransactionSecureType "AUTO"
           :merchantTransactionType       "AUTH"
           :language                      "UA"
           :currency                      "UAH"
           :serviceUrl                    (service-url)
           :returnUrl                     (success-url)
           :orderReference                order-ref
           :orderDate                     (str (t/to-long (:created_at intent)))
           :clientFirstName               first-name
           "productName[0]"               PRODUCT-NAME
           "productCount[0]"              (str 1)
           "productPrice[0]"              (str (:amount intent))
           :clientLastName                last-name
           :clientEmail                   email
           :clientPhone                   phone
           :amount                        (str (:amount intent))
           :transactionType               "PURCHASE"
           :apiVersion                    1
           :clientCountry                 "UKR"
           :recToken                      (or (:token (fetch-card selected-card-id)) "")}
      config/MERCHANT-TOKEN
      [:merchantAccount :merchantDomainName :orderReference :orderDate
       :amount :currency "productName[0]" "productCount[0]" "productPrice[0]"])))



