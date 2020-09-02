(ns erl.broadcast.config
  (:require [integrant.core :as ig]
            [clojure.java.io :as io]
            [aero.core :as aero]))

(defmethod ig/init-key ::config
  [_ {::keys [profile]}]
  (aero/read-config (io/resource "config.edn")
                    {:profile profile}))

(defn database-spec [config]
  (:database-spec config))

(defn rabbit-spec [config]
  (:rabbit-spec config))

(defn send-spec [config]
  (:send-spec config))

(defn target-spec [config]
  (:target-spec config))
