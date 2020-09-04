(ns erl.broadcast.targets
  (:require [erl.broadcast.config :as config]
            [jsonista.core :as j]))

(defmulti get-query (fn [op _] op))

(defmethod get-query :last72hours [_ _]
  ["select msisdn, amount from public.tbl_loan_request 
        where repay_time is null and status = 'success' and 
        timestamp between '2020-07-02 00:00:00.000000'::timestamp 
        and current_timestamp - interval '72 hour'"])

(defn to-sms [msisdn message]
  (j/write-value-as-string
   {:id -1, :msisdn msisdn, :message message, :flash? false, :from "Glo BoroMe"}))

(defmethod get-query :inactive-subscribers [_ table-name]
  [(str "select * from " table-name
        " where amount_loanable = ?") (or (Integer/parseInt (System/getProperty "denom.band")) 200)])

(defmulti get-message (fn [op _] op))

(defmethod get-message :last72hours [_ {:keys [msisdn]}]
  (let [message  (format "Dear Customer, your extracredit loan is now more than 72hours. Kindly recharge your account to repay your loans.")]
    (to-sms msisdn message)))

(defmethod get-message :inactive-subscribers [_ {:keys [amount msisdn]}]
  (let [message  (format
                  "Congratulations!! You are now qualified for N%s on Borrow Me Credit. The more you borrow, the higher your qualification. Simply dial *321# today."
                  amount)]
    (to-sms msisdn message)))

(defn config-query [config]
  (get-query (config/target-spec config) (config/table-spec config)))

(defn config-message [config result]
  (get-message (config/target-spec config) result))

(comment
  (j/write-value-as-string {:hello 1}))