;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns app.main.data.sayhi.web-materializer.motion-runtime
  "Compile backend-neutral SayHi Motion semantics into a bounded artifact
  playback plan. Concrete CSS, WAAPI, and GSAP adapters consume this plan;
  renderer mechanics never become the authored motion document."
  (:require
   [clojure.string :as str]))

(def schema-name "sayhi.artifact-motion-plan")
(def schema-version "0.2")

(def ^:private supported-drivers
  #{"css-transition" "css-animation" "waapi" "gsap"})
(def ^:private preview-adapter "web-animations-v1")
(def ^:private supported-motion-properties
  #{:x :y :xPercent :yPercent :scale :scaleX :scaleY :rotation
    :opacity :autoAlpha})
(def ^:private property-capabilities
  {:x "transform.translate"
   :y "transform.translate"
   :xPercent "transform.translate"
   :yPercent "transform.translate"
   :scale "transform.scale"
   :scaleX "transform.scale"
   :scaleY "transform.scale"
   :rotation "transform.rotate"
   :opacity "style.opacity"
   :autoAlpha "style.opacity-visibility"})
(def ^:private unsafe-segments
  #{"__proto__" "prototype" "constructor"})
(def ^:private token-reference
  #"^\{([A-Za-z0-9_$-]+(?:\.[A-Za-z0-9_$-]+)*)\}$")
(def ^:private max-reference-depth 32)

(defn- deep-merge
  [left right]
  (merge-with (fn [left-value right-value]
                (if (and (map? left-value) (map? right-value))
                  (deep-merge left-value right-value)
                  right-value))
              (or left {})
              (or right {})))

(defn- active-token-document
  [artifact]
  (let [package (get-in artifact [:designTokens :package])]
    (reduce
     (fn [document token-set]
       (if (true? (:active token-set))
         (deep-merge document (:document token-set))
         document))
     {}
     (:sets package))))

(defn- token-node
  [document path]
  (let [segments (str/split path #"\.")]
    (when-not (some unsafe-segments segments)
      (get-in document (mapv keyword segments)))))

(declare resolve-value)

(defn- resolve-token
  [document path resolving depth]
  (when (< depth max-reference-depth)
    (when-not (contains? resolving path)
      (let [node (token-node document path)]
        (when (and (map? node) (contains? node :$value))
          (resolve-value document
                         (:$value node)
                         (conj resolving path)
                         (inc depth)))))))

(defn- resolve-value
  [document value resolving depth]
  (cond
    (and (string? value) (re-matches token-reference value))
    (let [[_ path] (re-matches token-reference value)]
      (resolve-token document path resolving depth))

    (vector? value)
    (mapv #(resolve-value document % resolving depth) value)

    (map? value)
    (into {} (map (fn [[key item]]
                    [key (resolve-value document item resolving depth)])) value)

    :else value))

(defn- resolve-motion-value
  [document value]
  (resolve-value document value #{} 0))

(defn- finite-number?
  [value]
  (and (number? value) (js/Number.isFinite value)))

(defn- duration-seconds
  [document value]
  (let [resolved (resolve-motion-value document value)]
    (cond
      (finite-number? resolved) resolved

      (and (map? resolved) (finite-number? (:value resolved)))
      (case (:unit resolved)
        "ms" (/ (:value resolved) 1000)
        "s" (:value resolved)
        nil)

      :else nil)))

(defn- ease-css
  [document value]
  (let [resolved (resolve-motion-value document value)]
    (cond
      (string? resolved) resolved
      (and (vector? resolved)
           (= 4 (count resolved))
           (every? finite-number? resolved))
      (str "cubic-bezier(" (str/join ", " resolved) ")")
      :else nil)))

(defn- issue
  ([code message]
   (issue code message nil))
  ([code message track-id]
   (cond-> {:code code :message message}
     track-id (assoc :trackId track-id))))

(defn- target-selectors
  [artifact target-part]
  (->> (get-in artifact [:anatomy :parts target-part])
       (keep :selector)
       distinct
       vec))

(defn- coverflow-step?
  [step]
  (and (= "triggered" (:timelineKind step))
       (string? (:id step))
       (some? (re-find #"(?i)(?:coverflow|carousel)" (:id step)))))

(defn- descendant-part-targets
  [artifact item-shape-id active-parts]
  (mapv
   (fn [part]
     (let [entry (some
                  (fn [candidate]
                    (when (some #{item-shape-id}
                                (:ancestorShapeIds candidate))
                      candidate))
                  (get-in artifact [:anatomy :parts part]))]
       (when entry
         {:part part
          :shapeId (:shapeId entry)
          :selector (:selector entry)
          :active {:opacity 1}
          :inactive {:opacity 0}})))
   active-parts))

(defn- cycle-request
  [artifact response step]
  (let [declared       (:operation step)
        explicit?      (= "cycle" (:type declared))
        method-parts   (get-in artifact [:anatomy :parts "method"])
        inferred?      (and (coverflow-step? step)
                            (<= 3 (count method-parts)))
        requested-part (or (:targetPart declared) (:targetPart step))
        target-part    (if (and inferred?
                                (= "selector" requested-part)
                                (seq method-parts))
                         "method"
                         requested-part)]
    (when (or explicit? inferred?)
      (let [entries       (get-in artifact [:anatomy :parts target-part])
            slot-appearance (:slotAppearance declared)
            active-parts  (->> (:activeParts slot-appearance)
                               (filter string?)
                               distinct
                               vec)
            valid-entry?  (fn [{:keys [selector frame]}]
                            (and (string? selector)
                                 (map? frame)
                                 (every? finite-number?
                                         [(:x frame) (:y frame)
                                          (:width frame) (:height frame)
                                          (:rotation frame) (:opacity frame)])
                                 (pos? (:width frame))
                                 (pos? (:height frame))))
            usable        (filterv valid-entry? entries)
            ordered       (->> usable
                               (sort-by (juxt #(get-in % [:frame :x])
                                              #(get-in % [:frame :y])))
                               vec)
            slot-by-shape (into {}
                                (map-indexed
                                 (fn [index entry]
                                   [(:shapeId entry) index]))
                                ordered)
            center-slot   (when (seq ordered)
                            (->> ordered
                                 (map-indexed
                                  (fn [index entry]
                                    [index (* (get-in entry [:frame :width])
                                              (get-in entry [:frame :height]))]))
                                 (apply max-key second)
                                 first))
            direction     (or (:direction declared)
                              (get-in response [:match :direction])
                              (:direction response)
                              (:id response))
            delta         (or (:delta declared)
                              (case direction
                                "next" -1
                                "previous" 1
                                nil))
            appearance-by-shape
            (into {}
                  (map (fn [entry]
                         [(:shapeId entry)
                          (descendant-part-targets artifact
                                                   (:shapeId entry)
                                                   active-parts)]))
                  usable)
            appearance-missing?
            (and slot-appearance
                 (or (empty? active-parts)
                     (some (fn [entry]
                             (some nil?
                                   (get appearance-by-shape
                                        (:shapeId entry))))
                           usable)))
            issues        (cond-> []
                            (< (count usable) 3)
                            (conj (issue "motion_cycle_targets_missing"
                                         "A cycle transition needs at least three geometry-backed targets."
                                         (:id step)))

                            (not (and (number? delta)
                                      (js/Number.isInteger delta)
                                      (not (zero? delta))))
                            (conj (issue "motion_cycle_direction_invalid"
                                         "A cycle transition needs a next/previous direction or a non-zero integer delta."
                                         (:id step)))

                            appearance-missing?
                            (conj (issue "motion_cycle_appearance_targets_missing"
                                         "Every cycle item needs each declared active-slot appearance part."
                                         (:id step))))]
        {:targetPart target-part
         :issues issues
         :operation
         (when (empty? issues)
           {:type "cycle"
            :groupId (or (:groupId declared) target-part)
            :delta delta
            :centerSlot center-slot
            :slots (mapv
                    (fn [index entry]
                      (assoc (:frame entry)
                             :id index
                             :zIndex (if (= index center-slot) 3 1)))
                    (range)
                    ordered)
            :items (mapv
                    (fn [entry]
                      (cond->
                       {:shapeId (:shapeId entry)
                        :sourceId (:sourceId entry)
                        :selector (:selector entry)
                        :initialSlot (get slot-by-shape (:shapeId entry))}
                        (seq active-parts)
                        (assoc :appearanceTargets
                               (get appearance-by-shape (:shapeId entry)))))
                    usable)})}))))

(defn- motion-parts
  [artifact]
  (into {}
        (map (fn [[part entries]]
               [part (->> entries (keep :selector) distinct vec)]))
        (get-in artifact [:anatomy :parts])))

(defn- property-key
  [value]
  (cond
    (keyword? value) value
    (string? value) (keyword value)
    :else value))

(defn- normalize-property-map
  [value]
  (when (map? value)
    (into {}
          (map (fn [[property item]]
                 [(property-key property) item]))
          value)))

(defn- property-set
  [from to]
  (into #{} (concat (keys from) (keys to))))

(defn- invalid-property-values
  [from to]
  (->> [from to]
       (mapcat identity)
       (keep (fn [[property value]]
               (when (and (contains? supported-motion-properties property)
                          (not (finite-number? value)))
                 property)))
       distinct
       (sort-by name)
       vec))

(defn- required-capabilities
  [properties]
  (->> properties
       (keep property-capabilities)
       distinct
       sort
       vec))

(defn- normalize-step
  [artifact token-document response step]
  (let [track-id  (:id step)
        driver    (or (:driver step)
                      (get-in step [:execution :driver])
                      "waapi")
        cycle     (cycle-request artifact response step)
        target    (or (:targetPart cycle) (:targetPart step))
        selectors (if-let [operation (:operation cycle)]
                    (mapv :selector (:items operation))
                    (target-selectors artifact target))
        at        (duration-seconds token-document (or (:at step) 0))
        duration  (duration-seconds token-document (:duration step))
        stagger   (duration-seconds token-document (or (:stagger step) 0))
        ease      (ease-css token-document (or (:ease step) "linear"))
        from      (->> (:from step)
                       (resolve-motion-value token-document)
                       normalize-property-map)
        to        (->> (:to step)
                       (resolve-motion-value token-document)
                       normalize-property-map)
        properties (property-set from to)
        unsupported-properties (->> properties
                                    (remove supported-motion-properties)
                                    (map name)
                                    sort
                                    vec)
        invalid-values (invalid-property-values from to)
        capabilities (cond-> (required-capabilities properties)
                       (:operation cycle) (conj "layout.cycle")
                       (seq (get-in cycle [:operation :items 0 :appearanceTargets]))
                       (conj "style.slot-appearance"))
        problems  (cond-> (vec (:issues cycle))
                    (or (not (string? track-id)) (str/blank? track-id))
                    (conj (issue "motion_track_id_invalid"
                                 "A motion track needs a stable identifier."))

                    (not (contains? supported-drivers driver))
                    (conj (issue "motion_driver_unsupported"
                                 "The requested motion driver is not supported."
                                 track-id))

                    (or (not (string? target)) (str/blank? target))
                    (conj (issue "motion_target_invalid"
                                 "A motion track needs a semantic target part."
                                 track-id))

                    (empty? selectors)
                    (conj (issue "motion_target_unresolved"
                                 "The artifact has no shape selector for this motion part."
                                 track-id))

                    (and (nil? (:operation cycle))
                         (or (not (map? from)) (empty? from)
                             (not (map? to)) (empty? to)))
                    (conj (issue "motion_track_values_missing"
                                 "A motion track needs explicit from and to values."
                                 track-id))

                    (seq unsupported-properties)
                    (conj (issue "motion_property_unsupported"
                                 (str "The artifact preview cannot render motion properties: "
                                      (str/join ", " unsupported-properties) ".")
                                 track-id))

                    (seq invalid-values)
                    (conj (issue "motion_property_value_invalid"
                                 (str "The artifact preview needs finite numeric values for: "
                                      (str/join ", " (map name invalid-values)) ".")
                                 track-id))

                    (or (not (finite-number? at)) (neg? (or at -1))
                        (not (finite-number? duration)) (not (pos? (or duration 0)))
                        (not (finite-number? stagger)) (neg? (or stagger -1)))
                    (conj (issue "motion_track_timing_invalid"
                                 "A motion track has unresolved or invalid timing."
                                 track-id))

                    (not (string? ease))
                    (conj (issue "motion_track_ease_invalid"
                                 "A motion track has an unresolved easing value."
                                 track-id)))]
    (if (seq problems)
      {:issues problems}
      {:step {:id track-id
              :driver driver
              :requestedDriver driver
              :previewAdapter preview-adapter
              :translationMode (if (= driver "waapi")
                                 "native"
                                 "semantic-preview")
              :requiredCapabilities capabilities
              :targetPart target
              :timelineKind (or (:timelineKind step) "timed")
              :operation (:operation cycle)
              :at at
              :duration duration
              :ease ease
              :stagger stagger
              :from from
              :to to
              :selectors selectors}})))

(defn- normalize-response
  [artifact token-document response]
  (let [compiled (->> (:steps response)
                      (remove #(false? (:enabled %)))
                      (mapv #(normalize-step artifact token-document response %)))]
    {:response {:id (:id response)
                :label (or (:label response) (:id response))
                :match (or (:match response)
                           {:direction (or (:direction response)
                                           (:id response))})
                :steps (into [] (keep :step) compiled)}
     :issues (into [] (mapcat :issues) compiled)}))

(defn compile-plan
  "Compile one portable artifact's first SayHi Motion program. Invalid or
  unsupported tracks are reported and omitted so shadow evaluation cannot
  compromise the established projection runtime."
  [artifact]
  (let [motion         (:motion artifact)
        extension      (get-in motion [:$extensions :io.sayhi.motion])
        program        (first (:programs extension))
        token-document (active-token-document artifact)]
    (if-not (map? program)
      {:schemaName schema-name
       :schemaVersion schema-version
       :status "unavailable"
       :trackCount 0
       :responses []
       :issues [(issue "motion_program_missing"
                       "The artifact has no SayHi Motion program.")]}
      (let [compiled  (mapv #(normalize-response artifact token-document %)
                            (:responses program))
            responses (mapv :response compiled)
            issues    (into [] (mapcat :issues) compiled)
            track-count (reduce + (map #(count (:steps %)) responses))]
        {:schemaName schema-name
         :schemaVersion schema-version
         :programId (:id program)
         :event (or (get-in program [:trigger :event])
                    (:event program))
         :reducedMotion (or (:reducedMotion program) "skip")
         :status (if (seq issues) "partial" "ready")
         :trackCount track-count
         :partCount (count (motion-parts artifact))
         :parts (motion-parts artifact)
         :requestedDrivers (->> responses
                                (mapcat :steps)
                                (map :requestedDriver)
                                distinct
                                sort
                                vec)
         :previewAdapters (if (pos? track-count) [preview-adapter] [])
         :translationModes (->> responses
                                (mapcat :steps)
                                (map :translationMode)
                                distinct
                                sort
                                vec)
         :responses responses
         :issues issues}))))

(defn playable?
  [plan]
  (and (= schema-name (:schemaName plan))
       (pos? (:trackCount plan 0))))
