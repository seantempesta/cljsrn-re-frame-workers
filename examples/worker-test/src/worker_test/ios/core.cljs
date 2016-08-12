(ns worker-test.ios.core
  (:require [reagent.core :as r :refer [atom]]
            [worker-test.handlers]
            [worker-test.subs]
            [cljsrn-re-frame-workers.worker-api :refer [subscribe dispatch dispatch-sync init-worker]]))


(def ReactNative (js/require "react-native"))

(def app-registry (.-AppRegistry ReactNative))
(def text (r/adapt-react-class (.-Text ReactNative)))
(def view (r/adapt-react-class (.-View ReactNative)))
(def image (r/adapt-react-class (.-Image ReactNative)))
(def touchable-highlight (r/adapt-react-class (.-TouchableHighlight ReactNative)))
(def Easing (.-Easing ReactNative))
(def Animated (.-Animated ReactNative))
(def AnimatedValue (.-Value Animated))
(def animated-image (r/adapt-react-class (.-Image Animated)))

(def logo-img (js/require "./images/cljs.png"))

(defn alert [title]
  (.alert (.-Alert ReactNative) title))

;; never ending spin!
(defn spin [spin-value]
  (.setValue spin-value 0)
  (let [timing (.timing Animated spin-value #js {:toValue  1
                                                 :duration 500
                                                 :easing   (.-linear Easing)})]
    (.start timing #(spin spin-value))))

(defn greeting-component []
  (let [greeting (subscribe [:get-greeting])
        primes (subscribe [:get-primes])
        spin-value (AnimatedValue. 0)]
    (r/create-class                                         ;; <-- expects a map of functions
      {:component-did-mount #(spin spin-value)
       :reagent-render      (fn []
                              (let [spin (.interpolate spin-value (clj->js {:inputRange  [0 1]
                                                                            :outputRange ["0deg" "360deg"]}))]
                                [view {:style {:flex-direction "column" :margin 40 :align-items "center"}}
                                 [text {:style {:font-size 30 :font-weight "100" :margin-bottom 20 :text-align "center"}} @greeting]
                                 [animated-image {:source logo-img
                                                  :style  {:width     80 :height 80 :margin-bottom 30
                                                           :transform [{:rotate spin}]}}]
                                 [view {:style {:flex 1 :flexDirection "row"}}
                                  [touchable-highlight {:style    {:background-color "#999" :margin 10 :padding 10 :border-radius 5}
                                                        :on-press #(dispatch [:generate-primes])}
                                   [text {:style {:color "white" :text-align "center" :font-weight "bold"}} "Generate Primes!"]]
                                  [touchable-highlight {:style    {:background-color "#999" :margin 10 :padding 10 :border-radius 5}
                                                        :on-press #(dispatch [:clear-primes])}
                                   [text {:style {:color "white" :text-align "center" :font-weight "bold"}} "Clear"]]]

                                 (when @primes
                                   [text {:style {}} @primes])]))})))

(defonce worker-ready? (r/atom false))

(defn app-root []
  (fn []
    (if @worker-ready?
      [greeting-component]
      [view {:flex           1
             :alignItems     "center"
             :justifyContent "center"}
       [text {} "Waiting for worker"]])))

(defn init-development []
  (.log js/console "DEVELOPMENT MODE -- No Worker")
  (reset! worker-ready? true)
  (dispatch-sync [:initialize-db]))

(defn init-production []
  (.log js/console "PRODUCTION MODE -- Starting Worker")
  (init-worker "worker.js" #(reset! worker-ready? true)))

(defn init []
  (if goog.DEBUG
    (init-development)
    (init-production))
  (.registerComponent app-registry "WorkerTest" #(r/reactify-component app-root)))
