;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns frontend-tests.data.sayhi-web-preview-test
  (:require
   [app.main.data.sayhi.web-preview :as web-preview]
   [cljs.test :as t :include-macros true]))

(def ^:private shared-namespace
  (keyword "shared" "io.sayhi.studio"))

(defn- shape
  [id parent-id data]
  {:id id
   :parent-id parent-id
   :plugin-data {shared-namespace data}})

(defn- text-content
  [value]
  {:type "root"
   :children [{:type "paragraph"
               :children [{:text value}]}]})

(t/deftest resolves-an-explicit-portable-web-object-contract
  (let [objects {:child (shape :child :root {})
                 :root  (shape :root nil
                               {"web-object"
                                (js/JSON.stringify
                                 #js {:schemaName "sayhi.web-object"
                                      :schemaVersion "1.0"
                                      :id "sayhi.verify"
                                      :revision "revision-7"
                                      :runtime #js {:provider "sayhi-studio"
                                                    :component "sayhi.verification-method-selector"
                                                    :story "story.verify-methods-standalone"}})})}
        result  (web-preview/resolve-selected-web-object objects [:child])]
    (t/is (= {:schema-name "sayhi.web-object"
              :schema-version "1.0"
              :id "sayhi.verify"
              :revision "revision-7"
              :component-id "sayhi.verification-method-selector"
              :story-id "story.verify-methods-standalone"
              :source :contract
              :shape-id :root}
             result))))

(t/deftest resolves-the-existing-sayhi-vector-projection
  (let [objects {:verify (shape :verify nil
                                {"component-id" "sayhi.verification-method-selector"
                                 "component-version" "0.1.3"
                                 "story-id" "story.verify-methods-standalone"
                                 "projection" "editable-vector/v1"})}]
    (t/is (= {:schema-name "sayhi.web-object"
              :schema-version "1.0"
              :id "sayhi.verification-method-selector"
              :revision "0.1.3"
              :component-id "sayhi.verification-method-selector"
              :story-id "story.verify-methods-standalone"
              :source :legacy-projection
              :shape-id :verify}
             (web-preview/resolve-selected-web-object objects [:verify])))))

(t/deftest resolves-a-native-penpot-v2-import-before-the-explicit-contract-migration
  (let [objects {:verify (shape :verify nil
                                {"component-id" "sayhi.verification-method-selector"
                                 "component-version" "0.2.0-native-dtcg"
                                 "story-id" "story.verify-methods-standalone"
                                 "projection" "native-penpot/v2"})}]
    (t/is (= {:schema-name "sayhi.web-object"
              :schema-version "1.0"
              :id "sayhi.verification-method-selector"
              :revision "0.2.0-native-dtcg"
              :component-id "sayhi.verification-method-selector"
              :story-id "story.verify-methods-standalone"
              :source :legacy-projection
              :shape-id :verify}
             (web-preview/resolve-selected-web-object objects [:verify])))))

(t/deftest resolves-the-projection-below-a-selected-penpot-component-root
  (let [objects {:component-root {:id :component-root
                                  :name "SayHi Verify"
                                  :type :frame
                                  :component-id :penpot-component
                                  :component-root true
                                  :shapes [:verify]}
                 :verify (shape :verify :component-root
                                {"component-id" "sayhi.verification-method-selector"
                                 "component-version" "0.1.3"
                                 "story-id" "story.verify-methods-standalone"
                                 "projection" "editable-vector/v1"})}]
    (t/is (= :verify
             (:shape-id
              (web-preview/resolve-selected-web-object objects [:component-root]))))))

(t/deftest does-not-search-down-from-an-arbitrary-frame
  (let [objects {:frame {:id :frame
                         :type :frame
                         :shapes [:verify]}
                 :verify (shape :verify :frame
                                {"component-id" "sayhi.verification-method-selector"
                                 "component-version" "0.1.3"
                                 "story-id" "story.verify-methods-standalone"
                                 "projection" "editable-vector/v1"})}]
    (t/is (nil? (web-preview/resolve-selected-web-object objects [:frame])))))

