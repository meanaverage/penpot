;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns frontend-tests.data.sayhi-web-materializer-test
  (:require
   [app.common.geom.matrix :as gmt]
   [app.common.geom.point :as gpt]
   [app.common.geom.rect :as grc]
   [app.common.uuid :as uuid]
   [app.main.data.sayhi.artifact-motion-playback :as artifact-playback]
   [app.main.data.sayhi.web-materializer :as materializer]
   [app.main.data.sayhi.web-materializer.contract :as contract]
   [app.main.data.sayhi.web-materializer.motion-runtime :as motion-runtime]
   [app.main.data.sayhi.web-materializer.portable-v2 :as portable-v2]
   [app.util.code-beautify :as code-beautify]
   [cljs.test :as t :include-macros true]
   [cuerdas.core :as str]))

(def ^:private shared-namespace
  (keyword "shared" "io.sayhi.studio"))

(defn- points
  [x y width height]
  [(gpt/point x y)
   (gpt/point (+ x width) y)
   (gpt/point (+ x width) (+ y height))
   (gpt/point x (+ y height))])

(defn- fixture
  []
  (let [root-id  (uuid/next)
        child-id (uuid/next)
        dtcg      {:schemaName "io.sayhi.dtcg-package"
                   :schemaVersion "0.1.0"
                   :sets [{:name "Primitives"
                           :active true
                           :document
                           {:$schema "https://www.designtokens.org/schemas/dtcg.json"
                            :surface {:$type "color" :$value "#ffffff"}
                            :sayhi
                            {:motion
                             {:duration
                              {:selection
                               {:$type "duration"
                                :$value {:value 420 :unit "ms"}}}
                              :distance
                              {:selection
                               {:$type "number"
                                :$value 18}}
                              :easing
                              {:selection
                               {:$type "cubicBezier"
                                :$value [0.22 1 0.36 1]}}}}}}]}
        motion    {:$extensions
                   {:io.sayhi.motion
                    {:schemaVersion "0.5.0"
                     :revision 0
                     :programs
                     [{:id "verify-selection-response"
                       :label "Verify selection response"
                       :reducedMotion "skip"
                       :event "selection-change"
                       :responses
                       [{:id "next"
                         :direction "next"
                         :steps
                         [{:id "next-coverflow"
                           :driver "waapi"
                           :targetPart "selector"
                           :timelineKind "triggered"
                           :at 0
                           :duration "{sayhi.motion.duration.selection}"
                           :ease "{sayhi.motion.easing.selection}"
                           :stagger 0.025
                           :from {:x "{sayhi.motion.distance.selection}"
                                  :rotation 1.4
                                  :autoAlpha 0.72}
                           :to {:x 0 :rotation 0 :autoAlpha 1}}]}]}]}}}
        root      {:id root-id
                   :name "Verify"
                   :type :frame
                   :parent-id uuid/zero
                   :frame-id uuid/zero
                   :x 0 :y 0 :width 320 :height 240
                   :shapes [child-id]
                   :selrect (grc/make-rect 0 0 320 240)
                   :points (points 0 0 320 240)
                   :fills [{:fill-color "#ffffff" :fill-opacity 1}]
                   :plugin-data
                   {shared-namespace
                    {"dtcg-package" (js/JSON.stringify (clj->js dtcg))
                     "motion-dtcg" (js/JSON.stringify (clj->js motion))
                     "anatomy" (js/JSON.stringify #js {:selector #js ["Card"]})}}}
        child     {:id child-id
                   :name "Card"
                   :type :rect
                   :parent-id root-id
                   :frame-id root-id
                   :x 20 :y 20 :width 120 :height 80
                   :selrect (grc/make-rect 20 20 120 80)
                   :points (points 20 20 120 80)
                   :transform (gmt/matrix)
                   :fills [{:fill-color "#315f47" :fill-opacity 1}]
                   :applied-tokens {:fill "surface"}
                   :plugin-data
                   {shared-namespace
                    {"source-id" "verify.card"
                     "motion-part" "selector"}}}]
    {:objects {root-id root child-id child}
     :web-object {:id "sayhi.verify"
                  :component-id "sayhi.verification-method-selector"
                  :story-id "story.verify-methods-standalone"
                  :revision "0.2.0-native-dtcg"
                  :shape-id root-id}}))

(defn- materialize
  [params]
  ;; js-beautify's ESM facade is browser-only in this Node test bundle. The
  ;; materializer contract does not depend on whitespace formatting.
  (with-redefs [code-beautify/format-code (fn [value _type] value)]
    (materializer/materialize params)))

(t/deftest portable-v2-materializes-one-structured-artifact
  (let [{:keys [objects web-object]} (fixture)
        result   (materialize
                  {:mode contract/portable-provider
                   :studio-uri "https://studio.example/"
                   :objects objects
                   :web-object web-object})
        artifact (:artifact result)]
    (t/is (= contract/portable-provider (:activeProvider result)))
    (t/is (contract/portable-artifact? artifact))
    (t/is (= "sayhi.verification-method-selector"
             (get-in artifact [:identity :componentId])))
    (t/is (= 2 (get-in artifact [:componentGraph :shapeCount])))
    (t/is (= 2 (get-in artifact [:fidelity :selectorCount])))
    (t/is (= "surface"
             (-> artifact :designTokens :appliedByShape vals first (get "fill"))))
    (t/is (= "verify.card"
             (-> artifact :componentGraph :sourceIds vals first)))
    (t/is (= 1 (count (get-in artifact [:anatomy :parts "selector"]))))
    (t/is (= {:x 20 :y 20 :width 120 :height 80 :rotation 0 :opacity 1}
             (get-in artifact [:anatomy :parts "selector" 0 :frame])))
    (t/is (= "verify.card"
             (get-in artifact [:anatomy :parts "selector" 0 :sourceId])))
    (t/is (str/includes? (get-in artifact [:document :markup]) "Card"))
    (t/is (str/includes? (get-in artifact [:document :styles]) "#315f47"))
    (t/is (= "ready" (get-in artifact [:motionRuntime :status])))
    (t/is (= 1 (get-in artifact [:motionRuntime :trackCount])))))

(t/deftest artifact-motion-plan-resolves-dtcg-values-before-runtime-selection
  (let [{:keys [objects web-object]} (fixture)
        artifact (:artifact
                  (materialize
                   {:mode contract/portable-provider
                    :studio-uri "https://studio.example/"
                    :objects objects
                    :web-object web-object}))
        plan     (motion-runtime/compile-plan artifact)
        step     (get-in plan [:responses 0 :steps 0])]
    (t/is (= "sayhi.artifact-motion-plan" (:schemaName plan)))
    (t/is (= "0.2" (:schemaVersion plan)))
    (t/is (= "ready" (:status plan)))
    (t/is (= "next-coverflow" (:id step)))
    (t/is (= 0.42 (:duration step)))
    (t/is (= "cubic-bezier(0.22, 1, 0.36, 1)" (:ease step)))
    (t/is (= 18 (get-in step [:from :x])))
    (t/is (= [(get-in artifact [:anatomy :parts "selector" 0 :selector])]
             (:selectors step)))
    (t/is (= "waapi" (:driver step)))
    (t/is (= "waapi" (:requestedDriver step)))
    (t/is (= "web-animations-v1" (:previewAdapter step)))
    (t/is (= "native" (:translationMode step)))
    (t/is (= ["style.opacity-visibility"
              "transform.rotate"
              "transform.translate"]
             (:requiredCapabilities step)))
    (t/is (= ["waapi"] (:requestedDrivers plan)))
    (t/is (= ["web-animations-v1"] (:previewAdapters plan)))
    (t/is (= 1 (:partCount plan)))
    (t/is (= (:selectors step) (get-in plan [:parts "selector"])))))

(t/deftest artifact-motion-plan-rejects-unrenderable-tracks-without-taking-v1-down
  (let [{:keys [objects web-object]} (fixture)
        artifact (-> (:artifact
                      (materialize
                       {:mode contract/portable-provider
                        :studio-uri "https://studio.example/"
                        :objects objects
                        :web-object web-object}))
                     (assoc-in [:motion :$extensions :io.sayhi.motion
                                :programs 0 :responses 0 :steps 0 :to]
                               {}))
        plan     (motion-runtime/compile-plan artifact)]
    (t/is (= "partial" (:status plan)))
    (t/is (= 0 (:trackCount plan)))
    (t/is (= "motion_track_values_missing"
             (get-in plan [:issues 0 :code])))))

(t/deftest artifact-motion-plan-preserves-disabled-tracks-without-playing-them
  (let [{:keys [objects web-object]} (fixture)
        artifact (-> (:artifact
                      (materialize
                       {:mode contract/portable-provider
                        :studio-uri "https://studio.example/"
                        :objects objects
                        :web-object web-object}))
                     (assoc-in [:motion :$extensions :io.sayhi.motion
                                :programs 0 :responses 0 :steps 0 :enabled]
                               false))
        plan     (motion-runtime/compile-plan artifact)]
    (t/is (= "ready" (:status plan)))
    (t/is (= 0 (:trackCount plan)))
    (t/is (empty? (get-in plan [:responses 0 :steps])))
    (t/is (empty? (:issues plan)))))

(t/deftest artifact-motion-plan-rejects-properties-the-preview-cannot-render
  (let [{:keys [objects web-object]} (fixture)
        artifact (-> (:artifact
                      (materialize
                       {:mode contract/portable-provider
                        :studio-uri "https://studio.example/"
                        :objects objects
                        :web-object web-object}))
                     (assoc-in [:motion :$extensions :io.sayhi.motion
                                :programs 0 :responses 0 :steps 0 :to :blur]
                               12))
        plan     (motion-runtime/compile-plan artifact)]
    (t/is (= "partial" (:status plan)))
    (t/is (= 0 (:trackCount plan)))
    (t/is (= "motion_property_unsupported"
             (get-in plan [:issues 0 :code])))
    (t/is (str/includes? (get-in plan [:issues 0 :message]) "blur"))))

(t/deftest artifact-motion-playback-scrubs-the-materialized-dom-with-waapi
  (let [current-time* (atom nil)
        pause-count*  (atom 0)
        target        #js {:animate
                           (fn [_keyframes _options]
                             #js {:currentTime 0
                                  :pause (fn [] (swap! pause-count* inc))
                                  :cancel (fn [])})}
        document      #js {:querySelectorAll
                           (fn [selector]
                             (if (= selector ".Card") #js [target] #js []))}
        state*        (atom nil)
        plan          {:schemaName "sayhi.artifact-motion-plan"
                       :schemaVersion "0.1"
                       :status "ready"
                       :trackCount 1
                       :responses
                       [{:id "next"
                         :steps
                         [{:id "next-coverflow"
                           :driver "waapi"
                           :targetPart "selector"
                           :at 0
                           :duration 0.42
                           :ease "linear"
                           :stagger 0
                           :from {:x 18 :autoAlpha 0.72}
                           :to {:x 0 :autoAlpha 1}
                           :selectors [".Card"]}]}]}
        controller    (artifact-playback/create-runtime
                       document
                       plan
                       {:on-state #(reset! state* %)})]
    ((:seek controller) 0.5)
    (reset! current-time* (.-currentTime (first (:animations controller))))
    (t/is (= 210 @current-time*))
    (t/is (= "paused" (:status @state*)))
    (t/is (= 0.5 (:progress @state*)))
    (t/is (pos? @pause-count*))
    ((:dispose controller))))

(t/deftest artifact-motion-cycle-moves-each-card-to-a-distinct-slot-and-repeats
  (let [captures*      (atom [])
        next-frame*    (atom nil)
        frame-id*      (atom 0)
        make-target    (fn [id]
                         #js {:animate
                              (fn [keyframes options]
                                (swap! captures*
                                       conj
                                       {:id id
                                        :frames (js->clj keyframes :keywordize-keys true)
                                        :options (js->clj options :keywordize-keys true)})
                                #js {:currentTime 0
                                     :pause (fn [])
                                     :cancel (fn [])})})
        targets        {".method-left" (make-target "left")
                        ".method-center" (make-target "center")
                        ".method-right" (make-target "right")}
        document       #js {:querySelectorAll
                            (fn [selector]
                              (if-let [target (get targets selector)]
                                #js [target]
                                #js []))}
        scope          #js {:requestAnimationFrame
                            (fn [callback]
                              (reset! next-frame* callback)
                              (swap! frame-id* inc))
                            :cancelAnimationFrame (fn [_frame-id])}
        operation      {:type "cycle"
                        :groupId "methods"
                        :delta -1
                        :centerSlot 1
                        :slots [{:id 0 :x 0 :y 20 :width 120 :height 90
                                 ;; Penpot normalizes the authored -2.4deg angle.
                                 :rotation 357.6 :opacity 0.6 :zIndex 1}
                                {:id 1 :x 90 :y 0 :width 180 :height 150
                                 :rotation 0 :opacity 1 :zIndex 3}
                                {:id 2 :x 240 :y 20 :width 120 :height 90
                                 :rotation 2.4 :opacity 0.6 :zIndex 1}]
                        :items [{:shapeId "left" :selector ".method-left" :initialSlot 0}
                                {:shapeId "center" :selector ".method-center" :initialSlot 1}
                                {:shapeId "right" :selector ".method-right" :initialSlot 2}]}
        plan           {:responses
                        [{:id "next"
                          :match {:direction "next"}
                          :steps
                          [{:id "next-coverflow"
                            :driver "waapi"
                            :at 0
                            :duration 0.42
                            :ease "linear"
                            :stagger 0.025
                            :operation operation
                            :selectors [".method-left" ".method-center" ".method-right"]}]}]}
        controller     (artifact-playback/create-runtime document plan {:scope scope})]
    ((:trigger controller) {:detail {:direction "next"}})
    (let [first-run (subvec @captures* 3 6)
          starts    (into {} (map (fn [{:keys [id frames]}]
                                    [id (select-keys (first frames)
                                                     [:translate :rotate :scale])])) first-run)
          ends      (into {} (map (fn [{:keys [id frames]}]
                                    [id (select-keys (second frames)
                                                     [:translate :rotate :scale])])) first-run)]
      (t/is (= 1 (count (distinct (vals starts)))))
      (t/is (= {:translate "0px 0px" :rotate "0deg" :scale "1 1"}
               (get starts "left")))
      (t/is (= 3 (count (distinct (vals ends)))))
      (doseq [[id expected] {"left" 4.8 "center" -2.4 "right" -2.4}]
        (t/is (< (js/Math.abs
                  (- (js/parseFloat (:rotate (get ends id))) expected))
                 0.000001)))
      (doseq [timestamp [0 100 200 300 400 500]]
        (let [callback @next-frame*]
          (reset! next-frame* nil)
          (callback timestamp)))
      (t/is (= {"methods" 2}
               (:cycleState ((:get-state controller)))))
      ((:trigger controller) {:detail {:direction "next"}})
      (let [second-run (subvec @captures* 6 9)
            second-starts (into {} (map (fn [{:keys [id frames]}]
                                          [id (select-keys (first frames)
                                                           [:translate :rotate :scale])])) second-run)]
        (t/is (= ends second-starts))))
    ((:dispose controller))))

(t/deftest artifact-motion-cycle-transfers-active-slot-appearance
  (let [captures*   (atom [])
        make-target (fn [id]
                      #js {:animate
                           (fn [keyframes _options]
                             (swap! captures*
                                    conj
                                    {:id id
                                     :frames (js->clj keyframes
                                                      :keywordize-keys true)})
                             #js {:currentTime 0
                                  :pause (fn [])
                                  :cancel (fn [])})})
        targets     (into {}
                          (map (fn [id]
                                 [(str "." id) (make-target id)]))
                          ["left" "center" "right"
                           "left-ring" "left-face"
                           "center-ring" "center-face"
                           "right-ring" "right-face"])
        document    #js {:querySelectorAll
                         (fn [selector]
                           (if-let [target (get targets selector)]
                             #js [target]
                             #js []))}
        appearance  (fn [id]
                      [{:part "active-ring"
                        :selector (str "." id "-ring")
                        :active {:opacity 1}
                        :inactive {:opacity 0}}
                       {:part "active-face"
                        :selector (str "." id "-face")
                        :active {:opacity 1}
                        :inactive {:opacity 0}}])
        operation   {:type "cycle"
                     :groupId "methods"
                     :delta -1
                     :centerSlot 1
                     :slots [{:id 0 :x 0 :y 20 :width 120 :height 90
                              :rotation 357.6 :opacity 0.6 :zIndex 1}
                             {:id 1 :x 90 :y 0 :width 180 :height 150
                              :rotation 0 :opacity 1 :zIndex 3}
                             {:id 2 :x 240 :y 20 :width 120 :height 90
                              :rotation 2.4 :opacity 0.6 :zIndex 1}]
                     :items [{:shapeId "left" :selector ".left" :initialSlot 0
                              :appearanceTargets (appearance "left")}
                             {:shapeId "center" :selector ".center" :initialSlot 1
                              :appearanceTargets (appearance "center")}
                             {:shapeId "right" :selector ".right" :initialSlot 2
                              :appearanceTargets (appearance "right")}]}
        plan        {:responses
                     [{:id "next"
                       :steps
                       [{:id "next-coverflow"
                         :driver "waapi"
                         :at 0
                         :duration 0.42
                         :ease "linear"
                         :stagger 0
                         :operation operation}]}]}
        controller  (artifact-playback/create-runtime document plan)
        frames-by-id (into {}
                           (map (juxt :id :frames))
                           @captures*)]
    ;; The outgoing center loses the active shell while the incoming right
    ;; card gains both its ring and opaque face. Inactive-to-inactive cards
    ;; remain visually inactive throughout the same geometry transition.
    (doseq [suffix ["ring" "face"]]
      (t/is (= [{:opacity 0} {:opacity 0}]
               (get frames-by-id (str "left-" suffix))))
      (t/is (= [{:opacity 1} {:opacity 0}]
               (get frames-by-id (str "center-" suffix))))
      (t/is (= [{:opacity 0} {:opacity 1}]
               (get frames-by-id (str "right-" suffix)))))
    ((:dispose controller))))

(t/deftest artifact-motion-playback-maps-preview-navigation-keys
  (t/is (= "previous" (artifact-playback/keyboard-direction "ArrowLeft" false)))
  (t/is (= "next" (artifact-playback/keyboard-direction "ArrowRight" false)))
  (t/is (= "next" (artifact-playback/keyboard-direction "Tab" false)))
  (t/is (= "previous" (artifact-playback/keyboard-direction "Tab" true)))
  (t/is (nil? (artifact-playback/keyboard-direction "Enter" false))))

(t/deftest artifact-motion-playback-replaces-an-existing-playback-loop
  (let [next-frame* (atom 0)
        cancelled*  (atom [])
        scope       #js {:requestAnimationFrame
                         (fn [_callback]
                           (swap! next-frame* inc))
                         :cancelAnimationFrame
                         (fn [frame-id]
                           (swap! cancelled* conj frame-id))}
        target      #js {:animate
                         (fn [_keyframes _options]
                           #js {:currentTime 0
                                :pause (fn [])
                                :cancel (fn [])})}
        document    #js {:querySelectorAll (fn [_selector] #js [target])}
        plan        {:responses
                     [{:id "next"
                       :steps
                       [{:id "next-coverflow"
                         :driver "waapi"
                         :at 0
                         :duration 0.42
                         :ease "linear"
                         :stagger 0
                         :from {:x 18}
                         :to {:x 0}
                         :selectors [".Card"]}]}]}
        controller  (artifact-playback/create-runtime document plan {:scope scope})]
    ((:play controller))
    ((:play controller))
    (t/is (= [1] @cancelled*))
    (t/is (= "playing" (:status ((:get-state controller)))))
    ((:dispose controller))
    (t/is (= [1 2] @cancelled*))))

(t/deftest artifact-motion-playback-highlights-semantic-anatomy-parts
  (let [classes*   (atom #{})
        target     #js {:classList
                        #js {:add (fn [value]
                                    (swap! classes* conj value))
                             :remove (fn [value]
                                       (swap! classes* disj value))}
                        :animate
                        (fn [_keyframes _options]
                          #js {:currentTime 0
                               :pause (fn [])
                               :cancel (fn [])})}
        document   #js {:querySelectorAll
                        (fn [selector]
                          (if (= selector ".Card") #js [target] #js []))}
        plan       {:parts {"selector" [".Card"]}
                    :responses
                    [{:id "next"
                      :steps
                      [{:id "next-coverflow"
                        :driver "gsap"
                        :requestedDriver "gsap"
                        :previewAdapter "web-animations-v1"
                        :translationMode "semantic-preview"
                        :at 0
                        :duration 0.42
                        :ease "linear"
                        :stagger 0
                        :from {:x 18}
                        :to {:x 0}
                        :selectors [".Card"]}]}]}
        state*     (atom nil)
        controller (artifact-playback/create-runtime
                    document
                    plan
                    {:on-state #(reset! state* %)})]
    (artifact-playback/dispatch-command!
     controller
     {:action "highlight-parts" :value ["selector"]})
    (t/is (contains? @classes* "sayhi-motion-anatomy-highlight"))
    (t/is (= ["selector"] (:highlightedParts @state*)))
    (t/is (= ["gsap"] (:requestedDrivers @state*)))
    (t/is (= ["web-animations-v1"] (:previewAdapters @state*)))
    (artifact-playback/dispatch-command!
     controller
     {:action "highlight-parts" :value []})
    (t/is (empty? @classes*))
    ((:dispose controller))))

(t/deftest shadow-mode-computes-v2-without-changing-the-active-provider
  (let [{:keys [objects web-object]} (fixture)
        result (materialize
                {:mode contract/shadow-provider
                 :studio-uri "https://studio.example/"
                 :objects objects
                 :web-object web-object})]
    (t/is (= contract/projection-provider (:activeProvider result)))
    (t/is (= "comparable" (get-in result [:comparison :status])))
    (t/is (contract/portable-artifact? (:artifact result)))
    (t/is (str/includes? (get-in result [:projection :href])
                         "/studio/components/player.html"))))

(t/deftest projection-v1-does-not-pay-the-v2-materialization-cost
  (let [{:keys [objects web-object]} (fixture)
        result (materialize
                {:mode contract/projection-provider
                 :studio-uri "https://studio.example/"
                 :objects objects
                 :web-object web-object})]
    (t/is (= contract/projection-provider (:activeProvider result)))
    (t/is (nil? (:artifact result)))
    (t/is (materializer/previewable? result))))

(t/deftest shadow-mode-fails-closed-to-v1-when-v2-cannot-materialize
  (let [{:keys [objects web-object]} (fixture)
        result (with-redefs [portable-v2/materialize
                             (fn [_objects _web-object]
                               (throw (js/Error. "private failure details")))]
                 (materializer/materialize
                  {:mode contract/shadow-provider
                   :studio-uri "https://studio.example/"
                   :objects objects
                   :web-object web-object}))]
    (t/is (= contract/projection-provider (:activeProvider result)))
    (t/is (materializer/previewable? result))
    (t/is (= "candidate-failed" (get-in result [:comparison :status])))
    (t/is (= "portable_web_materialization_failed"
             (get-in result [:artifactError :code])))
    (t/is (not (str/includes? (pr-str result) "private failure details")))))

(t/deftest srcdoc-does-not-allow-styles-to-escape-their-raw-element
  (let [{:keys [objects web-object]} (fixture)
        artifact (:artifact
                  (materialize
                   {:mode contract/portable-provider
                    :studio-uri "https://studio.example/"
                    :objects objects
                    :web-object web-object}))
        document (contract/artifact-srcdoc
                  (assoc-in artifact [:document :styles]
                            "body{} </style><script>bad()</script>"))]
    (t/is (str/includes? document "<\\/style><script>bad()</script>"))
    (t/is (not (str/includes? document "</style><script>bad()</script>")))))
