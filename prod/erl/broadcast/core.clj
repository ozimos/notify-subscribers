(ns erl.broadcast.core
  (:gen-class)
  (:require
   erl.broadcast.db
   erl.broadcast.config
   erl.broadcast.rmq
   [erl.broadcast.send :refer [send-over-channel]]
   [integrant.core :as ig]
   [clojure.java.io :as io]))

(defn get-config []
  (ig/read-string (slurp (io/resource "system.edn"))))

(defn -main []
  (let [config (get-config)] 
    (ig/load-namespaces  config)
    (send-over-channel (ig/init config))))

