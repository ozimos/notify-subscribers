(ns user
  (:require
   erl.broadcast.db
   erl.broadcast.config
   erl.broadcast.rmq
   [erl.broadcast.send :refer [send-over-channel]]
   [integrant.core :as ig]
   [integrant.repl :refer [go halt  reset]]
   [integrant.repl.state :refer [system config]]
   [clojure.java.io :as io]))

(integrant.repl/set-prep!
 (fn []
   (merge
    (ig/read-string (slurp (io/resource "system.edn")))
    (ig/read-string (slurp (io/resource "resources/dev.edn"))))))

(comment
  (go)
  config
  system
  (send-over-channel system)
  (reset)
  (halt))
