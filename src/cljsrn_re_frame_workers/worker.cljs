(ns cljsrn-re-frame-workers.worker
  (:require-macros [reagent.ratom :refer [run!]])
  (:require [cognitect.transit :as t]
            [re-frame.core :refer [subscribe dispatch dispatch-sync]]))

(defonce Self (.-self (js/require "react-native-workers"))) ;; "self" is the worker thread
(defonce subscriptions (atom {}))                           ;; store all subscriptions here
(defonce tr (t/reader :json))                               ;; transit writer for converting json data to clj
(defonce tw (t/writer :json))                               ;; transit writer for converting clj data to json

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Worker -> Main Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn send-reaction-results
  "Most communication with the main thread is forwarding reaction results.
  Wrap it up in a transit message and send it on!"
  [sub-v data]
  (let [operation :reaction-results
        args {:sub-v sub-v
              :data  data}
        message [operation args]
        transit-message (t/write tw message)]
    (.postMessage Self transit-message)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Main -> Worker Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn receive-subscription
  "When the main thread issues a subscribe, subscribe in the worker,
  store it in the subscriptions atom, and deref it in a reagent (run!)
  statement so all future results get forwarded back to the Main thread."
  [sub-v]
  (.log js/console "WORKER: received subscription vector" (str sub-v))
  (when-not (contains? @subscriptions sub-v)
    (let [new-sub (subscribe sub-v)]
      (swap! subscriptions assoc sub-v new-sub)
      (run! (send-reaction-results sub-v @new-sub)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;  Worker init and fn dispatch
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn on-message
  "Used for message dispatch with cljs-transit. Expects a vector with operation and args.
  Ex. [:subscribe [:hello]]"
  [transit-message]
  (let [message (t/read tr transit-message)
        operation (first message)
        args (first (rest message))]
    (.log js/console "WORKER: Message received:" (str operation))
    (case operation
      :subscribe (receive-subscription args)
      :dispatch (dispatch args)
      :dispatch-sync (dispatch-sync args))))

(defn init-worker
  "Turn on console logging, tell the thread to listen for messages using the on-message fn
  and send a message back to the main thread indicating when the worker is ready."
  []
  (enable-console-print!)                                   ;; enable console printing
  (aset Self "onmessage" on-message)                        ;; listen for messages using the on-message fn
  (let [ready-transit-m (t/write tw [:worker-ready])]
    (.postMessage Self ready-transit-m)                    ;; Send a message to the main thread indicating the worker is ready
    (.log js/console "WORKER: Ready")))