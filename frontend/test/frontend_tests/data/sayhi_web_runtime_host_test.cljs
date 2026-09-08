;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns frontend-tests.data.sayhi-web-runtime-host-test
  (:require
   [app.main.data.sayhi.web-runtime-host.v1 :as runtime-host]
   [cljs.test :as t :include-macros true]))

(t/deftest accepts-bounded-runtime-requests
  (let [message
        (runtime-host/inbound-message
         #js {:schema runtime-host/schema-name
              :schemaVersion runtime-host/schema-version
              :type "runtime.artifact.request"
              :payload #js {:componentId "sayhi.verification-method-selector"
                            :revision "0.5.1-projection-v2"}})]
    (t/is (= "runtime.artifact.request" (:type message)))
    (t/is (= "sayhi.verification-method-selector"
             (get-in message [:payload :componentId])))))

(t/deftest rejects-unknown-messages-properties-and-versions
  (doseq [message
          [#js {:schema runtime-host/schema-name
                :schemaVersion "2.0"
                :type "runtime.context.request"
                :payload #js {}}
           #js {:schema runtime-host/schema-name
                :schemaVersion runtime-host/schema-version
                :type "runtime.eval"
                :payload #js {}}
           #js {:schema runtime-host/schema-name
                :schemaVersion runtime-host/schema-version
                :type "runtime.artifact.request"
                :payload #js {:componentId "sayhi.verify"
                              :revision "revision.1"
                              :source "javascript"}}]]
    (t/is (nil? (runtime-host/inbound-message message)))))

(t/deftest artifact-requests-remain-bound-to-the-current-artifact
  (let [artifact {:identity {:componentId "sayhi.verification-method-selector"
                             :revision "revision.7"}}]
    (t/is (runtime-host/current-artifact-request?
           {:componentId "sayhi.verification-method-selector"
            :revision "revision.7"}
           artifact))
    (t/is (not (runtime-host/current-artifact-request?
                {:componentId "sayhi.verification-method-selector"
                 :revision "revision.6"}
                artifact)))))

(t/deftest context-messages-bind-the-selected-source-and-capabilities
  (let [message
        (runtime-host/context-message
         {:file-id "file.1"
          :page-id "page.2"
          :root-shape-id "shape.3"
          :component-id "sayhi.verification-method-selector"
          :revision "revision.7"
          :component-name "SayHi Verify"
          :theme "dark"
          :locale "en-US"})]
    (t/is (= runtime-host/schema-name (:schema message)))
    (t/is (= {:fileId "file.1"
              :pageId "page.2"
              :selection
              [{:id "shape.3"
                :name "SayHi Verify"
                :type "frame"
                :componentId "sayhi.verification-method-selector"
                :revision "revision.7"}]
              :capabilities runtime-host/host-capabilities
              :theme "dark"
              :locale "en-US"}
             (get-in message [:payload :context])))))

(t/deftest builds-the-standalone-host-only-from-trusted-runtime-configuration
  (t/is (= "https://runtime.example/base/studio/penpot-web-runtime-host/?embed=1"
           (runtime-host/host-href "https://runtime.example/base/")))
  (t/is (nil? (runtime-host/host-href "javascript:alert(1)"))))
