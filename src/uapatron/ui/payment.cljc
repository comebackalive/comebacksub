(ns uapatron.ui.payment
  (:require [rum.core :as rum]))



;; (def FORM
;;   {language	
;;    amount
;;    currency	
;;    orderDate	
;;    orderReference
;;    productPrice[]	Массив цен на товары (суммарно должен быть равен полю amount)
;;    productCount[]	Массив количества товаров
;;    productName[]	Массив названия товаров
;;    merchantAccount	Идентификатор торговца (доступен в ЛК)
;;    merchantDomainName	Доменное имя мерчанта
;;    merchantSignature	Подпись запроса
;;    merchantTransactionType	Тип транзакции. Может принимать одно из следующих значений:
;;    • AUTO (по умолчанию)
;;    • AUTH (блокировка денег на карте)
;;    • SALE (списание без блокировки)
;;    merchantTransactionSecureType	Тип безопасности при проведении оплаты клиентом. Принимает значение:
;;    • AUTO
;;    serviceUrl	URL куда будет отправлен Callback
;;    returnUrl	URL куда будет пренаправлен пользователь после оплаты
;;    clientPhone	Телефон плательщика в формате 380YYXXXXXXX
;;    clientFirstName	Имя плательщика
;;    clientLastName	Фамилия плательщика
;;    clientEmail	E-mail плательщика
;;    clientAccountId	Идентификатор клиента в разрезе} магазина (нужно передавать для получения ассетов карты)
;; enabledPaymentMethods	Массив с типами платежных систем, которые отображать в форме. Возможные значения в массиве: card, privat24 (позже googlePay, applePay)
;; paymentMethod	Платежная система выбранная для оплаты в обход страницы с формой для ввода карты. Может принимать одно с значений card, privat24 (позже googlePay, applePay)
;;   )

(defn make-payment-form
  [])


(defn render-form
  []
  (rum/render-static-markup (make-payment-form)))


(defn page
  []
;;   <!doctype html>
;; <html>
;;   <head>
;;     <title>UA patron base page</title>
;;   </head>
;;   <body>
;;     <p>This is an example paragraph. Anything in the <strong>body</strong> tag will appear on the page, just like this <strong>p</strong> tag and its contents.</p>
;;   </body>
;; </html>
  
  )

