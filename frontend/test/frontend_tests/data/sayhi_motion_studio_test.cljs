;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns frontend-tests.data.sayhi-motion-studio-test
  (:require
   [app.main.data.sayhi.motion-host.v1 :as motion-host-v1]
   [app.main.data.sayhi.motion-studio :as motion-studio]
   [app.main.ui.sayhi.motion-dock-sizing :as dock-sizing]
   [cljs.test :as t :include-macros true]))

(def ^:private shared-namespace
  (keyword "shared" "io.sayhi.studio"))

(t/deftest motion-layout-is-bounded-and-separate-from-editing
  (let [message {:schema "io.sayhi.studio.motion-layout"
                 :schemaVersion "1.0" :type "studio.bounds" :payload {:height 250}}]
    (t/is (= 250 (dock-sizing/content-height (clj->js message))))
    (doseq [height [-1 0 63 4097 250.5 "250" js/Infinity nil]]
      (t/is (nil? (dock-sizing/content-height (clj->js (assoc-in message [:payload :height] height))))))
    (doseq [invalid [(assoc message :type "studio.motion.write")
                     (assoc message :schemaVersion "2.0")
                     (assoc message :extra true)
                     (assoc-in message [:payload :document] {})]]
      (t/is (nil? (dock-sizing/content-height (clj->js invalid)))))))

(t/deftest motion-layout-cannot-grow-beyond-the-available-viewport
  (t/is (= 296 (dock-sizing/fit-height 296 700)))
  (t/is (= 144 (dock-sizing/fit-height 50 700)))
  (t/is (= 400 (dock-sizing/fit-height 900 400)))
  (t/is (= 80 (dock-sizing/fit-height 300 80))))

(t/deftest motion-collapse-is-explicit-and-carries-no-editing-authority
  (let [message {:schema "io.sayhi.studio.motion-layout"
                 :schemaVersion "1.0" :type "studio.presentation" :payload {:collapsed true}}]
    (t/is (true? (dock-sizing/collapsed-state (clj->js message))))
    (t/is (false? (dock-sizing/collapsed-state (clj->js (assoc-in message [:payload :collapsed] false)))))
    (t/is (nil? (dock-sizing/content-height (clj->js message))))
    (doseq [invalid [(assoc message :schemaVersion "2.0")
                     (assoc message :schema "io.sayhi.penpot.motion-host")
                     (assoc message :type "studio.motion.write")
                     (assoc message :extra true)
                     (assoc-in message [:payload :height] 64)
                     (assoc-in message [:payload :document] {})
                     (assoc-in message [:payload :collapsed] "false")
                     (assoc-in message [:payload :collapsed] nil)
                     (assoc-in message [:payload :collapsed] 0)]]
      (t/is (nil? (dock-sizing/collapsed-state (clj->js invalid)))))))

(t/deftest motion-collapsed-bar-is-not-subject-to-expanded-minimum
  (t/is (= 66 (dock-sizing/fit-height 66 700 64)))
  (t/is (= 40 (dock-sizing/fit-height 66 40 64)))
  (t/is (= 144 (dock-sizing/fit-height 66 700))))

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

(defn- host-message
  [type payload]
  (clj->js {:schema motion-host-v1/schema-name
            :schemaVersion motion-host-v1/schema-version
            :type type
            :payload payload}))

(t/deftest inbound-host-messages-are-bounded-and-versioned
  (t/is (= {:schema motion-host-v1/schema-name
            :schemaVersion motion-host-v1/schema-version
            :type "studio.preview.command"
            :payload {:command "seek" :progress 0.25}}
           (motion-host-v1/assert-inbound-message
            (host-message "studio.preview.command"
                          {:command "seek" :progress 0.25}))))
  (t/is (nil? (motion-host-v1/inbound-message
               (host-message "studio.history.command"
                             {:command "begin"
                              :transactionId "motion.1"
                              :label "Edit motion"}))))
  (t/is (nil? (motion-host-v1/inbound-message
               (clj->js {:schema motion-host-v1/schema-name
                         :schemaVersion motion-host-v1/schema-version
                         :type "studio.context.request"
                         :payload {}
                         :credentials "not allowed"}))))
  (t/is (nil? (motion-host-v1/inbound-message
               (host-message "studio.preview.command"
                             {:command "seek" :progress 2}))))
  (t/is (nil? (motion-host-v1/inbound-message
               (host-message "studio.motion.write"
                             {:componentId "sayhi.verify"
                              :revision 1
                              :label "Too large"
                              :document {:value (apply str (repeat 1000001 "x"))}})))))

(t/deftest preview-recipes-are-bound-to-the-current-component-revision
  (let [current {:componentId "sayhi.verify" :revision 7}]
    (t/is (true? (motion-host-v1/current-preview-recipe?
                  {:componentId "sayhi.verify" :revision 7}
                  current)))
    (t/is (false? (motion-host-v1/current-preview-recipe?
                   {:componentId "sayhi.other" :revision 7}
                   current)))
    (t/is (false? (motion-host-v1/current-preview-recipe?
                   {:componentId "sayhi.verify" :revision 6}
                   current)))))

(t/deftest internal-unavailable-preview-state-has-a-public-v1-equivalent
  (t/is (= "empty" (motion-host-v1/public-preview-status "unavailable")))
  (t/is (= "playing" (motion-host-v1/public-preview-status "playing")))
  (t/is (= "empty" (motion-host-v1/public-preview-status "unknown"))))

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
    (t/is (= ["selection.read" "motion.read" "motion.write"]
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
