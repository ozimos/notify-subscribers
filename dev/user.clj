(ns user
  (:require
   [erl.broadcast.db :as db]
   [erl.broadcast.config :as mconfig]
   [erl.broadcast.targets :as target :refer [config-message]]
   [erl.broadcast.rmq :as rmq]
   [erl.broadcast.send :refer [send-over-channel]]
   [integrant.core :as ig]
   [integrant.repl :refer [go halt  reset]]
   [integrant.repl.state :refer [system config]]
   [clojure.java.io :as io]))

(defn get-config []
  (merge
   (ig/read-string (slurp (io/resource "system.edn")))
   (ig/read-string (slurp (io/resource "resources/dev.edn")))))

(integrant.repl/set-prep!
 get-config)

(comment
  (require '[next.jdbc :as jdbc])
  (doc jdbc/get-datasource)
  (go)
  config
  system
  (send-over-channel system)
  
  
  (reset)
  (halt))
