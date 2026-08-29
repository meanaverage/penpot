;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns frontend-tests.data.sayhi-motion-preview-test
  (:require
   [app.main.data.sayhi.motion-preview.v1 :as motion-preview]
   [cljs.test :as t :include-macros true]))

(def ^:private artifact
  {:identity {:componentId "sayhi.verify"
              :revision "artifact.revision.7"}})

(def ^:private motion-document
  {:$extensions
   {:io.sayhi.motion
    {:schemaName "sayhi.motion"
     :schemaVersion "0.5.0"
     :revision 4}}})

(t/deftest recipe-relay-requires-the-current-component-and-motion-revision
  (let [message {:type "studio.preview.recipe"
                 :payload {:componentId "sayhi.verify"
                           :revision 4
                           :recipe {:id "verify.motion"}}}
        relayed (motion-preview/recipe-message
                 message artifact motion-document)]
    (t/is (= "host.motion.recipe" (:type relayed)))
    (t/is (= "artifact.revision.7" (get-in relayed [:payload :revision])))
    (t/is (nil? (motion-preview/recipe-message
                 (assoc-in message [:payload :revision] 3)
                 artifact
                 motion-document)))
    (t/is (nil? (motion-preview/recipe-message
                 (assoc-in message [:payload :componentId] "other.component")
                 artifact
                 motion-document)))))

(t/deftest runtime-state-is-bounded-and-translates-to-motion-studio
  (let [state #js {:schema motion-preview/schema-name
                   :schemaVersion motion-preview/schema-version
                   :type "runtime.motion.state"
                   :payload #js {:status "playing"
                                 :progress 0.5
                                 :time 0.25
                                 :duration 0.5
                                 :responseId "next"
                                 :direction 1}}
        message (motion-preview/inbound-message state)
        translated (motion-preview/motion-host-message message)]
    (t/is (= "runtime.motion.state" (:type message)))
    (t/is (= "host.preview.state" (:type translated)))
    (t/is (= 0.5 (get-in translated [:payload :progress])))
    (t/is (nil? (motion-preview/inbound-message
                 #js {:schema motion-preview/schema-name
                      :schemaVersion motion-preview/schema-version
                      :type "runtime.motion.state"
                      :payload #js {:status "playing"
                                    :progress 2
                                    :time 0.25
                                    :duration 0.5
                                    :responseId "next"
                                    :direction 1}})))))

(t/deftest commands-remain-closed-to-preview-commands
  (t/is (= {:schema motion-preview/schema-name
            :schemaVersion motion-preview/schema-version
            :type "host.motion.command"
            :payload {:command "seek" :progress 0.2}}
           (motion-preview/command-message
            {:type "studio.preview.command"
             :payload {:command "seek" :progress 0.2}})))
  (t/is (nil? (motion-preview/command-message
               {:type "studio.history.command"
                :payload {:command "undo"}}))))
