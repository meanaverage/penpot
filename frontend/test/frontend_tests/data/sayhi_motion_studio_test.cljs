;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns frontend-tests.data.sayhi-motion-studio-test
  (:require
   [app.main.data.sayhi.motion-studio :as motion-studio]
   [cljs.test :as t :include-macros true]))

(def ^:private shared-namespace
  (keyword "shared" "io.sayhi.studio"))

(defn- component-shape
  ([] (component-shape nil))
  ([motion]
   {:id :verify
    :name "SayHi Verify"
    :type :frame
    :plugin-data
    {shared-namespace
     (cond-> {"component-id" "sayhi.verification-method-selector"
              "component-version" "0.1.3"
              "story-id" "story.verify-methods-standalone"
              "projection" "editable-vector/v1"}
       motion (assoc "motion-contract" (js/JSON.stringify (clj->js motion))))}}))

(defn- component-shape-with-dtcg
  [document]
  (assoc-in (component-shape)
            [:plugin-data shared-namespace "motion-dtcg"]
            (js/JSON.stringify (clj->js document))))

(def ^:private legacy-motion
  {:schemaName "sayhi.penpot.motion"
   :schemaVersion "0.1.0"
   :id "verify-selection-response"
   :label "Verify selection response"
   :parts {:method ["Email-code" "Passkey-selected" "Authenticator"]}
   :ambient []
   :responses [{:id "next"
                :label "Next method"
                :steps [{:id "next-coverflow"
                         :targetPart "method"
                         :at 0
                         :duration 0.42}]}]})

(def ^:private dtcg-motion
  {:$description "SayHi Verify motion"
   :motion {:fast {:$type "duration"
                   :$value {:value 180 :unit "ms"}}}
   :$extensions
   {:vendor.example {:preserve {:unknown true}}
    :io.sayhi.motion
    {:schemaVersion "0.5.0"
     :revision 7
     :tokenRevision "tokens.12"
     :programs [legacy-motion]}}})

(def ^:private projection-v2-motion
  {:$extensions
   {:io.sayhi.motion
    {:schemaName "sayhi.motion"
     :schemaVersion "0.5.0"
     :revision 0
     :tokenRevision "verify-projection-v2.1"
     :programs
     [{:id "verify-selection-response"
       :label "Verify selection response"
       :trigger {:type "event" :event "selection.change"}
       :reducedMotion "skip"
       :responses
       [{:id "previous"
         :label "Previous method"
         :direction "previous"
         :match {:direction "previous"}
         :steps
         [{:id "previous-carousel-flow"
           :driver "css-transition"
           :targetPart "selector"
           :timelineKind "triggered"
           :at 0
           :duration "{motion.duration.selection}"
           :ease "{motion.easing.selection}"
           :stagger 0.025
           :from {:x -18 :rotation -1.4 :autoAlpha 0.72}
           :to {:x 0 :rotation 0 :autoAlpha 1}}]}
        {:id "next"
         :label "Next method"
         :direction "next"
         :match {:direction "next"}
         :steps
         [{:id "next-carousel-flow"
           :driver "css-transition"
           :targetPart "selector"
           :timelineKind "triggered"
           :at 0
           :duration "{motion.duration.selection}"
           :ease "{motion.easing.selection}"
           :stagger 0.025
           :from {:x 18 :rotation 1.4 :autoAlpha 0.72}
           :to {:x 0 :rotation 0 :autoAlpha 1}}]}]}]}}})

(t/deftest native-host-is-an-explicit-http-mode
  (t/is (true? (motion-studio/native-mode? "native-v1" "https://studio.example/")))
  (t/is (true? (motion-studio/native-mode? "native-v2" "https://studio.example/")))
  (t/is (false? (motion-studio/native-v2-mode? "native-v1")))
  (t/is (true? (motion-studio/native-v2-mode? "native-v2")))
  (t/is (false? (motion-studio/native-mode? "legacy" "https://studio.example/")))
  (t/is (false? (motion-studio/native-mode? "native-v1" "javascript:alert(1)")))
  (t/is (= "https://studio.example/base/studio/penpot-motion-host/"
           (motion-studio/host-href "https://studio.example/base/"))))

(t/deftest canvas-preview-surface-is-an-explicit-reversible-mode
  (t/is (true? (motion-studio/canvas-preview-surface? "canvas")))
  (t/is (false? (motion-studio/canvas-preview-surface? "focused")))
  (t/is (false? (motion-studio/canvas-preview-surface? nil))))

(t/deftest canvas-preview-layout-preserves-design-size-while-following-pan-and-zoom
  (t/is (= {:left 120
            :top 240
            :screen-width 1160
            :screen-height 1000
            :design-width 580
            :design-height 500
            :scale 2}
           (motion-studio/canvas-preview-layout
            {:x -1
             :y -1
             :width 1
             :height 1
             :selrect {:x 100 :y 200 :width 580 :height 500}}
            {:x 40 :y 80}
            2))))