(t/deftest resolves-a-unique-page-web-object-when-play-has-no-selection
  (let [objects {:plain  (shape :plain nil {})
                 :verify (shape :verify nil
                                {"component-id" "sayhi.verification-method-selector"
                                 "component-version" "0.1.3"
                                 "story-id" "story.verify-methods-standalone"
                                 "projection" "editable-vector/v1"})}]
    (t/is (= "sayhi.verification-method-selector"
             (:component-id
              (web-preview/resolve-viewer-web-object objects #{} nil))))))

(t/deftest frame-play-resolves-the-web-object-inside-that-frame
  (let [objects {:frame  {:id :frame
                          :type :frame
                          :shapes [:verify :plain]}
                 :plain  (shape :plain :frame {})
                 :verify (shape :verify :frame
                                {"component-id" "sayhi.verification-method-selector"
                                 "component-version" "0.1.3"
                                 "story-id" "story.verify-methods-standalone"
                                 "projection" "editable-vector/v1"})
                 :other  (shape :other nil
                                {"component-id" "sayhi.mounted-sidebar"
                                 "component-version" "0.2.0"
                                 "story-id" "story.workspace-history"
                                 "projection" "editable-vector/v1"})}]
    (t/is (= "sayhi.verification-method-selector"
             (:component-id
              (web-preview/resolve-viewer-web-object
               objects #{} :frame))))))

(t/deftest selected-web-object-wins-on-a-mixed-page
  (let [objects {:verify (shape :verify nil
                                {"component-id" "sayhi.verification-method-selector"
                                 "component-version" "0.1.3"
                                 "story-id" "story.verify-methods-standalone"
                                 "projection" "editable-vector/v1"})
                 :sidebar (shape :sidebar nil
                                 {"component-id" "sayhi.mounted-sidebar"
                                  "component-version" "0.2.0"
                                  "story-id" "story.workspace-history"
                                  "projection" "editable-vector/v1"})}]
    (t/is (= "sayhi.mounted-sidebar"
             (:component-id
              (web-preview/resolve-viewer-web-object
               objects [:sidebar] nil))))))

(t/deftest mixed-page-without-a-selection-stays-native
  (let [objects {:verify (shape :verify nil
                                {"component-id" "sayhi.verification-method-selector"
                                 "component-version" "0.1.3"
                                 "story-id" "story.verify-methods-standalone"
                                 "projection" "editable-vector/v1"})
                 :sidebar (shape :sidebar nil
                                 {"component-id" "sayhi.mounted-sidebar"
                                  "component-version" "0.2.0"
                                  "story-id" "story.workspace-history"
                                  "projection" "editable-vector/v1"})}]
    (t/is (nil?
           (web-preview/resolve-viewer-web-object objects #{} nil)))))

(t/deftest rejects-malformed-or-untrusted-runtime-data
  (let [invalid-json {:bad (shape :bad nil {"web-object" "{"})}
        arbitrary-url {:bad (shape :bad nil
                                   {"web-object"
                                    (js/JSON.stringify
                                     #js {:schemaName "sayhi.web-object"
                                          :schemaVersion "1.0"
                                          :runtime #js {:provider "arbitrary-url"
                                                        :href "https://attacker.example/"}})})}
        invalid-id {:bad (shape :bad nil
                                {"component-id" "sayhi verify?<script>"
                                 "story-id" "story.verify"
                                 "projection" "editable-vector/v1"})}]
    (t/is (nil? (web-preview/resolve-selected-web-object invalid-json [:bad])))
    (t/is (nil? (web-preview/resolve-selected-web-object arbitrary-url [:bad])))
    (t/is (nil? (web-preview/resolve-selected-web-object invalid-id [:bad])))))

(t/deftest builds-preview-only-from-the-configured-studio-origin
  (let [web-object {:component-id "sayhi.verification-method-selector"
                    :story-id "story.verify-methods-standalone"}
        href       (web-preview/preview-href "https://studio.example/base/" web-object)
        url        (js/URL. href)]
    (t/is (= "https://studio.example" (.-origin url)))
    (t/is (= "/base/studio/components/player.html" (.-pathname url)))
    (t/is (nil? (.get (.-searchParams url) "standalone")))
    (t/is (= "sayhi.verification-method-selector" (.get (.-searchParams url) "component")))
    (t/is (= "story.verify-methods-standalone" (.get (.-searchParams url) "story")))
    (t/is (nil? (web-preview/preview-href "javascript:alert(1)" web-object)))))

(t/deftest projects-current-penpot-anatomy-into-a-bounded-runtime-state
  (let [projection (js/JSON.stringify
                    #js {:schemaName "sayhi.component-projection"
                         :schemaVersion "1.0"
                         :environment #js {:theme "dark"}
                         :coverage #js {:status "mapped"}
                         :bindings #js [#js {:id "panel-face"
                                             :layer "Panel-face"
                                             :source "fill-color"
                                             :target #js {:kind "token" :name "--sayhi-mounted-sidebar-face"}}
                                        #js {:id "panel-radius"
                                             :layer "Panel-face"
                                             :source "corner-radius"
                                             :target #js {:kind "token" :name "--sayhi-mounted-sidebar-radius"}}
                                        #js {:id "search-visible"
                                             :layer "Search"
                                             :source "visible"
                                             :target #js {:kind "prop" :name "searchable"}}
                                        #js {:id "search-placeholder"
                                             :layer "Search-label"
                                             :source "text-content"
                                             :target #js {:kind "prop" :name "searchPlaceholder"}}]})
        objects {:root  (assoc (shape :root nil
                                      {"component-id" "sayhi.mounted-sidebar"
                                       "component-version" "0.2.0"
                                       "story-id" "story.workspace-history"
                                       "projection" "editable-vector/v1"
                                       "render-projection" projection})
                               :shapes [:panel :search :search-label])
                 :panel {:id :panel :parent-id :root :name "Panel-face"
                         :fills [{:fill-color "#101113"}]
                         :r1 21 :r2 21 :r3 21 :r4 21}
                 :search {:id :search :parent-id :root :name "Search" :hidden true}
                 :search-label {:id :search-label :parent-id :root :name "Search-label"
                                :type :text :content (text-content "Find conversations")}}
        object  (web-preview/resolve-viewer-web-object objects #{:root} nil)
        state   (web-preview/resolve-viewer-render-state objects object)]
    (t/is (= "sayhi.component-render-state" (:schemaName state)))
    (t/is (= {:theme "dark"} (:environment state)))
    (t/is (= {"--sayhi-mounted-sidebar-face" "#101113"
              "--sayhi-mounted-sidebar-radius" "21px"}
             (:tokens state)))
    (t/is (= {"searchable" false
              "searchPlaceholder" "Find conversations"}
             (:props state)))
    (t/is (= {:status "mapped"
              :applied ["panel-face" "panel-radius" "search-visible" "search-placeholder"]
              :missing []}
             (:fidelity state)))))

(t/deftest carries-only-bounded-render-state-through-the-studio-url
  (let [render-state "{\"schemaName\":\"sayhi.component-render-state\"}"
        href (web-preview/preview-href
              "https://studio.example/"
              {:component-id "sayhi.mounted-sidebar"
               :story-id "story.workspace-history"
               :render-state render-state})
        url (js/URL. href)]
    (t/is (= render-state (.get (.-searchParams url) "renderState")))))

(t/deftest keeps-already-placed-first-party-projections-on-a-versioned-migration-adapter
  (let [objects {:root (assoc (shape :root nil
                                     {"component-id" "sayhi.mounted-sidebar"
                                      "component-version" "0.1.2"
                                      "story-id" "story.workspace-history"
                                      "projection" "editable-vector/v1"})
                              :shapes [:panel])
                 :panel {:id :panel :parent-id :root :name "Panel-face"
                         :fills [{:fill-color "#15161b"}]
                         :r1 17 :r2 17 :r3 17 :r4 17}}
        object (web-preview/resolve-viewer-web-object objects #{:root} nil)
        state (web-preview/resolve-viewer-render-state objects object)]
    (t/is (= {:theme "dark" :wireframe false :softFill false}
             (:environment state)))
    (t/is (= "#15161b" (get-in state [:tokens "--sayhi-mounted-sidebar-face"])))
    (t/is (= "17px" (get-in state [:tokens "--sayhi-mounted-sidebar-radius"])))
    (t/is (= ["search-visible" "create-visible"]
             (get-in state [:fidelity :missing])))))
