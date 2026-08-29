;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns frontend-tests.data.sayhi-component-artifact-test
  (:require
   [app.main.data.sayhi.component-artifact :as component-artifact]
   [cljs.test :as t :include-macros true]))

(def ^:private shared-namespace
  (keyword "shared" "io.sayhi.studio"))

(defn- web-object
  []
  {:schemaName "sayhi.web-object"
   :schemaVersion "1.0"
   :id "sayhi.verify"
   :revision "revision.7"
   :runtime {:provider "sayhi-studio"
             :component "sayhi.verification-method-selector"
             :story "story.verify-methods-standalone"}})

(defn- artifact
  []
  {:schemaName "sayhi.portable-web-artifact"
   :schemaVersion "1.0"
   :provider "portable-v2"
   :identity {:id "sayhi.verify"
              :componentId "sayhi.verification-method-selector"
              :storyId "story.verify-methods-standalone"
              :revision "revision.7"
              :rootShapeId "root-shape"}
   :root {:width 580 :height 500}
   :document {:mimeType "text/html"
              :markup "<main class=\"verify\"></main>"
              :styles ".verify{position:relative}"}
   :selectors {"verify.root" ".verify"}
   :componentGraph {:shapeCount 1 :sourceIds ["verify.root"]}
   :designTokens {:format "DTCG 2025.10"}
   :themes []
   :fonts []
   :assets []
   :anatomy {:parts {}}
   :motion {:schemaName "sayhi.motion" :schemaVersion "0.5.0"}
   :fidelity {:status "candidate" :issues []}
   :motionRuntime {:status "source-ready"}})

(defn- motion-document
  []
  {:$extensions
   {:io.sayhi.motion
    {:schemaName "sayhi.motion"
     :schemaVersion "0.5.0"
     :revision 0
     :programs []}}})

(defn- encoded
  [value]
  (.stringify js/JSON (clj->js value)))

(defn- shape
  [id parent-id data]
  {:id id
   :parent-id parent-id
   :plugin-data
   {shared-namespace
    (into {} (map (fn [[key value]] [key (encoded value)])) data)}})

(t/deftest resolves-the-bounded-artifact-and-motion-source-from-an-ancestor
  (let [objects {:child {:id :child :parent-id :root}
                 :root  (shape :root nil
                               {"web-object" (web-object)
                                "portable-web-artifact" (artifact)
                                "motion-dtcg" (motion-document)})}
        result  (component-artifact/resolve-selected-component objects [:child])]
    (t/is (= "sayhi.verification-method-selector"
             (get-in result [:web-object :component-id])))
    (t/is (= "revision.7" (get-in result [:artifact :identity :revision])))
    (t/is (= "sayhi.motion"
             (get-in result
                     [:motion-document :$extensions :io.sayhi.motion :schemaName])))
    (t/is (= :root (:shape-id result)))))

(t/deftest resolves-a-contract-below-a-selected-component-root
  (let [objects {:component {:id :component
                             :component-root true
                             :shapes [:projection]}
                 :projection (shape :projection :component
                                    {"web-object" (web-object)
                                     "portable-web-artifact" (artifact)
                                     "motion-dtcg" (motion-document)})}]
    (t/is (= :projection
             (:shape-id
              (component-artifact/resolve-selected-component
               objects
               [:component]))))))

(t/deftest does-not-search-down-from-an-arbitrary-frame
  (let [objects {:frame {:id :frame :type :frame :shapes [:projection]}
                 :projection (shape :projection :frame
                                    {"web-object" (web-object)
                                     "portable-web-artifact" (artifact)})}]
    (t/is (nil?
           (component-artifact/resolve-selected-component objects [:frame])))))

(t/deftest rejects-artifacts-that-do-not-match-the-selected-source-revision
  (let [wrong-artifact (assoc-in (artifact) [:identity :revision] "revision.8")
        objects        {:root (shape :root nil
                                     {"web-object" (web-object)
                                      "portable-web-artifact" wrong-artifact
                                      "motion-dtcg" (motion-document)})}
        result         (component-artifact/resolve-selected-component objects [:root])]
    (t/is (some? result))
    (t/is (nil? (:artifact result)))
    (t/is (some? (:motion-document result)))))

(t/deftest malformed-and-unbounded-plugin-data-fails-closed
  (let [bad-shape (fn [data]
                    {:id :bad
                     :plugin-data {shared-namespace data}})]
    (t/is (nil?
           (component-artifact/resolve-selected-component
            {:bad (bad-shape {"web-object" "{"})}
            [:bad])))
    (t/is (nil?
           (:artifact
            (component-artifact/resolve-selected-component
             {:bad (bad-shape
                    {"web-object" (encoded (web-object))
                     "portable-web-artifact" (apply str (repeat 2000001 "x"))})}
             [:bad]))))))

(t/deftest projects-a-component-into-viewport-local-pixels
  (t/is (= {:left 30
            :top 60
            :screen-width 240
            :screen-height 120
            :design-width 160
            :design-height 80
            :scale 1.5}
           (component-artifact/canvas-runtime-layout
            {:selrect {:x 30 :y 50 :width 160 :height 80}}
            {:x 10 :y 10}
            1.5)))
  (t/is (nil? (component-artifact/canvas-runtime-layout
               {:x 0 :y 0 :width 0 :height 10}
               {:x 0 :y 0}
               1))))
