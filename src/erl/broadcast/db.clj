(ns erl.broadcast.db
  (:require [erl.broadcast.config :as config]
            [integrant.core :as ig]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as result-set]
            [next.jdbc.prepare :as p]))


(defmethod ig/init-key ::ds
  [_ {::config/keys [config]}]
  (jdbc/get-datasource (config/database-spec config)))

(defmethod ig/init-key ::fds
  [_ {::config/keys [config]}]
  (jdbc/get-datasource (config/failed-database-spec config)))

(extend-protocol result-set/ReadableColumn

  ;; Automatically convert java.sql.Array into clojure vector in query
  ;; results
  java.sql.Array
  (read-column-by-label ^clojure.lang.PersistentVector
    [^java.sql.Array v _]
    (vec (.getArray v)))
  (read-column-by-index ^clojure.lang.PersistentVector
    [^java.sql.Array v _2 _3]
    (vec (.getArray v)))

  ;; Output java.time.LocalDate instead of java.sql.Date in query
  ;; results
  java.sql.Date
  (read-column-by-label ^java.time.LocalDate
    [^java.sql.Date v _]
    (.toLocalDate v))
  (read-column-by-index ^java.time.LocalDate
    [^java.sql.Date v _2 _3]
    (.toLocalDate v))

  ;; Output java.time.Instant instead of java.sql.Timestamp in query
  ;; results
  java.sql.Timestamp
  (read-column-by-label ^java.time.Instant
    [^java.sql.Timestamp v _]
    (.toInstant v))
  (read-column-by-index ^java.time.Instant
    [^java.sql.Timestamp v _2 _3]
    (.toInstant v)))


(extend-protocol p/SettableParameter

  ;; Accept java.time.Instant as a query param
  java.time.Instant
  (set-parameter
    [^java.time.Instant v ^java.sql.PreparedStatement ps ^long i]
    (.setTimestamp ps i (java.sql.Timestamp/from v)))

  ;; Accept java.time.LocalDate as a query param
  java.time.LocalDate
  (set-parameter
    [^java.time.LocalDate v ^java.sql.PreparedStatement ps ^long i]
    (.setTimestamp ps i (java.sql.Timestamp/valueOf (.atStartOfDay v)))))
