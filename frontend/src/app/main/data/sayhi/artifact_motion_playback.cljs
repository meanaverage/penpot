;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns app.main.data.sayhi.artifact-motion-playback
  "Controllable native preview runtime for a compiled portable-artifact motion
  plan. CSS and WAAPI semantics are controlled through WAAPI while scrubbing;
  materialized export remains a separate concern."
  (:require
   [goog.object :as gobj]))

(def ^:private transform-fields
  #{:x :y :xPercent :yPercent :scale :scaleX :scaleY :rotation})
(def ^:private highlight-class "sayhi-motion-anatomy-highlight")
(def ^:private highlight-style-id "sayhi-motion-anatomy-highlight-style")

(defn- finite
  [value fallback]
  (if (and (number? value) (js/Number.isFinite value)) value fallback))

(defn- motion-transform
  [values]
  (let [x         (finite (:x values) 0)
        y         (finite (:y values) 0)
        x-percent (finite (:xPercent values) 0)
        y-percent (finite (:yPercent values) 0)
        rotation  (finite (:rotation values) 0)
        scale     (finite (:scale values) 1)
        scale-x   (finite (:scaleX values) scale)
        scale-y   (finite (:scaleY values) scale)]
    (str "translate(" x-percent "%, " y-percent "%) "
         "translate3d(" x "px, " y "px, 0) "
         "rotate(" rotation "deg) "
         "scale(" scale-x ", " scale-y ")")))

(defn- motion-keyframe
  [values]
  (let [frame (js-obj)]
    (when (some transform-fields (keys values))
      (gobj/set frame "transform" (motion-transform values)))
    (when (contains? values :opacity)
      (gobj/set frame "opacity" (:opacity values)))
    (when (contains? values :autoAlpha)
      (gobj/set frame "opacity" (:autoAlpha values))
      (gobj/set frame
                "visibility"
                (if (zero? (:autoAlpha values)) "hidden" "visible")))
    frame))

(defn- query-targets
  [document selectors]
  (let [seen (js/Set.)]
    (reduce
     (fn [targets selector]
       (reduce
        (fn [targets target]
          (if (.has seen target)
            targets
            (do
              (.add seen target)
              (conj targets target))))
        targets
        (array-seq (.querySelectorAll document selector))))
     []
     selectors)))

(defn- wrap-index
  [value size]
  (if (pos? size) (mod value size) 0))

(defn- frame-center
  [frame axis size-axis]
  (+ (finite (get frame axis) 0)
     (/ (finite (get frame size-axis) 0) 2)))

(defn- shortest-angle
  "Normalize equivalent Penpot angles onto the signed shortest arc. Penpot may
  store an authored -2.4deg rotation as 357.6deg; interpolating that raw value
  would make a card perform an almost complete revolution."
  [value]
  (- (mod (+ (finite value 0) 180) 360) 180))

(defn- relative-slot-keyframe
  "Express one authored cycle slot relative to an item's original Penpot slot.
  Individual transform properties compose with the materialized Penpot
  transform instead of replacing it. Keeping translate, rotate, and scale
  independent also prevents browser matrix decomposition from making cards
  spin while they cross the center slot."
  [source target]
  (let [source-width  (max 0.001 (finite (:width source) 1))
        source-height (max 0.001 (finite (:height source) 1))
        frame         (js-obj)
        x             (- (frame-center target :x :width)
                         (frame-center source :x :width))
        y             (- (frame-center target :y :height)
                         (frame-center source :y :height))
        rotation      (shortest-angle
                       (- (finite (:rotation target) 0)
                          (finite (:rotation source) 0)))
        scale-x       (/ (finite (:width target) source-width) source-width)
        scale-y       (/ (finite (:height target) source-height) source-height)]
    (gobj/set frame "translate" (str x "px " y "px"))
    (gobj/set frame "rotate" (str rotation "deg"))
    (gobj/set frame "scale" (str scale-x " " scale-y))
    (gobj/set frame "transformOrigin" "center center")
    (gobj/set frame "opacity" (finite (:opacity target) 1))
    (gobj/set frame "zIndex" (str (int (finite (:zIndex target) 1))))
    frame))

(defn- cycle-item-order
  [slot center-slot delta size]
  (if (neg? delta)
    (wrap-index (- slot center-slot) size)
    (wrap-index (- center-slot slot) size)))

(defn- slot-appearance-keyframe
  [appearance active?]
  (let [values (if active? (:active appearance) (:inactive appearance))
        frame  (js-obj)]
    (when (contains? values :opacity)
      (gobj/set frame "opacity" (finite (:opacity values) 0)))
    frame))

(defn- create-cycle-track
  [document step cycle-offsets]
  (let [operation    (:operation step)
        group-id     (:groupId operation)
        slots        (:slots operation)
        slot-count   (count slots)
        offset       (wrap-index (get cycle-offsets group-id 0) slot-count)
        delta        (:delta operation)
        center-slot  (:centerSlot operation)
        animations   (->> (:items operation)
                          (mapcat
                           (fn [{:keys [selector initialSlot appearanceTargets]}]
                             (when-let [target (first (query-targets document [selector]))]
                               (let [current-slot (wrap-index (+ initialSlot offset) slot-count)
                                     next-slot    (wrap-index (+ current-slot delta) slot-count)
                                     order        (cycle-item-order current-slot
                                                                    center-slot
                                                                    delta
                                                                    slot-count)
                                     from         (relative-slot-keyframe
                                                   (get slots initialSlot)
                                                   (get slots current-slot))
                                     to           (relative-slot-keyframe
                                                   (get slots initialSlot)
                                                   (get slots next-slot))
                                     options      #js {:duration (* (:duration step) 1000)
                                                       :delay (* (+ (:at step)
                                                                    (* order (:stagger step)))
                                                                 1000)
                                                       :easing (:ease step)
                                                       ;; A repeated trigger rebuilds these animations from
                                                       ;; the committed semantic slot state. Backwards fill
                                                       ;; keeps that state visible during delay/stagger instead
                                                       ;; of exposing the authored DOM position between runs.
                                                       :fill "both"
                                                       :iterations 1}
                                     animation    (.animate target #js [from to] options)
                                     appearance-animations
                                     (keep
                                      (fn [appearance]
                                        (when-let [appearance-target
                                                   (first
                                                    (query-targets
                                                     document
                                                     [(:selector appearance)]))]
                                          (let [appearance-animation
                                                (.animate
                                                 appearance-target
                                                 #js [(slot-appearance-keyframe
                                                       appearance
                                                       (= current-slot center-slot))
                                                      (slot-appearance-keyframe
                                                       appearance
                                                       (= next-slot center-slot))]
                                                 options)]
                                            (.pause appearance-animation)
                                            (set! (.-currentTime appearance-animation) 0)
                                            appearance-animation)))
                                      appearanceTargets)]
                                 (.pause animation)
                                 (set! (.-currentTime animation) 0)
                                 (into [animation] appearance-animations)))))
                          vec)
        duration     (+ (:at step)
                        (:duration step)
                        (* (max 0 (dec slot-count)) (:stagger step)))]
    {:id (:id step)
     :driver (:driver step)
     :requestedDriver (:requestedDriver step)
     :previewAdapter (:previewAdapter step)
     :translationMode (:translationMode step)
     :operation operation
     :duration duration
     :animations animations}))

