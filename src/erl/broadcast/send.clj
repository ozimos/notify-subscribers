(ns erl.broadcast.send
  (:require [erl.broadcast.db :as db]
            [next.jdbc :as jdbc]
            [erl.broadcast.rmq :as rmq]
            [erl.broadcast.config :as config]
            [erl.broadcast.targets :as target]
            [langohr.confirm   :as lcf]
            [taoensso.timbre :as timbre :refer [info]]
            [clojure.core.async :as async :refer [chan go <! >!! timeout thread]]))

(def db-counter (atom 0))

(defn publisher [config {:keys [ch] :as rabbit} async-chan]
  (go
    (let [{:keys [pause-count pause-time]} (config/send-spec config)]
      (while true
        (while (< 0 (mod @db-counter pause-count))
          (swap! db-counter inc)
          (rmq/publish-message rabbit (<! async-chan)))
        (info "No of messages sent: " @db-counter)
        (lcf/wait-for-confirms ch)
        (info "All confirms arrived...")
        (<! (timeout pause-time))))))

(defn recipients-from-db [config pool async-chan]
  (thread
    (transduce
     (map (fn [result] (>!! async-chan (target/config-message config result)) 1))
     +
     0
     (jdbc/plan pool
                (target/config-query config)))))

(defn send-over-channel
  [{::db/keys [pool]
    ::config/keys [config]
    ::rmq/keys [rabbit]}]
  (let [{:keys [chan-buffer]} (config/send-spec config)
        c (chan chan-buffer)]
    (publisher config rabbit c)
    (recipients-from-db config pool c)))


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