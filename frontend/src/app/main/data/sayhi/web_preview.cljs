;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns app.main.data.sayhi.web-preview
  "Resolution and URL construction for SayHi portable web objects.

  This namespace deliberately accepts identifiers, never an object-provided
  URL. The configured Studio origin remains the only authority allowed to
  provide executable preview content."
  (:require
   [app.common.types.component :as ctc]
   [app.common.types.text :as txt]
   [app.util.json :as json]))

(def ^:private shared-namespace
  (keyword "shared" "io.sayhi.studio"))

(def ^:private portable-schema-name "sayhi.web-object")
(def ^:private portable-schema-version "1.0")
(def ^:private portable-provider "sayhi-studio")
(def ^:private legacy-projection "editable-vector/v1")
(def ^:private native-penpot-projection "native-penpot/v2")
(def ^:private projection-schema-name "sayhi.component-projection")
(def ^:private projection-schema-version "1.0")
(def ^:private render-state-schema-name "sayhi.component-render-state")
(def ^:private render-state-schema-version "1.0")
(def ^:private max-parent-depth 64)
(def ^:private max-component-descendants 4096)
(def ^:private max-bindings 64)

(def ^:private identifier-pattern
  #"^[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}$")

(defn- valid-identifier?
  [value]
  (and (string? value)
       (boolean (re-matches identifier-pattern value))))

(defn- valid-revision?
  [value]
  (or (nil? value)
      (and (string? value)
           (<= (count value) 256))))

(defn- decode-contract
  [value]
  (cond
    (map? value)
    value

    (string? value)
    (try
      (json/decode value)
      (catch :default _
        nil))

    :else
    nil))

(defn- explicit-contract
  [shape]
  (let [data      (get-in shape [:plugin-data shared-namespace])
        contract  (decode-contract (get data "web-object"))
        runtime   (:runtime contract)
        id        (:id contract)
        revision  (:revision contract)
        component (:component runtime)
        story     (:story runtime)]
    (when (and (= portable-schema-name (:schemaName contract))
               (= portable-schema-version (:schemaVersion contract))
               (= portable-provider (:provider runtime))
               (valid-identifier? id)
               (valid-revision? revision)
               (valid-identifier? component)
               (valid-identifier? story))
      {:schema-name portable-schema-name
       :schema-version portable-schema-version
       :id id
       :revision revision
       :component-id component
       :story-id story
       :source :contract
       :shape-id (:id shape)})))

(defn- legacy-contract
  [shape]
  (let [data      (get-in shape [:plugin-data shared-namespace])
        component (get data "component-id")
        revision  (get data "component-version")
        story     (get data "story-id")]
    (when (and (contains? #{legacy-projection native-penpot-projection}
                          (get data "projection"))
               (valid-identifier? component)
               (valid-revision? revision)
               (valid-identifier? story))
      {:schema-name portable-schema-name
       :schema-version portable-schema-version
       :id component
       :revision revision
       :component-id component
       :story-id story
       :source :legacy-projection
       :shape-id (:id shape)})))

(defn- shape-contract
  [shape]
  (or (explicit-contract shape)
      (legacy-contract shape)))

(defn- resolve-from-shape
  [objects shape-id]
  (loop [shape-id shape-id
         visited #{}
         depth 0]
    (when (and shape-id
               (< depth max-parent-depth)
               (not (contains? visited shape-id)))
      (when-let [shape (get objects shape-id)]
        (or (shape-contract shape)
            (recur (:parent-id shape)
                   (conj visited shape-id)
                   (inc depth)))))))

(defn- resolve-from-component-root
  "Resolve the one portable projection nested below a Penpot component root.

  Penpot wraps the editable shape used to create a component in an instance
  root. Plugin data remains on that editable projection, so a normal single
  click selects the wrapper while a double click selects the contract-bearing
  child. Search downward only from a real component root and fail closed when
  its bounded subtree contains more than one portable object."
  [objects shape-id]
  (when-let [root (get objects shape-id)]
    (when (ctc/instance-root? root)
      (loop [pending  (into [] (:shapes root))
             visited  #{}
             examined 0
             match    nil]
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
                (let [contract (shape-contract shape)]
                  (if (and match contract)
                    nil
                    (recur (into pending (:shapes shape))
                           (conj visited current-id)
                           (inc examined)
                           (or contract match))))
                (recur pending
                       (conj visited current-id)
                       (inc examined)
                       match)))))))))

