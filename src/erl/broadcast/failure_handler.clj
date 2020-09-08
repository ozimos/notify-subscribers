(ns erl.broadcast.failure-handler
  (:require [next.jdbc :as jdbc]
            [next.jdbc.prepare :as p]
            [erl.broadcast.config :as config]
            [erl.broadcast.targets :as target]
            [langohr.core      :as rmq]
            [langohr.consumers   :as lc]
            [langohr.basic     :as lb]
            [langohr.channel   :as lch]
            [langohr.queue     :as lq]
            [langohr.exchange  :as le]
            [taoensso.timbre :as timbre :refer [info]]
            [clojure.core.async :as async :refer [chan <!! >!! timeout close!]]))

(defn get-message-handler [to-db-chan ctrl-chan]
  (fn [ch {:keys [redelivery? delivery-tag] {:strs [server]} :headers} ^bytes payload]
    (if (and (= "erl.broadcast" (str server)) redelivery?)
      (do
        (async/put! to-db-chan (vector (:msisdn (target/json-bytes->edn payload))))
        (lb/ack ch delivery-tag)
        (>!! ctrl-chan true))
      (lb/nack ch delivery-tag false false))))

(defn get-batch [chan-batch-size to-db-chan]
  (<!! (async/into [] (async/take chan-batch-size to-db-chan))))

(defn persist-to-db [con ps to-db-chan batch-size chan-batch-size]
  (loop []
    (if-some [msisdn (<!! to-db-chan)]
      (do
        (jdbc/execute-one! (p/set-parameters ps msisdn))
        (p/execute-batch!
         ps
         (get-batch chan-batch-size to-db-chan)
         {:batch-size (min batch-size chan-batch-size)})
        (recur))
      (.close con))))

(defn controller [ch to-db-chan queue queue-timeout]
  (let [ctrl-chan (chan)
        message-handler (get-message-handler to-db-chan ctrl-chan)
        consumer (lc/create-default ch {:handle-delivery-fn message-handler})
        cons-tag (lb/consume ch queue consumer {:arguments {"x-priority" 10}})]
    (async/go-loop []
      (if (async/alt!  ctrl-chan true
                       (timeout queue-timeout) false)
        (recur)
        (do
          (lb/cancel ch cons-tag)
          (rmq/close ch)
          (close! to-db-chan)
          (info "done checking for failed messages"))))))

(defn failures-to-db [fds config {:keys [conn queue dl-exchange dl-queue dl-routing-key dl-ttl exchange routing-key]}]
  (let [failed-table-name (target/get-failed-table-name config)
        to-db-chan (chan)
        {:keys [queue-timeout batch-size chan-batch-size]} (config/persist-spec config)
        _ (jdbc/execute-one! fds [(target/prepare-failed-table failed-table-name)])
        con (jdbc/get-connection fds)
        ps  (jdbc/prepare con [(target/insert-failed failed-table-name)])
        ch (lch/open conn)
        dl-queue' (:queue (lq/declare ch dl-queue {:durable true
                                                   :arguments {"x-message-ttl" dl-ttl
                                                               "x-expires" dl-ttl
                                                               "x-dead-letter-exchange" exchange
                                                               "x-dead-letter-routing-key" routing-key}}))]
    (le/direct ch dl-exchange {:auto-delete true})
    (lq/bind ch dl-queue' dl-exchange {:routing-key dl-routing-key})
    (controller ch to-db-chan queue queue-timeout)
    (persist-to-db con ps to-db-chan batch-size chan-batch-size)
    (info "Finished handling failed messages")))