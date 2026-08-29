;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns app.main.data.sayhi.component-artifact
  "Resolve bounded SayHi source attachments from the selected Penpot object.

  Penpot owns selection and stored plugin data. Private SayHi capabilities own
  artifact compilation, rendering, and motion execution. This namespace only
  validates identity/revision-bound JSON before it crosses that adapter."
  (:require
   [app.common.types.component :as ctc]
   [clojure.string :as str]))

(def shared-namespace
  (keyword "shared" "io.sayhi.studio"))

(def canvas-runtime-root-id
  "sayhi-component-canvas-runtime-root")

(def ^:private web-object-schema "sayhi.web-object")
(def ^:private web-object-version "1.0")
(def ^:private web-object-provider "sayhi-studio")
(def ^:private artifact-schema "sayhi.portable-web-artifact")
(def ^:private artifact-version "1.0")
(def ^:private artifact-provider "portable-v2")
(def ^:private max-parent-depth 64)
(def ^:private max-component-descendants 4096)
(def ^:private max-artifact-bytes 2000000)
(def ^:private max-motion-bytes 1000000)
(def ^:private max-document-characters 1500000)
(def ^:private max-selectors 4096)

(def ^:private artifact-keys
  #{:schemaName
    :schemaVersion
    :provider
    :identity
    :root
    :document
    :selectors
    :componentGraph
    :designTokens
    :themes
    :fonts
    :assets
    :anatomy
    :motion
    :fidelity
    :motionRuntime})

