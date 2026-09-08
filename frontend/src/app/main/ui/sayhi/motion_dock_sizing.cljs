;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns app.main.ui.sayhi.motion-dock-sizing
  "Optional layout-only bridge. The frozen Motion editing protocol is unchanged.")

(defn content-height
  "Decode only the bounded layout envelope; no document or editing authority."
  [value]
  (try
    (let [encoded (js/JSON.stringify value)]
      (when (and (string? encoded) (<= (count encoded) 512))
        (let [message (js->clj (js/JSON.parse encoded) :keywordize-keys true)
              height  (get-in message [:payload :height])]
          (when (and (= #{:schema :schemaVersion :type :payload} (set (keys message)))
                     (= "io.sayhi.studio.motion-layout" (:schema message))
                     (= "1.0" (:schemaVersion message))
                     (= "studio.bounds" (:type message))
                     (= #{:height} (set (keys (:payload message))))
                     (js/Number.isSafeInteger height)
                     (<= 64 height 4096))
            height))))
    (catch :default _ nil)))

(defn collapsed-state
  "Optional presentation signal; old bounds-only senders remain supported."
  [value]
  (try
    (let [encoded (js/JSON.stringify value)]
      (when (and (string? encoded) (<= (count encoded) 512))
        (let [message   (js->clj (js/JSON.parse encoded) :keywordize-keys true)
              collapsed (get-in message [:payload :collapsed])]
          (when (and (= #{:schema :schemaVersion :type :payload} (set (keys message)))
                     (= "io.sayhi.studio.motion-layout" (:schema message))
                     (= "1.0" (:schemaVersion message))
                     (= "studio.presentation" (:type message))
                     (= #{:collapsed} (set (keys (:payload message))))
                     (boolean? collapsed))
            collapsed))))
    (catch :default _ nil)))

(defn fit-height
  ([desired maximum]
   (fit-height desired maximum 144))
  ([desired maximum minimum]
   (js/Math.round (max 1 (min maximum (max minimum desired))))))

(defn install!
  "Own only the dock/handle passed by Motion Studio. Return lifecycle cleanup."
  [dock frame handle origin]
  (let [content*   (atom nil)
        manual*    (atom nil)
        collapsed* (atom false)
        drag*      (atom nil)
        maximum    (fn []
                     (let [bottom (js/parseFloat (.-bottom (js/getComputedStyle dock)))]
                       (max 1 (- (.-innerHeight js/window) (if (js/isNaN bottom) 16 bottom) 48))))
        chrome     (fn []
                     (let [header (.querySelector dock "header")]
                       (+ 2 (if header (.-height (.getBoundingClientRect header)) 0))))
        refresh!   (fn []
                     (when @content*
                       (.setAttribute dock "data-content-collapsed" (str @collapsed*))
                       (let [limit  (maximum)
                             height (if @collapsed*
                                      (fit-height (+ @content* 2) limit 64)
                                      (fit-height (or @manual* (min 420 (+ @content* (chrome)))) limit))
                             mode   (cond @collapsed* "collapsed" @manual* "manual" :else "auto")]
                         (.setProperty (.-style dock) "--sayhi-motion-dock-height" (str height "px"))
                         (.setAttribute dock "data-height-mode" mode)
                         (.setAttribute handle "tabindex" (if @collapsed* "-1" "0"))
                         (.setAttribute handle "aria-disabled" (str @collapsed*))
                         (.setAttribute handle "aria-valuemin" (str (min (if @collapsed* 64 144) limit)))
                         (.setAttribute handle "aria-valuemax" (str (js/Math.round limit)))
                         (.setAttribute handle "aria-valuenow" (str height))
                         (.setAttribute handle "aria-valuetext" (str height " pixels, " (case mode "collapsed" "collapsed" "manual" "custom height" "fit to tracks"))))))
        reset!     (fn [_]
                     (when-not @collapsed*
                       (cljs.core/reset! manual* nil)
                       (refresh!)))
        receive!   (fn [event]
                     (when (and (= origin (.-origin event))
                                (identical? (.-source event) (.-contentWindow frame)))
                       (when-let [height (content-height (.-data event))]
                         (cljs.core/reset! content* height)
                         (refresh!))
                       (when-some [collapsed (collapsed-state (.-data event))]
                         (when (and collapsed @drag*)
                           (cljs.core/reset! manual* (:prior @drag*))
                           (cljs.core/reset! drag* nil))
                         (cljs.core/reset! collapsed* collapsed)
                         (refresh!))))
        down!      (fn [event]
                     (when (and @content* (not @collapsed*) (zero? (.-button event)))
                       (.preventDefault event)
                       (cljs.core/reset! drag* {:id (.-pointerId event)
                                                :y (.-clientY event)
                                                :height (.-height (.getBoundingClientRect dock))
                                                :prior @manual*})
                       (.setPointerCapture handle (.-pointerId event))))
        move!      (fn [event]
                     (when-let [{:keys [id y height]} @drag*]
                       (when (= id (.-pointerId event))
                         (cljs.core/reset! manual* (fit-height (+ height (- y (.-clientY event))) (maximum)))
                         (refresh!))))
        end!       (fn [event]
                     (when (= (:id @drag*) (.-pointerId event))
                       (cljs.core/reset! drag* nil)))
        cancel!    (fn [_]
                     (when @drag*
                       (cljs.core/reset! manual* (:prior @drag*))
                       (cljs.core/reset! drag* nil)
                       (refresh!)))
        key!       (fn [event]
                     (let [key (.-key event)]
                       (when (and @content* (not @collapsed*) (contains? #{"ArrowUp" "ArrowDown" "Home" "End" "Enter" "Escape"} key))
                         (.preventDefault event)
                         (.stopPropagation event)
                         (case key
                           ("Home" "Enter") (reset! nil)
                           "Escape" (cancel! nil)
                           (do
                             (cljs.core/reset! manual*
                                               (if (= key "End")
                                                 (maximum)
                                                 (fit-height (+ (.-height (.getBoundingClientRect dock))
                                                                (if (= key "ArrowUp") 24 -24))
                                                             (maximum))))
                             (refresh!))))))
        observer   (js/ResizeObserver. refresh!)]
    (.addEventListener js/window "message" receive!)
    (.addEventListener js/window "resize" refresh!)
    (.addEventListener handle "pointerdown" down!)
    (.addEventListener handle "pointermove" move!)
    (.addEventListener handle "pointerup" end!)
    (.addEventListener handle "pointercancel" cancel!)
    (.addEventListener handle "lostpointercapture" cancel!)
    (.addEventListener handle "keydown" key!)
    (.addEventListener handle "dblclick" reset!)
    (.observe observer dock)
    (fn []
      (.disconnect observer)
      (.removeEventListener js/window "message" receive!)
      (.removeEventListener js/window "resize" refresh!)
      (.removeEventListener handle "pointerdown" down!)
      (.removeEventListener handle "pointermove" move!)
      (.removeEventListener handle "pointerup" end!)
      (.removeEventListener handle "pointercancel" cancel!)
      (.removeEventListener handle "lostpointercapture" cancel!)
      (.removeEventListener handle "keydown" key!)
      (.removeEventListener handle "dblclick" reset!)
      (.removeAttribute dock "data-content-collapsed")
      (.removeAttribute dock "data-height-mode")
      (.removeAttribute handle "aria-disabled")
      (.setAttribute handle "tabindex" "0")
      (.removeProperty (.-style dock) "--sayhi-motion-dock-height"))))
