;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns app.main.data.sayhi.motion-studio
  "Bounded native host state and selection projection for SayHi Motion Studio."
  (:require
   [app.common.files.changes-builder :as pcb]
   [app.config :as cf]
   [app.main.data.changes :as dch]
   [app.main.data.helpers :as dsh]
   [app.main.data.sayhi.web-preview :as web-preview]
   [app.main.data.workspace.undo :as dwu]
   [app.util.json :as json]
   [beicon.v2.core :as rx]
   [potok.v2.core :as ptk]))

(def schema-name "io.sayhi.penpot.motion-host")
(def schema-version "1.0")
(def canvas-preview-root-id "sayhi-motion-canvas-preview-root")
(def ^:private shared-namespace
  (keyword "shared" "io.sayhi.studio"))
(def ^:private legacy-motion-schema "sayhi.penpot.motion")
(def ^:private legacy-motion-version "0.1.0")
(def ^:private dtcg-motion-key "motion-dtcg")
(def ^:private legacy-motion-key "motion-contract")
(def ^:private sayhi-motion-extension :io.sayhi.motion)
(def ^:private sayhi-motion-version "0.5.0")
(def ^:private max-motion-document-characters 1000000)
(def ^:private max-motion-responses 32)
(def ^:private max-motion-tracks 128)
(def ^:private max-pending-preview-messages 32)
(def ^:private max-parent-depth 64)
(def ^:private base-host-capabilities ["selection.read"])

(defn- identifier
  [value]
  (cond
    (keyword? value) (name value)
    (some? value) (str value)
    :else "unknown"))

(defn- ensure-trailing-slash
  [value]
  (if (.endsWith value "/") value (str value "/")))

(defn native-mode?
  [mode studio-uri]
  (and (contains? #{"native-v1" "native-v2"} mode)
       (string? studio-uri)
       (try
         (contains? #{"http:" "https:"} (.-protocol (js/URL. studio-uri)))
         (catch :default _
           false))))

(defn enabled?
  []
  (native-mode? cf/sayhi-motion-studio-mode cf/sayhi-motion-studio-uri))

(defn native-v2-mode?
  [mode]
  (= "native-v2" mode))

(defn canvas-preview-surface?
  [surface]
  (= "canvas" surface))

(defn- finite-number?
  [value]
  (and (number? value)
       (js/Number.isFinite value)))

(defn canvas-preview-layout
  "Project a Penpot shape into viewport-local pixels without changing the
  embedded component's design viewport. The wrapper follows pan and zoom;
  the iframe retains its authored width and height and is scaled as a unit."
  [shape vbox zoom]
  (let [bounds (or (:selrect shape) shape)
        x      (:x bounds)
        y      (:y bounds)
        width  (:width bounds)
        height (:height bounds)
        vbox-x (:x vbox)
        vbox-y (:y vbox)]
    (when (and (every? finite-number? [x y width height vbox-x vbox-y zoom])
               (pos? width)
               (pos? height)
               (pos? zoom))
      {:left          (* (- x vbox-x) zoom)
       :top           (* (- y vbox-y) zoom)
       :screen-width  (* width zoom)
       :screen-height (* height zoom)
       :design-width  width
       :design-height height
       :scale         zoom})))

(defn frame-ready-for-origin?
  "Only allow a cross-origin frame message after that exact origin is ready."
  [expected-origin ready-origin]
  (and (string? expected-origin)
       (not-empty expected-origin)
       (= expected-origin ready-origin)))

(defn same-cross-origin-window?
  "Compare WindowProxy objects without invoking ClojureScript value equality.

  Cross-origin Window objects reject the protocol-property reads performed by
  `=`. JavaScript reference identity is both sufficient and required when
  validating a MessageEvent source against an iframe's contentWindow."
  [left right]
  (identical? left right))

(defn enqueue-preview-message
  "Bound messages produced before the portable component preview is ready."
  [messages message]
  (->> (conj messages message)
       (take-last max-pending-preview-messages)
       vec))

(defn host-href
  [studio-uri]
  (when (native-mode? "native-v1" studio-uri)
    (try
      (str (js/URL. "studio/penpot-motion-host/" (ensure-trailing-slash studio-uri)))
      (catch :default _
        nil))))

(defn selected-web-object
  [objects selected]
  (web-preview/resolve-selected-web-object objects selected))

(defn eligible-selection?
  [objects selected]
  (some? (selected-web-object objects selected)))

(defn- decode-motion-document
  [value]
  (when (and (string? value)
             (<= (count value) max-motion-document-characters))
    (try
      (json/decode value)
      (catch :default _
        nil))))

(defn- legacy-motion-document?
  [document]
  (and (map? document)
       (= legacy-motion-schema (:schemaName document))
       (= legacy-motion-version (:schemaVersion document))
       (string? (:id document))
       (string? (:label document))
       (map? (:parts document))
       (vector? (:ambient document))
       (<= (count (:ambient document)) max-motion-tracks)
       (vector? (:responses document))
       (<= (count (:responses document)) max-motion-responses)
       (every? #(and (map? %)
                     (string? (:id %))
                     (vector? (:steps %))
                     (<= (count (:steps %)) max-motion-tracks))
               (:responses document))))

(defn- sayhi-motion-extension?
  [extension]
  (and (map? extension)
       (= sayhi-motion-version (:schemaVersion extension))
       (nat-int? (:revision extension))
       (string? (:tokenRevision extension))
       (<= 1 (count (:tokenRevision extension)) 256)
       (vector? (:programs extension))
       (<= (count (:programs extension)) max-motion-responses)
       (every? #(or (legacy-motion-document? %)
                    (and (map? %)
                         (string? (:id %))
                         (vector? (:responses %))
                         (<= (count (:responses %)) max-motion-responses)
                         (every? (fn [response]
                                   (and (map? response)
                                        (string? (:id response))
                                        (vector? (:steps response))
                                        (<= (count (:steps response)) max-motion-tracks)))
                                 (:responses %))))
               (:programs extension))))

