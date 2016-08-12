(ns worker-test.worker
  (:require [cljsrn-re-frame-workers.worker :as re-frame-worker]
            [reagent.core :as r :refer [atom]]
            [re-frame.core :refer [subscribe dispatch dispatch-sync]]
            [re-frame.db :refer [app-db]]
            [worker-test.handlers]
            [worker-test.subs]))

(dispatch-sync [:initialize-db])
(re-frame-worker/init-worker)

(.log js/console "App-DB: worker" (str @app-db))

