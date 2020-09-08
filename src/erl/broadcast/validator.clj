(ns erl.broadcast.validator
  (:require
   [clojure.spec.alpha :as s]))

(s/def ::msisdn-short #(re-matches #"^\d{10}$" %))
(s/def ::msisdn-long #(re-matches #"^234\d{10}$" %))
(s/def ::msisdn-full #(re-matches #"^\+234\d{10}$" %))
(s/def ::msisdn (s/or :short ::msisdn-short :long ::msisdn-long :full ::msisdn-full))
(defn format-msisdn [msisdn]
  (let [str-msisdn (str msisdn)
        res (s/conform ::msisdn str-msisdn)
        [k v] (if (= ::s/invalid res)
                (throw (ex-info "Invalid msisdn" (s/explain-data ::msisdn str-msisdn)))
                res)]
    (condp = k
      :full v
      :short (str "+234" v)
      :long (str "+" v)
      nil)))

(s/def ::amount #{"25" "50" "100" "200" "300" "500" "1000" "2000" "3000" "5000"})
(defn format-amount [config amount]
  (let [str-amount (str amount)
        naira
        (if (:is-kobo config)
          (subs str-amount 0 (- (.length str-amount) 2))
          str-amount)]
    (if (s/valid? ::amount naira)
      naira
      (throw (ex-info "Invalid naira" (s/explain-data ::naira str-amount))))))

(comment
  (s/valid? ::msisdn "+2348133248846")
  (s/conform ::msisdn "+234813328846")
  (s/valid? ::amount "5030")
  (format-msisdn "7051215974")
  (format-msisdn  7051215974)
  (format-msisdn "705121574"))