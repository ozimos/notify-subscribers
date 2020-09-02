(ns erl.broadcast.model.gift-list-test
  (:require [erl.broadcast.db :as db]
            [erl.broadcast.rmq :as rmq]
            [erl.broadcast.test-helper :as test-helper :refer [system]]
            [clojure.test :refer [use-fixtures deftest is]]
            )
  )

(use-fixtures :once (test-helper/use-system ::rmq/rabbit ::db/pool))


(defn example-gift-list
  ([]
   (example-gift-list {}))
  ([m]
   (merge #::gift-list{:id #uuid "5ac47a01-ce18-488e-b86e-9a70f3e3ca47"
                       :name "Test gift list"}
     m)))


(deftest test-create-gift-list
  
  
  (is (= {:count 1}
         (db/execute-one! pool {:select [(sql/call :count :*)]
                                :from [:gift_list]}))
      "There is one gift list in the database after insert")
  (is (= (assoc (select-keys gift-list [::gift-list/id ::gift-list/name])
                ::gift-list/created-by-id (::user/id user))
         (db/execute-one! pool {:select [:id :name :created_by_id]
                                :from [:gift_list]}))
      "The gift list in the database's id, name, and created by matches what was inserted"))

