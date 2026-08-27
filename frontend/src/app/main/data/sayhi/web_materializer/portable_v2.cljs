;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns app.main.data.sayhi.web-materializer.portable-v2
  "Structured Penpot graph -> portable web artifact materializer.

  This is deliberately separate from the existing Studio projection. It uses
  Penpot's own HTML/CSS/SVG code generators, then preserves DTCG, anatomy,
  assets, font references, stable shape selectors, and SayHi Motion as one
  versioned artifact rather than passing raw Inspector snippets between tools."
  (:require
   [app.common.files.helpers :as cfh]
   [app.common.geom.shapes :as gsh]
   [app.common.types.shape-tree :as ctst]
   [app.main.data.sayhi.web-materializer.contract :as contract]
   [app.main.data.sayhi.web-materializer.motion-runtime :as motion-runtime]
   [app.main.fonts :as fonts]
   [app.util.code-beautify :as cb]
   [app.util.code-gen :as code-gen]
   [app.util.code-gen.common :as code-common]
   [app.util.json :as json]))

(def ^:private shared-namespace
  (keyword "shared" "io.sayhi.studio"))
(def ^:private max-shapes 4096)
(def ^:private max-metadata-characters 1000000)

(defn- decode-metadata
  [value]
  (when (and (string? value)
             (<= (count value) max-metadata-characters))
    (try
      (json/decode value)
      (catch :default _
        nil))))

(defn- shared-data
  [shape]
  (get-in shape [:plugin-data shared-namespace]))

(defn- shape-ids
  [objects root-id]
  (->> [root-id]
       (cfh/selected-with-children objects)
       (take max-shapes)
       (ctst/sort-z-index objects)
       vec))

(defn- shape-fonts
  [shapes]
  (->> shapes
       (keep :content)
       (mapcat fonts/get-content-fonts)
       (map #(select-keys % [:font-id :font-variant-id :font-weight :font-style]))
       distinct
       (sort-by (juxt :font-id :font-variant-id))
       vec))

(defn- shape-assets
  [shapes]
  (->> shapes
       (keep
        (fn [shape]
          (when-let [asset (or (:metadata shape)
                               (:fill-image shape)
                               (-> shape :fills first :fill-image))]
            {:shapeId (str (:id shape))
             :asset (select-keys asset [:id :name :width :height :mtype :keep-aspect-ratio])})))
       vec))

(defn- shape-selectors
  [shapes]
  (into {}
        (map (fn [shape]
               [(str (:id shape)) (str "." (code-common/shape->selector shape))]))
        shapes))

(defn- ancestor-shape-ids
  [shape-by-id shape]
  (loop [parent-id (:parent-id shape)
         depth 0
         result []]
    (if-let [parent (and (< depth 64) (get shape-by-id parent-id))]
      (recur (:parent-id parent)
             (inc depth)
             (conj result (str (:id parent))))
      result)))

(defn- anatomy-index
  [shapes]
  (let [shape-by-id (into {} (map (juxt :id identity)) shapes)]
    (reduce
     (fn [result shape]
       (if-let [part (not-empty (get (shared-data shape) "motion-part"))]
         (let [bounds (or (:selrect shape) shape)]
           (update result part (fnil conj [])
                   (cond->
                    {:shapeId (str (:id shape))
                     :parentShapeId (some-> (:parent-id shape) str)
                     :ancestorShapeIds (ancestor-shape-ids shape-by-id shape)
                     :selector (str "." (code-common/shape->selector shape))
                     :name (:name shape)
                     :frame {:x (:x bounds)
                             :y (:y bounds)
                             :width (:width bounds)
                             :height (:height bounds)
                             :rotation (or (:rotation shape) 0)
                             :opacity (or (:opacity shape) 1)}}
                     (not-empty (get (shared-data shape) "source-id"))
                     (assoc :sourceId (get (shared-data shape) "source-id")))))
         result))
     {}
     shapes)))

(defn- applied-token-index
  [shapes]
  (into {}
        (keep
         (fn [shape]
           (when (seq (:applied-tokens shape))
             [(str (:id shape))
              (into {}
                    (map (fn [[property token-name]]
                           [(name property) token-name]))
                    (:applied-tokens shape))])))
        shapes))

(defn- issue
  [code message]
  {:code code :message message})

(defn- attach-motion-runtime
  [artifact]
  (assoc artifact :motionRuntime (motion-runtime/compile-plan artifact)))

(defn materialize
  [objects web-object]
  (when-let [root (get objects (:shape-id web-object))]
    (let [root-shape (gsh/translate-to-frame root root)
          ids        (shape-ids objects (:id root))
          candidates (mapv (fn [id]
                             (if (= id (:id root))
                               root-shape
                               (get objects id)))
                           ids)
          all-found? (every? some? candidates)
          shapes     (filterv some? candidates)
          markup     (code-gen/generate-formatted-markup-code objects "html" [root-shape])
          styles     (-> (code-gen/generate-style-code objects "css" [root-shape] shapes)
                         (cb/format-code "css"))
          data       (shared-data root)
          dtcg       (decode-metadata (get data "dtcg-package"))
          motion     (decode-metadata (get data "motion-dtcg"))
          anatomy    (or (decode-metadata (get data "anatomy")) {})
          indexed    (anatomy-index shapes)
          issues     (cond-> []
                       (not all-found?)
                       (conj (issue "shape_tree_incomplete"
                                    "One or more component descendants could not be materialized."))

                       (nil? dtcg)
                       (conj (issue "dtcg_package_missing"
                                    "The component has no readable canonical DTCG package."))

                       (nil? motion)
                       (conj (issue "motion_document_missing"
                                    "The component has no readable SayHi Motion document."))

                       (empty? indexed)
                       (conj (issue "motion_anatomy_unindexed"
                                    "No component layers expose a motion-part binding.")))
          bounds     (or (:selrect root) root)]
      (attach-motion-runtime
       {:schemaName contract/schema-name
        :schemaVersion contract/schema-version
        :provider contract/portable-provider
        :identity {:id (:id web-object)
                   :componentId (:component-id web-object)
                   :storyId (:story-id web-object)
                   :revision (:revision web-object)
                   :rootShapeId (str (:id root))}
        :root {:width (:width bounds)
               :height (:height bounds)}
        :document {:mimeType "text/html"
                   :markup markup
                   :styles styles}
        :selectors (shape-selectors shapes)
        :componentGraph {:shapeCount (count shapes)
                         :sourceIds (into {}
                                          (keep
                                           (fn [shape]
                                             (when-let [source-id (not-empty (get (shared-data shape) "source-id"))]
                                               [(str (:id shape)) source-id])))
                                          shapes)}
        :designTokens {:format "DTCG"
                       :package dtcg
                       :appliedByShape (applied-token-index shapes)}
        :themes (or (:themes dtcg) [])
        :fonts (shape-fonts shapes)
        :assets (shape-assets shapes)
        :anatomy {:contract anatomy
                  :parts indexed}
        :motion motion
        :fidelity {:status (if (seq issues) "partial" "candidate")
                   :issues issues
                   :shapeCount (count shapes)
                   :selectorCount (count (shape-selectors shapes))}}))))
