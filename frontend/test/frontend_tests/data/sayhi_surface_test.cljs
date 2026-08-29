;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns frontend-tests.data.sayhi-surface-test
  (:require
   [app.main.data.sayhi.surface :as surface]
   [cljs.test :as t :include-macros true]))

(t/deftest surface-enablement-is-explicit
  (t/is (surface/enabled? "canvas" "" "#/dashboard/recent"))
  (t/is (surface/enabled? nil "?sayhiStudio=1" "#/dashboard/recent"))
  (t/is (surface/enabled? nil "" "#/workspace?studioMode=pages"))
  (t/is (not (surface/enabled? nil "" "#/dashboard/recent"))))

(t/deftest active-app-and-app-links-share-the-current-penpot-location
  (let [href   "https://penpot.example/#/workspace?file-id=1&studioMode=pages"
        target (surface/app-location-href href :artifacts)
        url    (js/URL. target)
        params (js/URLSearchParams. (second (.split (.-hash url) "?")))]
    (t/is (= :pages (surface/active-app "" "#/workspace?studioMode=pages")))
    (t/is (= :components (surface/active-app "" "#/workspace")))
    (t/is (= "1" (.get params "file-id")))
    (t/is (= "artifacts" (.get params "studioMode")))))

(t/deftest controls-shortcut-does-not-steal-editable-input
  (let [event (fn [tag editable?]
                (js-obj "key" "\\"
                        "repeat" false
                        "altKey" false
                        "ctrlKey" false
                        "metaKey" false
                        "target" (js-obj "tagName" tag
                                         "isContentEditable" editable?)))]
    (t/is (surface/controls-shortcut? (event "DIV" false)))
    (t/is (not (surface/controls-shortcut? (event "TEXTAREA" false))))
    (t/is (not (surface/controls-shortcut? (event "DIV" true))))))

(t/deftest palette-inset-only-reserves-visible-palette-space
  (t/is (= 76 (surface/canvas-bottom-inset #{:colorpalette} 76)))
  (t/is (= 0 (surface/canvas-bottom-inset #{:colorpalette :hide-ui} 76)))
  (t/is (= 0 (surface/canvas-bottom-inset #{} 76)))
  (t/is (= 0 (surface/canvas-bottom-inset #{:textpalette} nil))))
