;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns frontend-tests.ui.sayhi-motion-context-test
  (:require
   [app.main.ui.sayhi.motion-context :as motion-context]
   [cljs.test :refer-macros [deftest is testing]]))

(deftest motion-toolbar-is-only-present-on-the-sayhi-surface
  (is (nil? (motion-context/toolbar-model nil))))

(deftest motion-toolbar-explains-when-selection-is-not-animatable
  (let [model (motion-context/toolbar-model
               {:available? false
                :open? false
                :on-toggle identity})]
    (is (false? (:available? model)))
    (is (= "Select a SayHi web component with motion" (:label model)))))

(deftest motion-toolbar-reflects-the-shared-dock-state
  (testing "the native icon and app launcher use the same toggle callback"
    (let [toggle identity
          model (motion-context/toolbar-model
                 {:available? true
                  :open? true
                  :on-toggle toggle})]
      (is (true? (:available? model)))
      (is (true? (:open? model)))
      (is (= "Motion Studio" (:label model)))
      (is (identical? toggle (:on-toggle model))))))
