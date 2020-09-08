(ns erl.broadcast.targets
  (:require [erl.broadcast.config :as config]
            [clojure.spec.alpha :as s]
            [erl.broadcast.validator :as v :refer [format-amount format-msisdn]]
            [jsonista.core :as j]))

(defn get-category [config] (format-amount config (System/getProperty "denom.band" "5000")))


(defn to-json [msisdn sender message]
  (j/write-value-as-string
   {:id -1, :msisdn (format-msisdn msisdn), :message message, :flash? false, :from sender}))

(defn json-bytes->edn [payload]
  (j/read-value payload j/keyword-keys-object-mapper))

(defn get-recipient-table-name [config]
  (:table (config/database-spec config)))

(defn get-failed-table-name [config]
  (:table (config/failed-database-spec config)))

(defn get-sender [config]
  (:sender (config/send-spec config)))

(defmulti send-ops (fn [{:keys [config op]} & _] [(config/target-spec config) op]))

(defmethod send-ops [:last72hours :q-recipient] [{config :config} & _]
  ["select msisdn, amount from " (get-recipient-table-name config)
   " where repay_time is null and status = 'success' and 
        timestamp between '2020-07-02 00:00:00.000000'::timestamp 
        and current_timestamp - interval '72 hour'"])


(defmethod send-ops [:inactive-subscribers  :q-recipient] [{config :config} & _]
  [(str "select subscriber_fk, amount_loanable from " (get-recipient-table-name config)
        " where amount_loanable = ?") (Integer/parseInt (get-category config))])

(defmethod send-ops [:new-adds  :q-recipient] [{config :config} & _]
  [(str "select msisdn, amount_loanable from " (get-recipient-table-name config)
        " where amount_loanable = ?") (Integer/parseInt (get-category config))])

(defmethod send-ops [:last72hours :message] [{config :config} & [{:last72hours/keys [msisdn]}]]
  (let [message  (format (first (:messages config)))]
    (to-json msisdn (get-sender config) message)))

(defmethod send-ops [:inactive-subscribers :message] [{config :config} & [{:tbl_glo_inactive_subs_sept1/keys [subscriber_fk amount_loanable]}]]
  (let [message (format (first (:messages config)) (format-amount config amount_loanable))]
    (to-json subscriber_fk (get-sender config) message)))

(defmethod send-ops [:new-adds :message] [{config :config} & [{:tbl_glo_aug2020_profiling_newadds/keys [msisdn amount_loanable]}]]
  (let [message (format (first (:messages config)) (format-amount config amount_loanable))]
    (to-json msisdn (get-sender config) message)))

(defn get-confirm-fns [config & msg-fns]
  (for [message-fn msg-fns
        subscriber (:confirms (config/send-spec config))
        :let [str-sub (str subscriber)]
        :when (s/valid? ::v/msisdn str-sub)]
    (fn [count] (to-json str-sub (get-sender config) (message-fn count)))))

(defmethod send-ops [:inactive-subscribers :confirms] [{config :config} & _]
  (get-confirm-fns
   config
   (fn [& _] (format (first (:messages config)) (get-category config)))
   (fn [& [num]] (format (last (:messages config)) num (name (config/target-spec config))))))

(defmethod send-ops [:new-adds :confirms] [{config :config} & _]
  (get-confirm-fns
   config
   (fn [& _] (format (first (:messages config)) (get-category config)))
   (fn [& [num]] (format (last (:messages config)) num (name (config/target-spec config))))))

(defmethod send-ops [:last72hours :confirms] [{config :config} & _]
  (get-confirm-fns
   config
   (fn [& _] (format (first (:messages config)) (get-category config)))
   (fn [& [num]] (format (last (:messages config)) num (name (config/target-spec config))))))

(defn insert-failed  [table]
  (str "insert into " table
       " as not_sent values (?) on conflict (msisdn) do update "
       "set attempts = array_append(not_sent.attempts::TIMESTAMPTZ[], current_timestamp) "
       "where cardinality(not_sent.attempts) < 5"))

(defn prepare-failed-table  [table]
  (str "CREATE TABLE IF NOT EXISTS  " table
       "(
        msisdn BIGINT NOT NULL PRIMARY KEY,
        attempts TIMESTAMPTZ[] NOT NULL DEFAULT ARRAY[CURRENT_TIMESTAMP]
        )"))

(comment
  (insert-failed "sample")
  (j/write-value-as-string {:hello 1}))