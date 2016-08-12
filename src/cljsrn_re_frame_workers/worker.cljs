(ns cljsrn-re-frame-workers.worker
  (:require [cognitect.transit :as t]
            [reagent.core :as r]
            [re-frame.core :refer [subscribe dispatch dispatch-sync]]
            [re-frame.db :refer [app-db]]))

(defonce Self (.-self (js/require "react-native-workers"))) ;; "self" is the worker thread
(defonce subscriptions (atom {}))                           ;; store all subscriptions here
(defonce tr (t/reader :json))                               ;; transit writer for converting json data to clj
(defonce tw (t/writer :json))                               ;; transit writer for converting clj data to json
(defonce trace false)                                       ;; enable for more logging

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Worker -> Main Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn send-reaction-results
  "Most communication with the main thread is forwarding reaction results.
  Wrap it up in a transit message and send it on!"
  [sub-v data-sub]
  (let [operation :reaction-results
        args {:sub-v sub-v
              :data  @data-sub}
        message [operation args]
        transit-message (t/write tw message)]
    (when trace (.log js/console "WORKER: Trace: Sending results to MAIN" (str transit-message)))
    (.postMessage Self transit-message)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Main -> Worker Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn receive-subscription
  "When the main thread issues a subscribe, subscribe in the worker,
  store it in the subscriptions atom, and deref it in a reagent track!
  statement so all future results get forwarded back to the Main thread."
  [sub-v]
  (.log js/console "WORKER: received subscription vector" (str sub-v))
  (when-not (contains? @subscriptions sub-v)
    (let [new-sub (subscribe sub-v)]
      (when trace (.log js/console "WORKER: Trace: Reusults = " @new-sub))
      (swap! subscriptions assoc sub-v new-sub)
      (r/track! send-reaction-results sub-v new-sub))))

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
    (.log js/console "WORKER: Message received:" (str operation args))
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