(defn- dtcg-motion-document?
  [document]
  (and (map? document)
       (sayhi-motion-extension?
        (get-in document [:$extensions sayhi-motion-extension]))))

(defn- normalize-legacy-coverflow-program
  [program]
  (if (= "verify-selection-response" (:id program))
    (-> program
        (assoc-in [:parts :selector]
                  (or (get-in program [:parts :selector])
                      ["Verification-methods"]))
        (update :responses
                (fn [responses]
                  (mapv
                   (fn [response]
                     (update response
                             :steps
                             (fn [steps]
                               (mapv
                                (fn [step]
                                  (if (and (string? (:id step))
                                           (re-find #"(?i)(?:coverflow|carousel)"
                                                    (:id step)))
                                    (-> step
                                        (assoc :targetPart "method")
                                        (update :operation
                                                #(or % {:type "cycle"
                                                        :groupId "method"})))
                                    step))
                                steps))))
                   responses))))
    program))

(defn- normalize-motion-compatibility
  [document]
  (update-in document
             [:$extensions sayhi-motion-extension :programs]
             (fn [programs]
               (mapv normalize-legacy-coverflow-program programs))))

(defn- legacy->dtcg-motion
  [document]
  (normalize-motion-compatibility
   {:$description "Migrated SayHi component motion"
    :$extensions
    {sayhi-motion-extension
     {:schemaVersion sayhi-motion-version
      :revision 0
      :tokenRevision "legacy.0"
      :programs [document]}}}))

(defn- decode-stored-motion
  [shape]
  (let [data        (get-in shape [:plugin-data shared-namespace])
        dtcg        (decode-motion-document (get data dtcg-motion-key))
        legacy      (decode-motion-document (get data legacy-motion-key))]
    (cond
      (dtcg-motion-document? dtcg) (normalize-motion-compatibility dtcg)
      (legacy-motion-document? legacy) (legacy->dtcg-motion legacy)
      :else nil)))

