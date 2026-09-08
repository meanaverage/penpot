;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns frontend-tests.data.sayhi-studio-chrome-test
  (:require
   [app.main.data.sayhi.studio-chrome.v1 :as chrome-v1]
   [cljs.test :as t :include-macros true]))

(t/deftest host-href-prefers-a-dedicated-private-chrome-host
  (t/is (= "https://studio.example/surface/?artifactsUri=https://artifacts.example/discover/"
           (chrome-v1/host-href
            "https://studio.example/surface/?artifactsUri=https://artifacts.example/discover/"
            "https://motion.example/")))
  (t/is (= "https://motion.example/root/studio/surface-chrome/"
           (chrome-v1/host-href nil "https://motion.example/root/")))
  (t/is (= "https://studio.example/surface/?activeApp=pages&componentsUri=https%3A%2F%2Fpenpot.example%2F%23%2Fworkspace%3FstudioMode%3Dcanvas&pagesUri=https%3A%2F%2Fpenpot.example%2F%23%2Fworkspace%3FstudioMode%3Dpages"
           (chrome-v1/host-href
            "https://studio.example/surface/"
            nil
            {:active-app :pages
             :components-uri "https://penpot.example/#/workspace?studioMode=canvas"
             :pages-uri "https://penpot.example/#/workspace?studioMode=pages"})))
  (t/is (nil? (chrome-v1/host-href "javascript:alert(1)" nil))))

(t/deftest inbound-messages-are-bounded-to-layout-and-two-host-commands
  (let [message {:schemaName chrome-v1/schema-name
                 :schemaVersion chrome-v1/schema-version
                 :type "studio.bounds"
                 :payload {:height 400}}]
    (t/is (= message (chrome-v1/inbound-message (clj->js message)))))
  (t/is (some?
         (chrome-v1/inbound-message
          (clj->js {:schemaName chrome-v1/schema-name
                    :schemaVersion chrome-v1/schema-version
                    :type "studio.command"
                    :payload {:command "motion.toggle"}}))))
  (t/is (nil?
         (chrome-v1/inbound-message
          (clj->js {:schemaName chrome-v1/schema-name
                    :schemaVersion chrome-v1/schema-version
                    :type "studio.command"
                    :payload {:command "document.delete"}}))))
  (t/is (nil?
         (chrome-v1/inbound-message
          (clj->js {:schemaName chrome-v1/schema-name
                    :schemaVersion "2.0"
                    :type "studio.context.request"
                    :payload {}})))))

(t/deftest width-requests-are-separate-and-bounded
  (doseq [width [80 752 1280 1600]]
    (let [message {:schemaName chrome-v1/schema-name
                   :schemaVersion chrome-v1/schema-version
                   :type "studio.width"
                   :payload {:width width}}]
      (t/is (= message (chrome-v1/inbound-message (clj->js message))))))
  (doseq [payload [{:width 79} {:width 1601} {:width 800.5}
                   {:width "900"} {:width nil} {:width 900 :height 80}]]
    (t/is (nil? (chrome-v1/inbound-message
                 (clj->js {:schemaName chrome-v1/schema-name
                           :schemaVersion chrome-v1/schema-version
                           :type "studio.width"
                           :payload payload}))))))

(t/deftest public-context-removes-penpot-data-beyond-the-current-selection
  (t/is (= "en" (chrome-v1/public-locale "")))
  (t/is (= "es" (chrome-v1/public-locale " es ")))
  (t/is (= "dark" (chrome-v1/public-theme :dark)))
  (t/is (= "light" (chrome-v1/public-theme :default)))
  (t/is (= [{:id "shape-1" :name "Verify" :type "frame"}]
           (chrome-v1/selection-context
            [{:id "shape-1" :name "Verify" :type :frame :private-data "excluded"}]))))
