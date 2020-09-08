(ns erl.broadcast.core
  (:gen-class)
  (:require [erl.broadcast.db :as db]
            [erl.broadcast.config :as config]
            [erl.broadcast.rmq :as rmq]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.core :as appenders]
            [erl.broadcast.send :refer [send-over-channel]]
            [camel-snake-kebab.core :as csk]
            [integrant.core :as ig]
            [clojure.java.io :as io]))

(timbre/merge-config!
 {:appenders 
  {:println {:enabled? false}
   :spit (appenders/spit-appender {:fname (str "./"(csk/->snake_case_string (or (System/getenv "TARGET") "default_throttled_BC")) ".log")})}})

(defn get-config []
  (ig/read-string (slurp (io/resource "system.edn"))))

(defn get-system [config]
  (ig/load-namespaces  config)
  (ig/init config))

(defn -main []
  (let [system (get-system (get-config))
        {::db/keys [ds]
         ::config/keys [config]
         ::rmq/keys [rabbit]} system]
    
    (send-over-channel ds config rabbit)
    (ig/halt! system)))

(comment
  (-main))
