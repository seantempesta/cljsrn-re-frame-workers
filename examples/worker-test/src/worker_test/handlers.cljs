(ns worker-test.handlers
  (:require
    [re-frame.core :refer [register-handler after]]
    [cljs.spec :as s]
    [clojure.string :as string]
    [worker-test.db :as db :refer [app-db]]))

;; -- Middleware ------------------------------------------------------------
;;
;; See https://github.com/Day8/re-frame/wiki/Using-Handler-Middleware
;;
(defn check-and-throw
  "Throw an exception if db doesn't have a valid spec."
  [spec db]
  (when-not (s/valid? spec db)
    (let [explain-data (s/explain-data spec db)]
      (throw (ex-info (str "Spec check failed: " explain-data) explain-data)))))

(def validate-spec-mw
  (if goog.DEBUG
    (after (partial check-and-throw ::db/app-db))
    []))

(defn prime-algorithm [start end]
  (let [prime? (fn [number]
                 (cond
                   (= number 1) false
                   (= number 2) true
                   :default (not-any? zero? (map #(rem number %)
                                                 (range 2 number)))))
        prime-numbers (keep #(when (prime? %) %) (range start end))]
    (string/join ", " prime-numbers)))


;; -- Handlers --------------------------------------------------------------

(register-handler
  :initialize-db
  validate-spec-mw
  (fn [_ _]
    app-db))

(register-handler
  :set-greeting
  validate-spec-mw
  (fn [db [_ value]]
    (assoc db :greeting value)))

(register-handler
  :generate-primes
  validate-spec-mw
  (fn [db _]
    (let [primes (prime-algorithm 0 5000)]
    (assoc db :primes primes))))

(register-handler
  :clear-primes
  validate-spec-mw
  (fn [db _]
    (dissoc db :primes)))