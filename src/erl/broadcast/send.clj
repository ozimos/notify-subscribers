(ns erl.broadcast.send
  (:require [erl.broadcast.db :as db]
            [next.jdbc :as jdbc]
            [erl.broadcast.rmq :as rmq]
            [erl.broadcast.config :as config]
            [erl.broadcast.targets :as target]
            [langohr.confirm   :as lcf]
            [taoensso.timbre :as timbre :refer [info]]
            [clojure.core.async :as async :refer [chan <!! >!! timeout thread]]))

(def loop-counter (atom 0))
(def resume-status (atom false))
(def sending-status (atom true))

(defn publisher [config {:keys [ch] :as rabbit} async-chan]

  (let [{:keys [pause-count pause-time]} (config/send-spec config)]
    (while @sending-status
      (while (or (< 0 (mod @loop-counter pause-count)) (= 0 @loop-counter) @resume-status)
        (swap! loop-counter inc)
        (swap! resume-status (constantly false))
        (rmq/publish-message rabbit (<!! async-chan)))
      (swap! resume-status (constantly true))
      (info "No of messages sent: " @loop-counter)
      (lcf/wait-for-confirms ch)
      (info "All confirms arrived...")
      (<!! (timeout pause-time)))
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
    (swap! sending-status (constantly false))))

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