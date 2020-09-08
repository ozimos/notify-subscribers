(ns erl.broadcast.send
  (:require [next.jdbc :as jdbc]
            [erl.broadcast.rmq :as rmq]
            [langohr.core      :as lrmq]
            [erl.broadcast.config :as config]
            [erl.broadcast.targets :as target]
            [langohr.confirm   :as lcf]
            [langohr.channel   :as lch]
            [throttler.core :refer [throttle-chan]]
            [taoensso.timbre :as timbre :refer [info log-errors]]
            [clojure.core.async :as async :refer [chan <!! >!! thread close!]]))

(defn publish-batch [current-count pause-count async-chan ch rabbit]
  (<!! (async/transduce
        (map (fn [message] (rmq/publish-message ch rabbit message) 1))
        +
        current-count
        (async/take pause-count async-chan))))

(defn publisher [config {:keys [conn] :as rabbit} async-chan]

  (let [{:keys [pause-count confirm-time]} (config/send-spec config)
        ch    (doto (lch/open conn) (lcf/select))
        confirms (target/send-ops {:config config :op :confirms})]
    (loop [dispatched-count 0]
      (let [current-count (publish-batch dispatched-count pause-count async-chan ch rabbit)]
        (lcf/wait-for-confirms ch confirm-time)
        (info "No of messages sent: " current-count " for Denom: " (System/getProperty "denom.band" "None"))
        (doseq [message-fn confirms]
          (rmq/publish-message ch rabbit (message-fn current-count)))
        (if-some [message (<!! async-chan)]
          (do (rmq/publish-message ch rabbit message)
              (recur (inc current-count)))
          (lrmq/close ch))))
    (info "Exiting publisher")))

(defn recipients-from-db [config ds async-chan]
  (thread
    (transduce
     (map (fn [result]
            (when-let [message (log-errors 
                           (target/send-ops {:config config :op :message} result))]
              (>!! async-chan message)) 
            1))
     +
     0
     (jdbc/plan ds
                (target/send-ops {:config config :op :q-recipient})))
    (info "done with db fetch")
    (close! async-chan)))

(defn send-over-channel
  [ds config rabbit]
  (let [{:keys [chan-buffer throttle-num throttle-intvl]} (config/send-spec config)
        c (chan chan-buffer)]
    (recipients-from-db config ds c)
    (publisher config rabbit (throttle-chan c throttle-num throttle-intvl))
    (info "All done!! Exiting send-over-channel")))


(comment
  (def db {:dbtype "postgresql" :dbname "erlcsdplive"})
  (def ds (jdbc/get-datasource db))

  (jdbc/execute-one! ds ["
create table invoice (
  id serial  primary key,
  product varchar(32),
  unit_price decimal(10,2),
  unit_count int ,
  customer_id int 
)"])
  (reduce
   (fn [cost row]
     (+ cost (* (:unit_price row)
                (:unit_count row))))
   0
   (jdbc/plan ds ["select * from invoice where customer_id = ?" 100])))