(defn- indicator-slot-keyframe
  [source target]
  (let [frame (js-obj)
        x     (- (frame-center target :x :width)
                 (frame-center source :x :width))
        y     (- (frame-center target :y :height)
                 (frame-center source :y :height))]
    (gobj/set frame "translate" (str x "px " y "px"))
    (gobj/set frame "transformOrigin" "center center")
    frame))

(defn- create-cycle-indicator-track
  [document step cycle-offsets]
  (let [operation     (:operation step)
        group-id      (:groupId operation)
        slots         (:slots operation)
        slot-count    (count slots)
        offset        (wrap-index (get cycle-offsets group-id 0) slot-count)
        delta         (:delta operation)
        initial-slot  (:initialSlot operation)
        current-slot  (wrap-index (- initial-slot offset) slot-count)
        next-slot     (wrap-index (- initial-slot (+ offset delta)) slot-count)
        source-frame  (:sourceFrame operation)
        target        (first (query-targets document [(:selector operation)]))
        options       #js {:duration (* (:duration step) 1000)
                           :delay (* (:at step) 1000)
                           :easing (:ease step)
                           ;; Keep the committed indicator slot visible during
                           ;; the response delay. Without backwards fill, a
                           ;; repeated trigger exposes the authored center slot
                           ;; before jumping back to its logical start position.
                           :fill "both"
                           :iterations 1}
        animation     (when target
                        (.animate
                         target
                         #js [(indicator-slot-keyframe source-frame
                                                       (get slots current-slot))
                              (indicator-slot-keyframe source-frame
                                                       (get slots next-slot))]
                         options))]
    (when animation
      (.pause animation)
      (set! (.-currentTime animation) 0))
    {:id (:id step)
     :driver (:driver step)
     :requestedDriver (:requestedDriver step)
     :previewAdapter (:previewAdapter step)
     :translationMode (:translationMode step)
     :operation operation
     :duration (+ (:at step) (:duration step))
     :animations (cond-> [] animation (conj animation))}))

