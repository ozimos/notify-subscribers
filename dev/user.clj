(ns user
  (:require
   [erl.broadcast.db :as db]
   [erl.broadcast.config :as config]
   [erl.broadcast.rmq :as rmq]
   [erl.broadcast.send :refer [send-over-channel]]
   [erl.broadcast.failure-handler :refer [failures-to-db]]
   [erl.broadcast.targets :as target :refer [send-ops]]
   [integrant.core :as ig]
   [integrant.repl :refer [go halt  reset]]
   [next.jdbc :as jdbc]

   [integrant.repl.state :refer [system config]]
   [clojure.java.io :as io]))

(defn get-config []
  (merge
   (ig/read-string (slurp (io/resource "system.edn")))
   (ig/read-string (slurp (io/resource "resources/dev.edn")))))

(integrant.repl/set-prep!
 get-config)

(comment
  (go)
  config
  system
  (jdbc/execute! (::db/ds system) (send-ops {:config (::config/config system) :op :q-recipient}))
  (:database-spec (::config/config system))
  (:failed-spec (::config/config system))
  (apply send-over-channel (map system [::db/ds ::config/config ::rmq/rabbit]))
  (apply failures-to-db (map system [::db/fds ::config/config ::rmq/rabbit]))
  (def cnf-fns (send-ops {:config (::config/config system) :op :confirms}))
  cnf-fns
  (for [msg cnf-fns]
    (msg "200"))
  ((nth cnf-fns 1) "100")
  (reset)
  (halt))
