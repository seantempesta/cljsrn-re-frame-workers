(defproject worker-test "0.1.0-SNAPSHOT"
            :description "FIXME: write description"
            :url "http://example.com/FIXME"
            :license {:name "Eclipse Public License"
                      :url  "http://www.eclipse.org/legal/epl-v10.html"}
            :dependencies [[org.clojure/clojure "1.9.0-alpha10"]
                           [org.clojure/clojurescript "1.9.89"]
                           [cljsrn-re-frame-workers "0.1.3"]
                           [reagent "0.6.0-rc" :exclusions [cljsjs/react cljsjs/react-dom cljsjs/react-dom-server]]
[re-frame "0.7.0"]]
            :plugins [[lein-cljsbuild "1.1.1"]
                      [lein-figwheel "0.5.0-6"]
                      [lein-shell "0.5.0"]]
            :repositories {"local" "file:maven_repository"}
            :clean-targets ["target/" "index.ios.js" "index.android.js"]
            :aliases {"prod-build" ^{:doc "Recompile code with prod profile."}
                                   ["do" "clean"
                                    ["with-profile" "prod" "cljsbuild" "once" "ios"]
                                    ["with-profile" "prod" "cljsbuild" "once" "android"]
                                    ["with-profile" "prod" "cljsbuild" "once" "worker"]
                                    ["shell" "./build-worker-bundle.sh"]
                                    ]}
            :profiles {:dev {:dependencies [[figwheel-sidecar "0.5.0-6"]
                                            [com.cemerick/piggieback "0.2.1"]]
                             :source-paths ["src" "env/dev"]
                             :cljsbuild    {:builds {:ios     {:source-paths ["src" "env/dev"]
                                                               :figwheel     true
                                                               :compiler     {:output-to     "target/ios/not-used.js"
                                                                              :main          "env.ios.main"
                                                                              :output-dir    "target/ios"
                                                                              :optimizations :none}}
                                                     :android {:source-paths ["src" "env/dev"]
                                                               :figwheel     true
                                                               :compiler     {:output-to     "target/android/not-used.js"
                                                                              :main          "env.android.main"
                                                                              :output-dir    "target/android"
                                                                              :optimizations :none}}}}
                             :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}
                       :prod {:cljsbuild {:builds {:ios     {:source-paths ["src" "env/prod"]
                                                             :compiler     {:output-to     "index.ios.js"
                                                                            :main          "env.ios.main"
                                                                            :output-dir    "target/ios"
                                                                            :static-fns    true
                                                                            :optimize-constants true
                                                                            :optimizations :simple
                                                                            :closure-defines {"goog.DEBUG" false}}}
                                                   :android {:source-paths ["src" "env/prod"]
                                                             :compiler     {:output-to     "index.android.js"
                                                                            :main          "env.android.main"
                                                                            :output-dir    "target/android"
                                                                            :static-fns    true
                                                                            :optimize-constants true
                                                                            :optimizations :simple
                                                                            :closure-defines {"goog.DEBUG" false}}}
                                                   :worker  {:source-paths ["src"]
                                                             :compiler     {:output-to     "worker.js"
                                                                            :main          "worker-test.worker"
                                                                            :output-dir    "target/worker"
                                                                            :optimizations :simple
                                                                            :closure-defines {"goog.DEBUG" false}}}}}}})
