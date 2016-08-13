(ns cljsrn-re-frame-workers.worker-utils
  (:require [reagent.core :as r :refer [atom]]
            [reagent.ratom :as ratom]
            [re-frame.core]
            [cognitect.transit :as t]))


;; react requires
(defonce ReactNative (js/require "react-native"))
(defonce Worker (.-Worker (js/require "react-native-workers")))

;; Worker state
(defonce worker (atom nil))                                 ;; worker ref stored here
(defonce subscriptions (atom {}))                           ;; store all subscriptions here

;; transit readers and writers
(defonce tr (t/reader :json))                               ;; transit writer for converting json data to clj
(defonce tw (t/writer :json))                               ;; transit writer for converting clj data to json

;; Debugging
(defonce trace false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;  Main -> Worker Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn subscribe
  "Accept the re-frame subscription, see if it's stored in subscriptions (if so return it),
   otherwise create an ratom with metadata {:loading true} and forward the subscription to the worker.
  Later when reaction results come in, we can just update the ratom."
  [sub-v]
  (.log js/console "MAIN: Subscribe called:" (str sub-v))
  (if-let [existing-ratom (get @subscriptions sub-v)]
    (do (when trace (.log js/console "MAIN: Returning existing ratom."))
        existing-ratom)
    (let [new-ratom (r/atom nil :meta {:loading true})
          transit-m (t/write tw [:subscribe sub-v])]
      (swap! subscriptions assoc sub-v new-ratom)
      (when trace (.log js/console "MAIN: Forwarding subscription request to worker" (str transit-m)))
      (.postMessage @worker transit-m)
      new-ratom)))

(defn dispatch
  "Forward dispatch requests to the worker"
  [dispatch-v]
  (let [transit-m (t/write tw [:dispatch dispatch-v])]
    (.log js/console "MAIN: Dispatch called:" (str dispatch-v))
    (.postMessage @worker transit-m)))

(defn dispatch-sync
  "Forward dispatch-sync requests to the worker"
  [dispatch-v]
  (let [transit-m (t/write tw [:dispatch-sync dispatch-v])]
    (.log js/console "MAIN: Dispatch Sync called:" (str dispatch-v))
    (.postMessage @worker transit-m)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;  Worker -> Main Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn receive-reaction-results
  "Lookup the ratom, reset the metadata so we know the data has arrived and
  put the new results in.  This will trigger updates in any reagent components
  that subscribed."
  [{:keys [sub-v data]}]
  (.log js/console "MAIN: Subscription reaction received:" (str sub-v))
  (let [existing-ratom (get @subscriptions sub-v)]
    (when trace (.log js/console "MAIN: Trace: Found existing ratom " (str @existing-ratom)))
    (reset-meta! existing-ratom {:loading false})
    (reset! existing-ratom data)
    (when trace (.log js/console "MAIN: Trace: Ratom is now! " (str @existing-ratom)))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;  Create worker and fn dispatch
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn on-message
  "Used for receiving messages from the worker (encoded with cljs-transit) and
   dispatching to message to the handler function.

  Expects a vector with operation and args.
  Ex. [:reaction-results [:hello-msg \"Hi!\"]]
  "
  [transit-message ready-fn]
  (let [message (t/read tr transit-message)
        operation (first message)
        args (first (rest message))]
    (.log js/console "MAIN: Received message from worker" (str message))
    (case operation
      :worker-ready (when ready-fn (ready-fn))
      :reaction-results (receive-reaction-results args))))

(defn init-worker
  "Starts up the worker and tell the main thread to listen for messages from it with the
  on-message dispatch function.

  [Optional: ready-fn that will be run when this worker has been initialized.]
  "
  ([worker-file] (init-worker worker-file nil))
  ([worker-file ready-fn]
   (.log js/console "MAIN: Starting worker:" (str worker-file))
   (let [worker-init (Worker. worker-file)]
     (aset worker-init "onmessage" #(on-message % ready-fn))
     (reset! worker worker-init))))