(defn resolve-selected-web-object
  "Return the first trusted portable web object represented by `selected`.

  Selection can point at a child inside an imported component or at Penpot's
  generated component root; users should not need to enter a component merely
  to expose its web preview and motion tools."
  [objects selected]
  (some #(or (resolve-from-shape objects %)
             (resolve-from-component-root objects %))
        selected))

(defn- contract-identity
  [contract]
  (select-keys contract
               [:schema-name
                :schema-version
                :id
                :revision
                :component-id
                :story-id]))

(defn- resolve-unique-web-object
  [shapes]
  (let [contracts (->> shapes
                       (keep shape-contract)
                       (reduce
                        (fn [result contract]
                          (assoc result (contract-identity contract) contract))
                        {}))]
    (when (= 1 (count contracts))
      (-> contracts vals first))))

(defn- shape-tree
  [objects root-id]
  (loop [pending (when root-id [root-id])
         visited #{}
         result []]
    (if-let [shape-id (peek pending)]
      (if (contains? visited shape-id)
        (recur (pop pending) visited result)
        (if-let [shape (get objects shape-id)]
          (recur (into (pop pending) (:shapes shape))
                 (conj visited shape-id)
                 (conj result shape))
          (recur (pop pending) (conj visited shape-id) result)))
      result)))

(defn- render-projection
  [shape]
  (let [data       (get-in shape [:plugin-data shared-namespace])
        serialized (get data "render-projection")
        projection (when (and (string? serialized)
                              (<= (count serialized) 16384))
                     (decode-contract serialized))]
    (when (and (= projection-schema-name (:schemaName projection))
               (= projection-schema-version (:schemaVersion projection))
               (map? (:environment projection))
               (vector? (:bindings projection))
               (<= (count (:bindings projection)) max-bindings))
      projection)))

(defn- legacy-render-projection
  "Migration adapter for SayHi projections imported before render-state v1.

  New imports carry their own projection contract. This bounded adapter keeps
  already-placed first-party library instances faithful until the owner elects
  to replace them with the newer, more completely bound projection."
  [web-object]
  (case (:component-id web-object)
    "sayhi.mounted-sidebar"
    {:schemaName projection-schema-name
     :schemaVersion projection-schema-version
     :environment {:theme "dark" :wireframe false :softFill false}
     :coverage {:status "mapped"}
     :bindings [{:id "panel-face" :layer "Panel-face" :source "fill-color"
                 :target {:kind "token" :name "--sayhi-mounted-sidebar-face"}}
                {:id "panel-radius" :layer "Panel-face" :source "corner-radius"
                 :target {:kind "token" :name "--sayhi-mounted-sidebar-radius"}}
                {:id "search-visible" :layer "Search" :source "visible"
                 :target {:kind "prop" :name "searchable"}}
                {:id "create-visible" :layer "Create-action" :source "visible"
                 :target {:kind "prop" :name "showCreateAction"}}]}

    "sayhi.verification-method-selector"
    {:schemaName projection-schema-name
     :schemaVersion projection-schema-version
     :environment {:theme "light" :wireframe false :softFill false}
     :coverage {:status "mapped"}
     :bindings [{:id "panel-face" :layer "Panel-face" :source "fill-color"
                 :target {:kind "token" :name "--sayhi-verify-panel"}}
                {:id "panel-radius" :layer "Panel-face" :source "corner-radius"
                 :target {:kind "token" :name "--sayhi-verify-panel-radius"}}]}
    nil))