(defn- resolve-stored-motion
  "Find motion metadata on the portable projection or its component wrapper.

  Penpot can retain shared plugin data on either level depending on whether a
  component was freshly imported, repaired in place, or selected through an
  instance root. Keep the lookup within the selected object's bounded ancestor
  chain so an unrelated board cannot donate a motion program."
  [objects shape-id]
  (loop [shape-id shape-id
         visited  #{}
         depth    0]
    (when (and shape-id
               (< depth max-parent-depth)
               (not (contains? visited shape-id)))
      (when-let [shape (get objects shape-id)]
        (if-let [document (decode-stored-motion shape)]
          {:shape-id shape-id
           :document document}
          (recur (:parent-id shape)
                 (conj visited shape-id)
                 (inc depth)))))))

(defn motion-revision
  [document]
  (get-in document [:$extensions sayhi-motion-extension :revision]))

(defn prepare-motion-write
  "Merge one optimistic motion edit into the current DTCG document.

  The current document owns tokens and all non-SayHi extensions. Motion Studio
  may replace only `io.sayhi.motion`; the host assigns the next revision so a
  stale or malicious client cannot skip versions or erase neighboring data."
  [current-document expected-revision next-document]
  (let [current-revision (motion-revision current-document)
        next-extension   (get-in next-document
                                 [:$extensions sayhi-motion-extension])]
    (when-not (= current-revision expected-revision)
      (throw (ex-info "The motion document revision changed."
                      {:code "motion_revision_conflict"
                       :currentRevision current-revision})))
    (when-not (sayhi-motion-extension? next-extension)
      (throw (ex-info "The updated io.sayhi.motion extension is invalid."
                      {:code "motion_document_invalid"})))
    (assoc-in current-document
              [:$extensions sayhi-motion-extension]
              (assoc next-extension :revision (inc current-revision)))))

(defn motion-document
  "Resolve a bounded DTCG motion document from the selected component.

  Current imports read their native DTCG document. Older component imports are
  projected through an explicit, non-mutating v0.5 compatibility envelope so
  the host can use one contract while persistence remains opt-in."
  [objects selected]
  (when-let [web-object (selected-web-object objects selected)]
    (when-let [{:keys [document]}
               (resolve-stored-motion objects (:shape-id web-object))]
      (when (dtcg-motion-document? document)
        {:componentId (:component-id web-object)
         :revision (motion-revision document)
         :document document}))))

(defn- host-capabilities
  [objects selected preview-enabled? materialization]
  (cond-> base-host-capabilities
    (some? (motion-document objects selected))
    (into (cond-> ["motion.read" "motion.write" "history.transaction"]
            preview-enabled? (into ["preview.control" "anatomy.highlight"])))

    (some? materialization)
    (conj "artifact.summary")))

