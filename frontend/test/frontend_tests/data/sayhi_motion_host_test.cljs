;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns frontend-tests.data.sayhi-motion-host-test
  (:require
   [app.main.data.sayhi.motion-host.v1 :as motion-host]
   [cljs.test :as t :include-macros true]))

(def ^:private component-source
  {:shape-id "shape.1"
   :web-object {:component-id "sayhi.verify"
                :revision "revision.7"}
   :artifact {:identity {:componentId "sayhi.verify"}}
   :motion-document
   {:$extensions
    {:io.sayhi.motion
     {:schemaName "sayhi.motion"
      :schemaVersion "0.5.0"
      :revision 3
      :programs []}}}})

(t/deftest accepts-only-the-bounded-motion-read-and-preview-contract
  (doseq [message
          [#js {:schema motion-host/schema-name
                :schemaVersion motion-host/schema-version
                :type "studio.motion.read"
                :payload #js {:componentId "sayhi.verify"}}
           #js {:schema motion-host/schema-name
                :schemaVersion motion-host/schema-version
                :type "studio.preview.command"
                :payload #js {:command "seek" :progress 0.4}}]]
    (t/is (some? (motion-host/inbound-message message))))
  (t/is (nil?
         (motion-host/inbound-message
          #js {:schema motion-host/schema-name
               :schemaVersion motion-host/schema-version
               :type "studio.history.command"
               :payload #js {:command "begin"}}))))

(t/deftest context-and-motion-document-remain-selection-bound
  (let [context (motion-host/context-message
                 {:file-id "file.1"
                  :page-id "page.1"
                  :component-source component-source
                  :theme "dark"
                  :locale "en-US"})
        document (motion-host/motion-document-message component-source)]
    (t/is (= motion-host/capabilities
             (get-in context [:payload :context :capabilities])))
    (t/is (= "sayhi.verify"
             (get-in context [:payload :context :selection 0 :componentId])))
    (t/is (= 3 (get-in document [:payload :revision])))
    (t/is (= "sayhi.motion"
             (get-in document
                     [:payload :document :$extensions
                      :io.sayhi.motion :schemaName])))))

(t/deftest host-uri-and-window-validation-fail-closed
  (t/is (= "https://motion.example/base/studio/penpot-motion-host/?embed=1"
           (motion-host/host-href "https://motion.example/base/")))
  (t/is (nil? (motion-host/host-href "javascript:alert(1)")))
  (let [frame-window (js-obj)]
    (t/is (motion-host/same-window? frame-window frame-window))
    (t/is (not (motion-host/same-window? frame-window (js-obj))))))