(defn- create-track
  [document step cycle-offsets]
  (case (get-in step [:operation :type])
    "cycle"
    (create-cycle-track document step cycle-offsets)

    "cycle-indicator"
    (create-cycle-indicator-track document step cycle-offsets)

    (let [targets    (query-targets document (:selectors step))
          from       (motion-keyframe (:from step))
          to         (motion-keyframe (:to step))
          animations (mapv
                      (fn [target index]
                        (let [options   #js {:duration (* (:duration step) 1000)
                                             :delay (* (+ (:at step)
                                                          (* index (:stagger step)))
                                                       1000)
                                             :easing (:ease step)
                                             :fill "forwards"
                                             :iterations 1}
                              animation (.animate target #js [from to] options)]
                          (.pause animation)
                          (set! (.-currentTime animation) 0)
                          animation))
                      targets
                      (range))
          duration   (+ (:at step)
                        (:duration step)
                        (* (max 0 (dec (count targets))) (:stagger step)))]
      {:id (:id step)
       :driver (:driver step)
       :requestedDriver (:requestedDriver step)
       :previewAdapter (:previewAdapter step)
       :translationMode (:translationMode step)
       :duration duration
       :animations animations})))

(defn- dispose-tracks!
  [tracks]
  (doseq [animation (mapcat :animations tracks)]
    (.cancel animation)))

(defn- remove-highlight!
  [target]
  (when-let [class-list (.-classList target)]
    (.remove class-list highlight-class)))

(defn- add-highlight!
  [target]
  (when-let [class-list (.-classList target)]
    (.add class-list highlight-class)))

(defn- ensure-highlight-style!
  [document style-element*]
  (when (and (nil? @style-element*)
             (.-head document)
             (.-createElement document))
    (let [style (.createElement document "style")]
      (set! (.-id style) highlight-style-id)
      (set! (.-textContent style)
            (str "." highlight-class " {"
                 "outline: 3px solid #6c5ce7 !important;"
                 "outline-offset: 3px !important;"
                 "filter: drop-shadow(0 0 8px rgba(108, 92, 231, .45)) !important;"
                 "}"))
      (.appendChild (.-head document) style)
      (reset! style-element* style))))

(defn- dispose-highlight-style!
  [style-element*]
  (when-let [style @style-element*]
    (if (.-remove style)
      (.remove style)
      (when-let [parent (.-parentNode style)]
        (.removeChild parent style)))
    (reset! style-element* nil)))

(defn create-runtime
  "Mount a compiled artifact motion plan against a same-origin, non-scripted
  artifact document. Returns a controller map with play, pause, reverse, seek,
  response selection, state inspection, and disposal functions."
  ([document plan]
   (create-runtime document plan {}))
  ([document plan {:keys [on-state scope]
                   :or {on-state (fn [_])
                        scope js/globalThis}}]
   (let [responses       (:responses plan)
         response-id*    (atom (:id (first responses)))
         tracks*         (atom [])
         duration*       (atom 0)
         time*           (atom 0)
         cycle-offsets*  (atom {})
         commit-cycle?*  (atom false)
         direction*      (atom 1)
         status*         (atom "loading")
         frame*          (atom nil)
         previous-time*  (atom nil)
         highlighted-parts* (atom [])
         highlighted-targets* (atom [])
         highlight-style* (atom nil)
         disposed?*      (atom false)
         request-frame   (if-let [request (.-requestAnimationFrame scope)]
                           (.bind request scope)
                           (fn [callback]
                             (js/setTimeout #(callback (.now js/Date)) 16)))
         cancel-frame    (if-let [cancel (.-cancelAnimationFrame scope)]
                           (.bind cancel scope)
                           js/clearTimeout)]
     (letfn [(response []
               (some #(when (= @response-id* (:id %)) %) responses))

             (snapshot []
               {:implementation "artifact-motion-native-v2"
                :status @status*
                :responseId @response-id*
                :time @time*
                :duration @duration*
                :progress (if (pos? @duration*)
                            (/ @time* @duration*)
                            0)
                :direction @direction*
                :cycleState @cycle-offsets*
                :requestedDrivers (into [] (distinct (map :requestedDriver @tracks*)))
                :previewAdapters (into [] (distinct (map :previewAdapter @tracks*)))
                :translationModes (into [] (distinct (map :translationMode @tracks*)))
                :highlightedParts @highlighted-parts*})

             (publish! []
               (when-not @disposed?*
                 (on-state (snapshot))))

             (cancel-scheduled-frame! []
               (when-let [frame @frame*]
                 (cancel-frame frame))
               (reset! frame* nil)
               (reset! previous-time* nil))

             (render-time! [seconds]
               (let [value (min @duration* (max 0 (finite seconds 0)))]
                 (reset! time* value)
                 (doseq [animation (mapcat :animations @tracks*)]
                   (set! (.-currentTime animation) (* value 1000)))))

             (rebuild! []
               (cancel-scheduled-frame!)
               (dispose-tracks! @tracks*)
               (let [next-tracks (mapv #(create-track document % @cycle-offsets*)
                                       (:steps (response)))]
                 (reset! tracks* next-tracks)
                 (reset! duration* (reduce max 0 (map :duration next-tracks)))
                 (render-time! 0)
                 (reset! status* (if (seq next-tracks) "ready" "unavailable"))
                 (publish!)))

             (commit-cycle-state! []
               (when @commit-cycle?*
                 (let [operations (->> @tracks*
                                       (keep :operation)
                                       (filter #(= "cycle" (:type %)))
                                       (reduce (fn [result operation]
                                                 (assoc result (:groupId operation) operation))
                                               {}))]
                   (doseq [[group-id operation] operations]
                     (let [slot-count (count (:slots operation))]
                       (swap! cycle-offsets*
                              update
                              group-id
                              (fn [offset]
                                (wrap-index (+ (or offset 0) (:delta operation))
                                            slot-count))))))
                 (reset! commit-cycle?* false)))

             (tick! [timestamp]
               (reset! frame* nil)
               (let [previous (or @previous-time* timestamp)
                     delta    (min 0.1 (max 0 (/ (- timestamp previous) 1000)))]
                 (reset! previous-time* timestamp)
                 (render-time! (+ @time* (* delta @direction*)))
                 (cond
                   (and (pos? @direction*) (>= @time* @duration*))
                   (do
                     (commit-cycle-state!)
                     (reset! status* "finished")
                     (publish!))

                   (and (neg? @direction*) (<= @time* 0))
                   (do
                     (reset! status* "ready")
                     (publish!))

                   :else
                   (do
                     (publish!)
                     (reset! frame* (request-frame tick!))))))

             (start-play! [commit?]
               (when (seq @tracks*)
                 (cancel-scheduled-frame!)
                 (when (>= @time* @duration*) (render-time! 0))
                 (reset! commit-cycle?* commit?)
                 (reset! direction* 1)
                 (reset! status* "playing")
                 (reset! frame* (request-frame tick!))
                 (publish!)))

             (play! []
               (start-play! false))

             (pause! []
               (cancel-scheduled-frame!)
               (reset! status* "paused")
               (publish!))

             (reverse! []
               (when (seq @tracks*)
                 (cancel-scheduled-frame!)
                 (reset! commit-cycle?* false)
                 (when (<= @time* 0) (render-time! @duration*))
                 (reset! direction* -1)
                 (reset! status* "reversing")
                 (reset! frame* (request-frame tick!))
                 (publish!)))

             (seek! [progress]
               (cancel-scheduled-frame!)
               (reset! commit-cycle?* false)
               (let [bounded (min 1 (max 0 (finite progress 0)))]
                 (render-time! (* @duration* bounded))
                 (reset! status* (cond
                                   (>= bounded 1) "finished"
                                   (<= bounded 0) "ready"
                                   :else "paused"))
                 (publish!)))

             (restart! []
               (cancel-scheduled-frame!)
               (reset! commit-cycle?* false)
               (render-time! 0)
               (reset! direction* 1)
               (reset! status* "ready")
               (publish!))

             (highlight-parts! [part-ids]
               (doseq [target @highlighted-targets*]
                 (remove-highlight! target))
               (let [parts     (->> part-ids
                                    (filter string?)
                                    distinct
                                    vec)
                     selectors (->> parts
                                    (mapcat #(get-in plan [:parts %]))
                                    distinct
                                    vec)
                     targets   (query-targets document selectors)]
                 (when (seq targets)
                   (ensure-highlight-style! document highlight-style*))
                 (doseq [target targets]
                   (add-highlight! target))
                 (reset! highlighted-parts* parts)
                 (reset! highlighted-targets* targets)
                 (publish!)
                 true))

             (select-response! [response-id]
               (when (some #(= response-id (:id %)) responses)
                 (reset! commit-cycle?* false)
                 (reset! response-id* response-id)
                 (rebuild!)
                 true))

             (trigger! [event]
               (let [detail (:detail event)
                     matched (some
                              (fn [candidate]
                                (when (every? (fn [[key value]]
                                                (= value (get detail key)))
                                              (:match candidate))
                                  candidate))
                              responses)]
                 (when matched
                   (select-response! (:id matched))
                   (start-play! true)
                   true)))

             (dispose! []
               (when-not @disposed?*
                 (reset! disposed?* true)
                 (cancel-scheduled-frame!)
                 (dispose-tracks! @tracks*)
                 (doseq [target @highlighted-targets*]
                   (remove-highlight! target))
                 (dispose-highlight-style! highlight-style*)
                 (reset! highlighted-targets* [])
                 (reset! highlighted-parts* [])
                 (reset! cycle-offsets* {})
                 (reset! commit-cycle?* false)
                 (reset! tracks* [])))]
       (rebuild!)
       {:animations (into [] (mapcat :animations @tracks*))
        :play play!
        :pause pause!
        :reverse reverse!
        :seek seek!
        :restart restart!
        :select-response select-response!
        :trigger trigger!
        :highlight-parts highlight-parts!
        :get-state snapshot
        :dispose dispose!}))))

(defn dispatch-command!
  "Apply the established portable-component preview command envelope to a
  native artifact controller. Unknown commands fail closed."
  [controller command]
  (case (:action command)
    "select-response" ((:select-response controller) (:value command))
    "play" ((:play controller))
    "pause" ((:pause controller))
    "reverse" ((:reverse controller))
    "seek" ((:seek controller) (:value command))
    "simulate-event" ((:trigger controller) (:value command))
    "highlight-parts" ((:highlight-parts controller) (:value command))
    "restart" ((:restart controller))
    false))

(defn keyboard-direction
  "Resolve the portable component interaction direction represented by a
  preview key event. Returns nil for keys the preview does not own."
  [key shift-key?]
  (case key
    "ArrowLeft" "previous"
    "ArrowRight" "next"
    "Tab" (if shift-key? "previous" "next")
    nil))