(defn host-context
  [{:keys [file-id page-id objects selected shapes theme locale preview-enabled? materialization]}]
  (let [web-object (selected-web-object objects selected)
        component  (:component-id web-object)
        items      (->> selected
                        (sort-by str)
                        (keep (fn [id]
                                (when-let [shape (or (get objects id)
                                                     (some #(when (= id (:id %)) %) shapes))]
                                  (cond-> {:id (identifier id)
                                           :name (or (not-empty (:name shape)) "Unnamed shape")
                                           :type (identifier (or (:type shape) :shape))}
                                    component (assoc :componentId component)))))
                        (take 32)
                        (into []))]
    {:fileId (identifier file-id)
     :pageId (identifier page-id)
     :selection items
     :capabilities (host-capabilities objects selected preview-enabled? materialization)
     :theme (if (= "dark" theme) "dark" "light")
     :locale (or (not-empty locale) "en")
     :webMaterialization materialization}))

(defn motion-preview-href
  [studio-uri objects web-object canvas-preview?]
  (when web-object
    (let [render-state (some-> (web-preview/resolve-viewer-render-state objects web-object)
                               (json/encode))]
      (when-let [href (web-preview/preview-href
                       studio-uri
                       {:component-id (:component-id web-object)
                        :story-id (:story-id web-object)
                        :render-state render-state})]
        (let [url (js/URL. href)]
          (.set (.-searchParams url) "motionStudio" "1")
          (.set (.-searchParams url) "motionEngine" "v2")
          (when canvas-preview?
            (.set (.-searchParams url) "embed" "canvas"))
          (.-href url))))))

(defn context-message
  [context]
  {:schema schema-name
   :schemaVersion schema-version
   :type "host.context"
   :payload {:context context}})

(defn motion-document-message
  [payload]
  {:schema schema-name
   :schemaVersion schema-version
   :type "host.motion.document"
   :payload payload})

(defn preview-simulation
  "Build the semantic component event used to exercise one motion response.

  The real portable component receives this event, changes its own state, and
  emits the runtime motion event. Motion Studio therefore previews the actual
  interaction instead of playing a detached visual approximation."
  [motion-payload response-id]
  (let [document (:document motion-payload)
        program  (or (get-in document [:$extensions sayhi-motion-extension :programs 0])
                     (when (legacy-motion-document? document) document))
        response (some #(when (= response-id (:id %)) %)
                       (:responses program))
        event-id (or (get-in program [:trigger :event]) "selection.change")
        detail   (or (:match response)
                     (when response
                       {:direction (or (:direction response) (:id response))}))]
    (when (and (string? event-id) response (map? detail))
      {:event event-id
       :detail detail})))

(defn- notify-write-result
  [callback result]
  (ptk/reify ::notify-write-result
    ptk/EffectEvent
    (effect [_ _ _]
      (callback result))))

(defn write-motion-document
  "Persist one revision-checked motion edit as a normal Penpot undo entry."
  [{:keys [file-id page-id shape-id component-id revision document on-result]}]
  (ptk/reify ::write-motion-document
    ptk/WatchEvent
    (watch [it state _]
      (try
        (let [file-data       (dsh/lookup-file-data state file-id)
              objects         (dsh/lookup-page-objects state file-id page-id)
              web-object      (selected-web-object objects [shape-id])
              stored-motion   (resolve-stored-motion objects (:shape-id web-object))
              target-shape-id (:shape-id stored-motion)
              current         (:document stored-motion)]
          (when-not (and web-object
                         target-shape-id
                         (= component-id (:component-id web-object)))
            (throw (ex-info "The selected component changed before the motion write."
                            {:code "motion_selection_changed"})))
          (let [updated         (prepare-motion-write current revision document)
                next-revision   (motion-revision updated)
                changes         (-> (pcb/empty-changes it)
                                    (pcb/with-file-data file-data)
                                    (assoc :file-id file-id)
                                    (pcb/set-plugin-data
                                     :shape
                                     target-shape-id
                                     page-id
                                     shared-namespace
                                     dtcg-motion-key
                                     (json/encode updated)))
                undo-id         (js/Symbol)]
            (rx/of
             (dwu/start-undo-transaction undo-id)
             (dch/commit-changes changes)
             (dwu/commit-undo-transaction undo-id)
             (notify-write-result on-result
                                  {:ok true
                                   :revision next-revision
                                   :document updated}))))
        (catch :default error
          (rx/of
           (notify-write-result
            on-result
            {:ok false
             :code (or (:code (ex-data error)) "motion_write_failed")
             :message (or (.-message error) "Penpot could not save the motion document.")
             :currentRevision (:currentRevision (ex-data error))})))))))

(defn toggle
  []
  (ptk/reify ::toggle
    ptk/UpdateEvent
    (update [_ state]
      (let [open?    (true? (get-in state [:workspace-local :sayhi-motion-studio :open?]))
            objects  (dsh/lookup-page-objects state)
            selected (get-in state [:workspace-local :selected])]
        (if (and (enabled?)
                 (or open? (eligible-selection? objects selected)))
          (-> state
              (assoc-in [:workspace-local :sayhi-motion-studio :open?] (not open?))
              (assoc-in [:workspace-local :sayhi-motion-studio :collapsed?] false))
          state)))))

(defn close
  []
  (ptk/reify ::close
    ptk/UpdateEvent
    (update [_ state]
      (assoc-in state [:workspace-local :sayhi-motion-studio :open?] false))))

(defn toggle-collapsed
  []
  (ptk/reify ::toggle-collapsed
    ptk/UpdateEvent
    (update [_ state]
      (update-in state [:workspace-local :sayhi-motion-studio :collapsed?] not))))
