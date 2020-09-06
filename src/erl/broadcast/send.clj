(ns erl.broadcast.send
  (:require [erl.broadcast.db :as db]
            [next.jdbc :as jdbc]
            [erl.broadcast.rmq :as rmq]
            [erl.broadcast.config :as config]
            [erl.broadcast.targets :as target]
            [langohr.confirm   :as lcf]
            [taoensso.timbre :as timbre :refer [info]]
            [clojure.core.async :as async :refer [chan <!! >!! timeout thread close!]]))


(defn publish-batch [current-count pause-time bound-chan rabbit ch]
  (let [counter  (loop [loop-count current-count]
                   (if-some [message (<!! bound-chan)]
                     (do
                       (rmq/publish-message rabbit message)
                       (recur  (inc loop-count)))
                     loop-count))]
    (info "No of messages sent: " counter)
    (lcf/wait-for-confirms ch)
    (info "All confirms arrived...")
    (<!! (timeout pause-time))
    counter))

(defn publisher [config {:keys [ch] :as rabbit} async-chan]

  (let [{:keys [pause-count pause-time]} (config/send-spec config)]
    (loop [bound-chan (async/take pause-count async-chan) initial-count 0]
      (let [current-count  (publish-batch initial-count pause-time bound-chan rabbit ch)
            next-chan (async/take pause-count async-chan)]
        (when-some [message (<!! next-chan)]
          (rmq/publish-message rabbit message)
          (recur  next-chan (inc current-count)))))
    (info "Exiting publisher")))


(defn recipients-from-db [config ds async-chan]
  (thread
    (transduce
     (map (fn [result] (>!! async-chan (target/config-message config result)) 1))
     +
     0
     (jdbc/plan ds
                (target/config-query config)))
    (info "done with db fetch")
    (close! async-chan)))

(defn send-over-channel
  [{::db/keys [ds]
    ::config/keys [config]
    ::rmq/keys [rabbit]}]
  (let [{:keys [chan-buffer]} (config/send-spec config)
        c (chan chan-buffer)]
    (recipients-from-db config ds c)
    (publisher config rabbit c)
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