(def ^:private identifier-pattern
  #"^(?!__proto__$|prototype$|constructor$)[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}$")

(defn- identifier?
  [value]
  (and (string? value)
       (boolean (re-matches identifier-pattern value))))

(defn- revision?
  [value]
  (and (string? value)
       (not (str/blank? value))
       (<= (count value) 256)))

(defn- json-bytes
  [value]
  (try
    (let [encoded (if (string? value)
                    value
                    (.stringify js/JSON (clj->js value)))]
      (when (string? encoded)
        (.-byteLength (.encode (js/TextEncoder.) encoded))))
    (catch :default _
      nil)))

(defn- decode-bounded
  [value maximum]
  (when-let [size (json-bytes value)]
    (when (<= size maximum)
      (try
        (cond
          (string? value)
          (js->clj (.parse js/JSON value) :keywordize-keys true)

          (map? value)
          (js->clj
           (.parse js/JSON (.stringify js/JSON (clj->js value)))
           :keywordize-keys true)

          :else
          nil)
        (catch :default _
          nil)))))

(defn- shape-data
  [shape]
  (get-in shape [:plugin-data shared-namespace]))

(defn- web-object-contract
  [shape]
  (let [contract  (decode-bounded (get (shape-data shape) "web-object") 16384)
        runtime   (:runtime contract)
        id        (:id contract)
        revision  (:revision contract)
        component (:component runtime)
        story     (:story runtime)]
    (when (and (map? contract)
               (= web-object-schema (:schemaName contract))
               (= web-object-version (:schemaVersion contract))
               (= web-object-provider (:provider runtime))
               (identifier? id)
               (revision? revision)
               (identifier? component)
               (identifier? story))
      {:schema-name web-object-schema
       :schema-version web-object-version
       :id id
       :revision revision
       :component-id component
       :story-id story})))

(defn- valid-root?
  [root]
  (and (map? root)
       (= #{:width :height} (set (keys root)))
       (every? #(and (number? %)
                     (js/Number.isFinite %)
                     (< 0 % 100001))
               [(:width root) (:height root)])))

(defn- valid-document?
  [document]
  (and (map? document)
       (= #{:mimeType :markup :styles} (set (keys document)))
       (= "text/html" (:mimeType document))
       (string? (:markup document))
       (string? (:styles document))
       (<= (+ (count (:markup document))
              (count (:styles document)))
           max-document-characters)))

(defn- valid-selectors?
  [selectors]
  (and (map? selectors)
       (<= (count selectors) max-selectors)
       (every? (fn [[source-id selector]]
                 (and (identifier? (name source-id))
                      (string? selector)
                      (not (str/blank? selector))
                      (<= (count selector) 512)))
               selectors)))

(defn- valid-bounded-vector?
  [value maximum]
  (and (vector? value) (<= (count value) maximum)))

(defn- portable-artifact
  [shape web-object]
  (let [artifact (decode-bounded
                  (get (shape-data shape) "portable-web-artifact")
                  max-artifact-bytes)
        identity (:identity artifact)]
    (when (and (map? artifact)
               (= artifact-keys (set (keys artifact)))
               (= artifact-schema (:schemaName artifact))
               (= artifact-version (:schemaVersion artifact))
               (= artifact-provider (:provider artifact))
               (map? identity)
               (= #{:id :componentId :storyId :revision :rootShapeId}
                  (set (keys identity)))
               (every? identifier?
                       [(:id identity)
                        (:componentId identity)
                        (:storyId identity)
                        (:rootShapeId identity)])
               (revision? (:revision identity))
               (= (:id web-object) (:id identity))
               (= (:component-id web-object) (:componentId identity))
               (= (:story-id web-object) (:storyId identity))
               (= (:revision web-object) (:revision identity))
               (valid-root? (:root artifact))
               (valid-document? (:document artifact))
               (valid-selectors? (:selectors artifact))
               (map? (:componentGraph artifact))
               (map? (:designTokens artifact))
               (valid-bounded-vector? (:themes artifact) 64)
               (valid-bounded-vector? (:fonts artifact) 256)
               (valid-bounded-vector? (:assets artifact) 1024)
               (map? (:anatomy artifact))
               (map? (:motion artifact))
               (map? (:fidelity artifact))
               (map? (:motionRuntime artifact)))
      artifact)))

(defn- motion-document
  [shape]
  (let [document (decode-bounded
                  (get (shape-data shape) "motion-dtcg")
                  max-motion-bytes)
        motion   (get-in document [:$extensions :io.sayhi.motion])]
    (when (and (map? document)
               (map? motion)
               (= "sayhi.motion" (:schemaName motion))
               (string? (:schemaVersion motion))
               (not (str/blank? (:schemaVersion motion))))
      document)))

(defn- component-source
  [shape]
  (when-let [web-object (web-object-contract shape)]
    {:shape-id (:id shape)
     :web-object web-object
     :artifact (portable-artifact shape web-object)
     :motion-document (motion-document shape)}))

(defn- resolve-from-shape
  [objects shape-id]
  (loop [shape-id shape-id
         visited #{}
         depth 0]
    (when (and shape-id
               (< depth max-parent-depth)
               (not (contains? visited shape-id)))
      (when-let [shape (get objects shape-id)]
        (or (component-source shape)
            (recur (:parent-id shape)
                   (conj visited shape-id)
                   (inc depth)))))))

(defn- resolve-from-component-root
  [objects shape-id]
  (when-let [root (get objects shape-id)]
    (when (ctc/instance-root? root)
      (loop [pending (into [] (:shapes root))
             visited #{}
             examined 0
             match nil]
        (cond
          (empty? pending)
          match

          (>= examined max-component-descendants)
          nil

          :else
          (let [current-id (peek pending)
                pending    (pop pending)]
            (if (contains? visited current-id)
              (recur pending visited examined match)
              (if-let [shape (get objects current-id)]
                (let [source (component-source shape)]
                  (if (and match source)
                    nil
                    (recur (into pending (:shapes shape))
                           (conj visited current-id)
                           (inc examined)
                           (or source match))))
                (recur pending
                       (conj visited current-id)
                       (inc examined)
                       match)))))))))

(defn resolve-selected-component
  "Resolve the first bounded SayHi component represented by `selected`.

  Ancestors are searched so nested shape selection works. Descendants are
  searched only below a real Penpot component root, and ambiguous component
  roots fail closed."
  [objects selected]
  (some #(or (resolve-from-shape objects %)
             (resolve-from-component-root objects %))
        selected))

(defn canvas-runtime-layout
  "Project a Penpot shape into viewport-local pixels.

  The host follows Penpot pan and zoom while the standalone web runtime keeps
  the component's authored viewport and is scaled as one unit."
  [shape vbox zoom]
  (let [bounds (or (:selrect shape) shape)
        values [(:x bounds)
                (:y bounds)
                (:width bounds)
                (:height bounds)
                (:x vbox)
                (:y vbox)
                zoom]]
    (when (and (every? #(and (number? %) (js/Number.isFinite %)) values)
               (pos? (:width bounds))
               (pos? (:height bounds))
               (pos? zoom))
      {:left (* (- (:x bounds) (:x vbox)) zoom)
       :top (* (- (:y bounds) (:y vbox)) zoom)
       :screen-width (* (:width bounds) zoom)
       :screen-height (* (:height bounds) zoom)
       :design-width (:width bounds)
       :design-height (:height bounds)
       :scale zoom})))
