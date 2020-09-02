(ns erl.broadcast.targets
  (:require [erl.broadcast.config :as config]))

(defmulti get-query (fn [op] op))

(defmethod get-query :last72hours [_]
  ["select msisdn, amount from public.tbl_loan_request 
        where repay_time is null and status = 'success' and 
        timestamp between '2020-07-02 00:00:00.000000'::timestamp 
        and current_timestamp - interval '72 hour'"])

(defmethod get-query :inactive-subscribers [_]
  ["select * from public.tbl_glo_bmc_inactive_subs_aug2020
         where amount_loanable = ?" (or (System/getProperty "denom.band") 500)])

(defmulti get-message (fn [op _] op))

(defmethod get-message :last72hours [_ _]
  (format "Dear Customer, your extracredit loan is now more than 72hours. Kindly recharge your account to repay your loans."))

(defmethod get-message :inactive-subscribers [_ result]
  (format 
   "Congratulations!! You are now qualified for N%s on Borrow Me Credit. The more you borrow, the higher your qualification. Simply dial *321# today." 
   (:amount result)))

(defn config-query [config]
  (get-query (config/target-spec config)))

(defn config-message [config result]
  (get-message (config/target-spec config) result))