(t/deftest canvas-preview-layout-rejects-incomplete-or-degenerate-geometry
  (t/is (nil? (motion-studio/canvas-preview-layout
               {:x 10 :y 20 :width 0 :height 100}
               {:x 0 :y 0}
               1)))
  (t/is (nil? (motion-studio/canvas-preview-layout
               {:x 10 :y 20 :width 100 :height 100}
               {:x 0 :y 0}
               0)))
  (t/is (nil? (motion-studio/canvas-preview-layout
               {:x 10 :y 20 :width 100 :height 100}
               nil
               1))))

(t/deftest cross-origin-messages-wait-for-the-exact-frame-origin
  (t/is (false? (motion-studio/frame-ready-for-origin?
                 "https://motion.example"
                 nil)))
  (t/is (false? (motion-studio/frame-ready-for-origin?
                 "https://motion.example"
                 "https://penpot.example")))
  (t/is (true? (motion-studio/frame-ready-for-origin?
                "https://motion.example"
                "https://motion.example"))))

(t/deftest cross-origin-window-comparison-does-not-read-foreign-properties
  (let [property-reads (atom 0)
        foreign-window (js/Proxy.
                        #js {}
                        #js {:get (fn [_target _property]
                                    (swap! property-reads inc)
                                    nil)})]
    (t/is (true? (motion-studio/same-cross-origin-window?
                  foreign-window
                  foreign-window)))
    (t/is (false? (motion-studio/same-cross-origin-window?
                   foreign-window
                   #js {})))
    (t/is (zero? @property-reads))))

(t/deftest pending-preview-messages-have-a-bounded-order-preserving-queue
  (let [queued (reduce motion-studio/enqueue-preview-message [] (range 40))]
    (t/is (= 32 (count queued)))
    (t/is (= (vec (range 8 40)) queued))))

(t/deftest portable-component-selection-becomes-bounded-host-context
  (let [shape   (component-shape)
        objects {:verify shape}
        context (motion-studio/host-context
                 {:file-id :file
                  :page-id :page
                  :objects objects
                  :selected [:verify]
                  :shapes [shape]
                  :theme "dark"
                  :locale "en-US"})]
    (t/is (= "io.sayhi.penpot.motion-host" motion-studio/schema-name))
    (t/is (= ["selection.read"] (:capabilities context)))
    (t/is (= "sayhi.verification-method-selector"
             (get-in context [:selection 0 :componentId])))
    (t/is (= "SayHi Verify" (get-in context [:selection 0 :name])))
    (t/is (= "dark" (:theme context)))
    (t/is (= "host.context" (:type (motion-studio/context-message context))))))

(t/deftest motion-preview-is-a-version-two-controllable-portable-object
  (let [shape      (component-shape legacy-motion)
        objects    {:verify shape}
        web-object (motion-studio/selected-web-object objects [:verify])
        href       (motion-studio/motion-preview-href
                    "https://studio.example/base/"
                    objects
                    web-object
                    true)
        context    (motion-studio/host-context
                    {:file-id :file
                     :page-id :page
                     :objects objects
                     :selected [:verify]
                     :shapes [shape]
                     :theme "light"
                     :locale "en"
                     :preview-enabled? true})]
    (t/is (.includes href "/studio/components/player.html"))
    (t/is (.includes href "motionStudio=1"))
    (t/is (.includes href "motionEngine=v2"))
    (t/is (.includes href "embed=canvas"))
    (t/is (= ["selection.read"
              "motion.read"
              "motion.write"
              "history.transaction"
              "preview.control"
              "anatomy.highlight"]
             (:capabilities context)))))

(t/deftest selected-response-simulates-the-real-component-event
  (let [shape      (component-shape legacy-motion)
        payload    (motion-studio/motion-document {:verify shape} [:verify])]
    (t/is (= {:event "selection.change"
              :detail {:direction "next"}}
             (motion-studio/preview-simulation payload "next")))
    (t/is (nil? (motion-studio/preview-simulation payload "missing")))))

(t/deftest legacy-imported-motion-is-advertised-read-only
  (let [shape    (component-shape legacy-motion)
        objects  {:verify shape}
        payload  (motion-studio/motion-document objects [:verify])
        context  (motion-studio/host-context
                  {:file-id :file
                   :page-id :page
                   :objects objects
                   :selected [:verify]
                   :shapes [shape]
                   :theme "light"
                   :locale "en"})]
    (t/is (= ["selection.read" "motion.read" "motion.write" "history.transaction"]
             (:capabilities context)))
    (t/is (= "sayhi.verification-method-selector" (:componentId payload)))
    (t/is (= 0 (:revision payload)))
    (t/is (= "0.5.0"
             (get-in payload [:document :$extensions :io.sayhi.motion :schemaVersion])))
    (t/is (= "sayhi.penpot.motion"
             (get-in payload [:document :$extensions :io.sayhi.motion :programs 0 :schemaName])))
    (t/is (= ["Verification-methods"]
             (get-in payload [:document :$extensions :io.sayhi.motion :programs 0 :parts :selector])))
    (t/is (= "method"
             (get-in payload [:document :$extensions :io.sayhi.motion :programs 0 :responses 0 :steps 0 :targetPart])))
    (t/is (= {:type "cycle" :groupId "method"}
             (get-in payload [:document :$extensions :io.sayhi.motion :programs 0 :responses 0 :steps 0 :operation])))
    (t/is (= "method" (get-in legacy-motion [:responses 0 :steps 0 :targetPart]))
          "compatibility projection must not mutate imported plugin data")
    (t/is (= "host.motion.document"
             (:type (motion-studio/motion-document-message payload))))))

(t/deftest dtcg-motion-is-read-with-its-revision-and-unknown-extensions-intact
  (let [shape   (component-shape-with-dtcg dtcg-motion)
        objects {:verify shape}
        payload (motion-studio/motion-document objects [:verify])]
    (t/is (= 7 (:revision payload)))
    (t/is (= {:preserve {:unknown true}}
             (get-in payload [:document :$extensions :vendor.example])))
    (t/is (= {:value 180 :unit "ms"}
             (get-in payload [:document :motion :fast :$value])))))

(t/deftest projection-v2-motion-is-readable-from-the-imported-root
  (let [shape   (component-shape-with-dtcg projection-v2-motion)
        objects {:verify shape}
        payload (motion-studio/motion-document objects [:verify])]
    (t/is (some? payload))
    (t/is (= 0 (:revision payload)))
    (t/is (= "verify-selection-response"
             (get-in payload
                     [:document :$extensions :io.sayhi.motion :programs 0 :id])))
    (t/is (= "method"
             (get-in payload
                     [:document :$extensions :io.sayhi.motion
                      :programs 0 :responses 1 :steps 0 :targetPart])))))

(t/deftest motion-writes-use-optimistic-revisions-and-preserve-the-current-dtcg-document
  (let [next-document (-> dtcg-motion
                          (assoc-in [:$extensions :io.sayhi.motion :revision] 99)
                          (assoc-in [:$extensions :io.sayhi.motion :programs 0 :label]
                                    "Updated motion"))
        written       (motion-studio/prepare-motion-write dtcg-motion 7 next-document)]
    (t/is (= 8 (motion-studio/motion-revision written)))
    (t/is (= "Updated motion"
             (get-in written [:$extensions :io.sayhi.motion :programs 0 :label])))
    (t/is (= {:preserve {:unknown true}}
             (get-in written [:$extensions :vendor.example])))
    (t/is (= {:value 180 :unit "ms"}
             (get-in written [:motion :fast :$value])))
    (t/is (= 7 (motion-studio/motion-revision dtcg-motion))
          "the current document must remain immutable")))

(t/deftest stale-motion-writes-fail-with-the-current-revision
  (t/is (thrown-with-msg?
         js/Error
         #"revision changed"
         (motion-studio/prepare-motion-write dtcg-motion 6 dtcg-motion)))
  (try
    (motion-studio/prepare-motion-write dtcg-motion 6 dtcg-motion)
    (t/is false "the stale write should fail")
    (catch :default error
      (t/is (= "motion_revision_conflict" (:code (ex-data error))))
      (t/is (= 7 (:currentRevision (ex-data error)))))))

(t/deftest plain-penpot-shapes-do-not-enable-motion-studio
  (let [objects {:plain {:id :plain :name "Rectangle" :type :rect}}]
    (t/is (false? (motion-studio/eligible-selection? objects [:plain])))
    (t/is (true? (motion-studio/eligible-selection? {:verify (component-shape)} [:verify])))))

(t/deftest selected-penpot-component-root-enables-the-nested-motion-document
  (let [objects {:component-root {:id :component-root
                                  :name "SayHi Verify"
                                  :type :frame
                                  :component-id :penpot-component
                                  :component-root true
                                  :shapes [:verify]}
                 :verify (assoc (component-shape legacy-motion)
                                :parent-id :component-root)}]
    (t/is (true? (motion-studio/eligible-selection? objects [:component-root])))
    (t/is (= "sayhi.verification-method-selector"
             (:componentId
              (motion-studio/motion-document objects [:component-root]))))))

(t/deftest repaired-component-wrapper-can-own-the-motion-document
  (let [root (component-shape-with-dtcg dtcg-motion)
        child (assoc (component-shape)
                     :id :verify-projection
                     :parent-id :component-root)
        objects {:component-root (assoc root
                                        :id :component-root
                                        :component-id :penpot-component
                                        :component-root true
                                        :shapes [:verify-projection])
                 :verify-projection child}
        payload (motion-studio/motion-document objects [:verify-projection])]
    (t/is (= "sayhi.verification-method-selector" (:componentId payload)))
    (t/is (= 7 (:revision payload)))
    (t/is (= {:preserve {:unknown true}}
             (get-in payload [:document :$extensions :vendor.example])))))
