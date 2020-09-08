(ns erl.broadcast.rmq
  (:require [langohr.core      :as rmq]
            [langohr.basic     :as lb]
            [erl.broadcast.config :as config]
            [integrant.core :as ig]))

(defmethod ig/init-key ::rabbit
  [_ {::config/keys [config]}]
  (let [spec (config/rabbit-spec config)
        conn  (rmq/connect spec)]
    (assoc spec :conn conn)))

(defmethod ig/halt-key! ::rabbit
  [_ {:keys [conn]}]
  (rmq/close conn))

(defn publish-message [ch {:keys [routing-key exchange]} message]
  (lb/publish ch exchange routing-key message
              {:content-type "application/octet-stream"
               :headers {"server" "erl.broadcast"}
               :priority 0 :delivery-mode 2 :persistent true}))