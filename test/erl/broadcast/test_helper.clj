(ns erl.broadcast.test-helper
  (:require [integrant.core :as ig]
            [clojure.java.io :as io]))

(def system nil)

(defn use-system
  "Test fixture that initializes system components and sets it as the
  value of the `system` var, runs the test, then halts system
  components and resets `system` to nil. If no system components are
  passed in, initializes and halts the full system."
  [& component-keys]
  (fn [test-fn]
    (alter-var-root #'system
                    (fn [_]
                      (let [ig-config (merge
                                       (ig/read-string
                                        (slurp (io/resource "system.edn")))
                                       (ig/read-string
                                        (slurp (io/resource "resources/test.edn"))))]
                        (if (seq component-keys)
                          (ig/init ig-config component-keys)
                          (ig/init ig-config)))))
    (test-fn)
    (ig/halt! system)
    (alter-var-root #'system (constantly nil))))

