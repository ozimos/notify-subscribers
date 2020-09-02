(ns erl.broadcast.rmq
  (:require [langohr.core      :as rmq]
            [langohr.channel   :as lch]
            [langohr.confirm   :as lcf]
            [langohr.basic     :as lb]
            [erl.broadcast.config :as config]
            [integrant.core :as ig]))

(defmethod ig/init-key ::rabbit
  [_ {::config/keys [config]}]
  (let [spec (config/rabbit-spec config)
        conn  (rmq/connect spec)
        ch    (doto (lch/open conn) (lcf/select))]
    
    (assoc spec :ch  ch :conn conn)))

(defmethod ig/halt-key! ::rabbit
  [_ {:keys [conn ch]}]
  (rmq/close ch)
  (rmq/close conn))

(defn gen-message [result]
  (format "Dear Customer, your extracredit loan of %s is now more than 72hours. Kindly recharge your account to repay your loans." (:amount result)))

(defn publish-message [{:keys [queue exchange ch]} message]
  (lb/publish ch exchange queue message
              {:content-type "application/octet-stream'"
               :priority 0 :delivery-mode 2 :persistent true}))