;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns frontend-tests.data.sayhi-studio-canvas-test
  (:require
   [app.main.data.sayhi.studio-canvas :as studio-canvas]
   [cljs.test :as t :include-macros true]))

(t/deftest studio-canvas-mode-is-explicit-and-hash-route-aware
  (t/is (studio-canvas/studio-canvas-mode-from-location?
         ""
         "#/workspace?team-id=team&studioMode=canvas"))
  (t/is (studio-canvas/studio-canvas-mode-from-location?
         "?sayhiStudio=1"
         "#/workspace?team-id=team"))
  (t/is (studio-canvas/studio-canvas-mode-from-location?
         ""
         "#/workspace?team-id=team&studioMode=pages"))
  (t/is (not (studio-canvas/studio-canvas-mode-from-location?
              ""
              "#/workspace?team-id=team"))))

(t/deftest configured-sayhi-canvas-keeps-the-prompt-surface-enabled
  (t/is (studio-canvas/studio-surface-enabled? "canvas"))
  (t/is (studio-canvas/studio-surface-enabled? " CANVAS "))
  (t/is (not (studio-canvas/studio-surface-enabled? "standard")))
  (t/is (not (studio-canvas/studio-surface-enabled? nil))))

(t/deftest studio-app-follows-the-explicit-studio-mode
  (t/is (= :components
           (studio-canvas/studio-app-from-location "" "#/workspace?studioMode=canvas")))
  (t/is (= :pages
           (studio-canvas/studio-app-from-location "" "#/workspace?studioMode=pages")))
  (t/is (= :components
           (studio-canvas/studio-app-from-location "" "#/workspace?team-id=team"))))

(t/deftest studio-controls-shortcut-stays-out-of-editable-fields
  (let [event (fn [overrides]
                (js/Object.assign
                 #js {:key "\\"
                      :repeat false
                      :altKey false
                      :ctrlKey false
                      :metaKey false
                      :target #js {:tagName "DIV"
                                   :isContentEditable false}}
                 (clj->js overrides)))]
    (t/is (studio-canvas/studio-controls-shortcut? (event {})))
    (t/is (not (studio-canvas/studio-controls-shortcut?
                (event {:target {:tagName "INPUT" :isContentEditable false}}))))
    (t/is (not (studio-canvas/studio-controls-shortcut?
                (event {:target {:tagName "DIV" :isContentEditable true}}))))
    (t/is (not (studio-canvas/studio-controls-shortcut? (event {:key "/"}))))
    (t/is (not (studio-canvas/studio-controls-shortcut? (event {:ctrlKey true}))))
    (t/is (not (studio-canvas/studio-controls-shortcut? (event {:repeat true}))))))

(t/deftest studio-apps-preserve-the-established-navigation-set
  (t/is (= [:components :pages :video :paper :artifacts]
           (mapv :id studio-canvas/studio-apps)))
  (t/is (= "https://studio.example/studio/pages/"
           (studio-canvas/studio-app-href "https://studio.example/root" :pages)))
  (t/is (= "https://studio.example/studio/canvas/?studioMode=video"
           (studio-canvas/studio-app-href "https://studio.example/root" :video)))
  (t/is (= "https://studio.example/studio/canvas/?studioMode=paper"
           (studio-canvas/studio-app-href "https://studio.example/root" :paper)))
  (t/is (= "https://studio.example/artifacts/"
           (studio-canvas/studio-app-href "https://studio.example/root" :artifacts)))
  (t/is (nil? (studio-canvas/studio-app-href "https://studio.example/root" :components)))
  (t/is (nil? (studio-canvas/studio-app-href "not a URL" :video))))

(t/deftest studio-app-location-links-preserve-the-current-penpot-workspace
  (let [href "https://penpot.example/#/workspace?team-id=team&file-id=file&studioMode=canvas"]
    (t/is (= "https://penpot.example/#/workspace?team-id=team&file-id=file&studioMode=pages"
             (studio-canvas/studio-app-location-href href :pages)))
    (t/is (= "https://penpot.example/#/workspace?team-id=team&file-id=file&studioMode=canvas"
             (studio-canvas/studio-app-location-href href :components)))))

(t/deftest canvas-bottom-inset-follows-visible-resizable-palettes
  (t/is (= 72 (studio-canvas/canvas-bottom-inset #{:colorpalette} 72)))
  (t/is (= 64 (studio-canvas/canvas-bottom-inset #{:textpalette} 64)))
  (t/is (= 0 (studio-canvas/canvas-bottom-inset #{} 54)))
  (t/is (= 0 (studio-canvas/canvas-bottom-inset #{:colorpalette :hide-ui} 72)))
  (t/is (= 0 (studio-canvas/canvas-bottom-inset #{:colorpalette} nil)))
  (t/is (= 0 (studio-canvas/canvas-bottom-inset #{:colorpalette} -12))))

(t/deftest assistance-route-selection-is-capability-bound
  (t/is (= "/v1/studio/canvas/qwen-assist"
           (:endpoint
            (studio-canvas/select-assist-route
             {:routes [{:capability "component.patch"
                        :available true
                        :endpoint "/v1/studio/canvas/qwen-patches"}
                       {:capability "studio.assist"
                        :available true
                        :endpoint "/v1/studio/canvas/qwen-assist"}]}))))
  (t/is (nil? (studio-canvas/select-assist-route
               {:routes [{:capability "studio.assist"
                          :available false
                          :endpoint "/v1/studio/canvas/qwen-assist"}]}))))

(t/deftest assistance-request-is-advisory-and-selection-bounded
  (let [request (studio-canvas/assist-request
                 "Make the selected transition faster"
                 (map #(str "Layer " %) (range 12)))]
    (t/is (= "studio.assist" (:capability request)))
    (t/is (= studio-canvas/assist-output-schema (:output_schema request)))
    (t/is (re-find #"Do not claim that you changed the document" (:prompt request)))
    (t/is (re-find #"Layer 7" (:prompt request)))
    (t/is (not (re-find #"Layer 8" (:prompt request))))))