(defn- first-layer
  [shapes layer-name]
  (first (filter #(= layer-name (:name %)) shapes)))

(defn- length-value
  [values]
  (when (every? #(and (number? %) (js/Number.isFinite %)) values)
    (let [values (mapv #(str % "px") values)]
      (if (apply = values)
        (first values)
        (apply str (interpose " " values))))))

(defn- binding-value
  [shape source]
  (case source
    "fill-color" (-> shape :fills first :fill-color)
    "stroke-color" (-> shape :strokes first :stroke-color)
    "corner-radius" (length-value [(:r1 shape) (:r2 shape) (:r3 shape) (:r4 shape)])
    "opacity" (when (number? (:opacity shape)) (:opacity shape))
    "visible" (not (true? (:hidden shape)))
    "text-content" (when (= :text (:type shape))
                     (let [value (txt/content->text (:content shape))]
                       (when (and (string? value)
                                  (<= (count value) 1024))
                         value)))
    nil))

(defn- safe-binding?
  [binding]
  (let [target (:target binding)]
    (and (map? binding)
         (valid-identifier? (:id binding))
         (string? (:layer binding))
         (<= (count (:layer binding)) 256)
         (contains? #{"fill-color" "stroke-color" "corner-radius" "opacity" "visible" "text-content"} (:source binding))
         (map? target)
         (contains? #{"token" "prop"} (:kind target))
         (string? (:name target))
         (if (= "token" (:kind target))
           (boolean (re-matches #"^--[a-z0-9-]{1,253}$" (:name target)))
           (valid-identifier? (:name target))))))

(defn resolve-viewer-render-state
  "Project the current editable Penpot anatomy into a bounded portable state.

  Only bindings declared by the imported component projection are evaluated.
  Studio independently validates every resulting token and prop against the
  portable component manifest before rendering it."
  [objects web-object]
  (when-let [root (get objects (:shape-id web-object))]
    (when-let [projection (or (render-projection root)
                              (legacy-render-projection web-object))]
      (let [shapes   (shape-tree objects (:id root))
            bindings (filterv safe-binding? (:bindings projection))
            result   (reduce
                      (fn [result binding]
                        (let [shape  (first-layer shapes (:layer binding))
                              value  (when shape (binding-value shape (:source binding)))
                              target (:target binding)]
                          (if (nil? value)
                            (update result :missing conj (:id binding))
                            (-> result
                                (assoc-in [(if (= "token" (:kind target)) :tokens :props)
                                           (:name target)]
                                          value)
                                (update :applied conj (:id binding))))))
                      {:tokens {} :props {} :applied [] :missing []}
                      bindings)
            missing  (:missing result)
            status   (if (seq missing) "partial" (or (get-in projection [:coverage :status]) "mapped"))]
        {:schemaName render-state-schema-name
         :schemaVersion render-state-schema-version
         :component {:id (:component-id web-object)
                     :story (:story-id web-object)
                     :revision (:revision web-object)}
         :environment (select-keys (:environment projection) [:theme :wireframe :softFill])
         :tokens (:tokens result)
         :props (:props result)
         :fidelity {:status status
                    :applied (:applied result)
                    :missing missing}}))))

(defn resolve-viewer-web-object
  "Resolve the portable object represented by a real Penpot Play action.

  An explicit selection wins. A frame-scoped Play action can resolve one
  portable object from that frame. The workspace-level Play button has no
  frame or selection, so a page containing exactly one distinct trusted
  portable object is also safe to run. Pages with multiple different portable
  objects remain native Penpot prototypes instead of choosing arbitrarily."
  [objects selected frame-id]
  (or (resolve-selected-web-object objects selected)
      (when frame-id
        (resolve-unique-web-object (shape-tree objects frame-id)))
      (resolve-unique-web-object (vals objects))))

(defn preview-href
  "Build a Studio standalone preview URL from trusted configuration and ids.

  The portable object cannot supply an href. Only an HTTP(S) Studio base URI
  supplied by the Penpot deployment is accepted."
  [studio-uri {:keys [component-id story-id render-state]}]
  (when (and (string? studio-uri)
             (valid-identifier? component-id)
             (valid-identifier? story-id))
    (try
      (let [base     (js/URL. studio-uri)
            protocol (.-protocol base)]
        (when (or (= protocol "https:")
                  (= protocol "http:"))
          (let [preview (js/URL. "studio/components/player.html" base)
                params  (.-searchParams preview)]
            (.set params "component" component-id)
            (.set params "story" story-id)
            (when (and (string? render-state)
                       (<= (count render-state) 16384))
              (.set params "renderState" render-state))
            (.-href preview))))
      (catch :default _
        nil))))
