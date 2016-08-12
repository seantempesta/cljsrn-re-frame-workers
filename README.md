# ClojureScript Re-Frame Workers

A library integrating [react-native-workers](https://github.com/devfd/react-native-workers) and [re-frame](https://github.com/Day8/re-frame) for ClojureScript React Native magic.

![](https://clojars.org/cljsrn-re-frame-workers/latest-version.svg)

##### [Warning: Alpha quality]

## Features:
- Offload all computations except for rendering and get rid of jank!  There are too many non-native parts about `react-native`!
- Swap in API to move all `re-frame` computations (handlers/subscribers) to a worker thread
- Go ahead and throw more work in there!  I've also offloaded my `Firebase` and `Datascript` queries.
- In DEBUG mode use `re-frame` normally, and enable the worker during `prod-build`
- In theory it works with both iOS and Android (I've only tested it on iOS)

## Usage

##### Create a new project
- `re-natal init WorkerTest`
- `cd worker-test`

##### Install react-native-workers
- `npm install react-native-workers --save`
- `rnpm link react-native-workers`

##### Tell re-natal about the dependency
- `re-natal use-component react-native-workers`
- `re-natal use-figwheel`

##### Add a `worker.cljs` file to your project
```clojure
(ns worker-test.worker
  (:require [cljsrn-re-frame-workers.worker :as re-frame-worker]
            [reagent.core :as r :refer [atom]]
            [re-frame.core :refer [subscribe dispatch dispatch-sync]]
            [re-frame.db :refer [app-db]]
            [worker-test.handlers]
            [worker-test.subs]))

(dispatch-sync [:initialize-db])
(re-frame-worker/init-worker)
```
[NOTE: Any code or dependencies referenced in this file will run in a worker!]

##### Replace all references to re-frame's `subscribe`, `dispatch` and `dispatch-sync` with the worker's api:
```clojure
(ns worker-test.ios.core
  (:require [reagent.core :as r :refer [atom]]
            [worker-test.handlers]
            [worker-test.subs]
            [cljsrn-re-frame-workers.worker-api :refer [subscribe dispatch dispatch-sync init-worker]]))
```

##### On startup, initialize the worker
```clojure
(init-worker "worker.js" #(reset! worker-ready? true)))
```

##### Modify your `project.clj` file to build a worker
```clojure
:worker  {:source-paths ["src"]
          :compiler     {:output-to     "worker.js"
                         :main          "worker-test.worker"
                         :output-dir    "target/worker"
                         :optimizations :simple
                         :closure-defines {"goog.DEBUG" false}}}
```

##### Then build it
`lein with-profile prod cljsbuild once worker`

##### And when you're ready to deploy the app, package it with react native's packager.
`/usr/local/bin/node "./node_modules/react-native/local-cli/cli.js" bundle  --entry-file ./worker.js --platform ios --dev false --bundle-output "ios/worker.jsbundle"
`

## What's going on under the hood? Let's follow a request!

A todo-list app (of course!) mounts with a subscription for all todos:
```clojure
(defn todo-list []
  (let [todos (subscribe [:all-todos])]
    (fn []
      [render-todos @todos])))
```
The worker api detects if the worker is running and forwards it either directly to re-frame (in development) or to an intermediary layer.
```clojure
(defn subscribe [sub-v]
  (if @use-worker?
    (worker-utils/subscribe sub-v)
    (re-frame.core/subscribe sub-v)))
```
The intermediary layer checks to see if it's seen the subscription vector before:
- If yes, immediately return an ratom containing the most recent results
- If not, forward the request to the worker (using `cljs-transit`), and create a new ratom to hold the future results and return it.

```clojure
(defn subscribe
  "Accept the re-frame subscription, see if it's stored in subscriptions (if so return it),
   otherwise create an ratom with metadata {:loading true} and forward the subscription to the worker.
  Later when reaction results come in, we can just update the ratom."
  [sub-v]
  (if-let [existing-ratom (get @subscriptions sub-v)]
    existing-ratom
    (let [new-ratom (r/atom nil :meta {:loading true})
          transit-m (t/write tw [:subscribe sub-v])]
      (swap! subscriptions assoc sub-v new-ratom)
      (.postMessage @worker transit-m)
      new-ratom)))
```

NOTE: (`:meta {:loading true}` will be designated so you know the state of the subscription)

The worker receives the subscription request, subscribes with the subscription vector with re-frame and runs `reagent.core/track!` to forward the immediate (and all future) results back to the main thread.

```clojure
(defn receive-subscription
  "When the main thread issues a subscribe, subscribe in the worker,
  store it in the subscriptions atom, and deref it in a reagent track!
  statement so all future results get forwarded back to the main thread."
  [sub-v]
  (when-not (contains? @subscriptions sub-v)
    (let [new-sub (subscribe sub-v)]
      (swap! subscriptions assoc sub-v new-sub)
      (r/track! send-reaction-results sub-v new-sub))))
```

The main thread receives the updated results and swaps them in.
```clojure
(defn receive-reaction-results
  "Lookup the ratom, reset the metadata so we know the data has arrived and
  put the new results in.  This will trigger updates in any reagent components
  that subscribed.  Wrap the update in an InteractionManager call to not interrupt animations."
  [{:keys [sub-v data]}]
  (let [existing-ratom (get @subscriptions sub-v)]
    (.runAfterInteractions InteractionManager #(do (reset-meta! existing-ratom {:loading false})
                                                   (reset! existing-ratom data)))))
```

Dispatches are even easier as we just need to forward them to the worker.

##### Check out the example project for more details or read the code!  I think it's only 150 lines of code (with comments!)

## What's wrong with it?
* Since subscriptions immediately return an ratom with nil, you'll have to code defensively or check for metadata before rendering:
```clojure
  (let [todos (or @sub-result [])]
```
```clojure
  (if (:loading (meta sub-result))
    [spinner-component]
    [main-view @sub-result])
```
* Debugging problems with the worker is rough.  As of right now you can't use Chrome.  I'm trying to figure out why.
* Right now subscriptions immediately trigger computation on the worker even if you don't deref it in the component.  Then again, subscriptions shouldn't be launching missiles so this might not be too bad.
* Double the memory for every subscription.  Results are being cached in memory on the main app and the worker.
* Subscriptions do not free up their memory even after the component unmounts.  (I plan to fix this soon)
* Many many other things I'm sure.  But then again, I swapped it into my app and nothing broke.  How many projects can you move half of your code to a worker thread and not have anything break?!


## License

Copyright © 2016 Sean Tempesta

MIT License

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.