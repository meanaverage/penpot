;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns frontend-tests.ui.thumbnail-recovery-test
  (:require
   [app.main.ui.thumbnail-recovery :as thumbnail-recovery]
   [beicon.v2.core :as rx]
   [cljs.test :as t :include-macros true]))

(t/deftest scheduled-thumbnail-task-uses-disposable-protocol
  (let [disposed? (atom false)
        task      (reify rx/IDisposable
                    (-dispose [_]
                      (reset! disposed? true)))]
    (thumbnail-recovery/dispose-scheduled-task! task)
    (t/is (true? @disposed?))))

(t/deftest failed-cached-image-falls-back-to-native-content
  (let [uri "blob:stale-thumbnail"]
    (t/is (false? (thumbnail-recovery/cached-image-visible? true uri uri)))
    (t/is (true? (thumbnail-recovery/native-content-visible? true uri uri)))))

(t/deftest replacement-thumbnail-becomes-visible
  (let [failed-uri "blob:stale-thumbnail"
        replacement-uri "blob:replacement-thumbnail"]
    (t/is (true? (thumbnail-recovery/cached-image-visible?
                  true
                  replacement-uri
                  failed-uri)))
    (t/is (false? (thumbnail-recovery/native-content-visible?
                   true
                   replacement-uri
                   failed-uri)))))

(t/deftest disabled-or-missing-cache-renders-native-content
  (t/is (true? (thumbnail-recovery/native-content-visible?
                false
                "blob:available-thumbnail"
                nil)))
  (t/is (true? (thumbnail-recovery/native-content-visible? true nil nil))))
