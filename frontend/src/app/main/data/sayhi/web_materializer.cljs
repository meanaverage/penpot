;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns app.main.data.sayhi.web-materializer
  "Single switch point for the stable SayHi web materialization contract."
  (:require
   [app.main.data.sayhi.web-materializer.contract :as contract]
   [app.main.data.sayhi.web-materializer.portable-v2 :as portable-v2]
   [app.main.data.sayhi.web-preview :as projection-v1]
   [app.util.json :as json]))

(defn- projection
  [studio-uri objects web-object]
  (let [render-state (projection-v1/resolve-viewer-render-state objects web-object)]
    {:provider contract/projection-provider
     :href (projection-v1/preview-href
            studio-uri
            (cond-> web-object
              render-state (assoc :render-state (json/encode render-state))))
     :renderState render-state
     :fidelity (or (:fidelity render-state)
                   {:status "runtime-default" :applied [] :missing []})}))

(defn- compare-providers
  [projection artifact artifact-error]
  (let [projection-component (get-in projection [:renderState :component :id])
        artifact-component   (get-in artifact [:identity :componentId])]
    {:status (cond
               artifact-error "candidate-failed"
               (and artifact
                    (or (nil? projection-component)
                        (= projection-component artifact-component))) "comparable"
               :else "identity-mismatch")
     :sameComponent (or (nil? projection-component)
                        (= projection-component artifact-component))
     :projectionFidelity (get-in projection [:fidelity :status])
     :artifactFidelity (get-in artifact [:fidelity :status])
     :artifactIssues (get-in artifact [:fidelity :issues])
     :artifactError artifact-error}))

(defn- materialize-portable
  [objects web-object]
  (try
    {:artifact (portable-v2/materialize objects web-object)}
    (catch :default _
      ;; Shadow evaluation must never take the proven projection path down.
      ;; Keep the public error bounded and implementation-neutral.
      {:error {:code "portable_web_materialization_failed"
               :message "The portable web artifact could not be materialized."}})))

(defn materialize
  [{:keys [mode studio-uri objects web-object]}]
  (when web-object
    (let [mode       (contract/normalize-mode mode)
          projected  (projection studio-uri objects web-object)
          candidate  (when (not= mode contract/projection-provider)
                       (materialize-portable objects web-object))
          artifact   (:artifact candidate)
          artifact-error (:error candidate)
          active     (if (= mode contract/portable-provider)
                       contract/portable-provider
                       contract/projection-provider)]
      {:schemaName contract/materialization-schema-name
       :schemaVersion contract/materialization-schema-version
       :mode mode
       :activeProvider active
       :projection projected
       :artifact artifact
       :artifactError artifact-error
       :comparison (when (= mode contract/shadow-provider)
                     (compare-providers projected artifact artifact-error))})))

(defn previewable?
  [materialization]
  (case (:activeProvider materialization)
    "projection-v1" (some? (get-in materialization [:projection :href]))
    "portable-v2" (contract/portable-artifact? (:artifact materialization))
    false))

(defn host-summary
  "Expose bounded materialization evidence to Studio without shipping generated
  markup or CSS across the host bridge."
  [materialization]
  (when materialization
    (cond-> {:schemaName (:schemaName materialization)
             :schemaVersion (:schemaVersion materialization)
             :mode (:mode materialization)
             :activeProvider (:activeProvider materialization)}
      (:comparison materialization)
      (assoc :comparison (:comparison materialization))

      (:artifactError materialization)
      (assoc :artifactError (:artifactError materialization))

      (:artifact materialization)
      (assoc :artifact
             {:schemaName (get-in materialization [:artifact :schemaName])
              :schemaVersion (get-in materialization [:artifact :schemaVersion])
              :identity (get-in materialization [:artifact :identity])
              :root (get-in materialization [:artifact :root])
              :fidelity (get-in materialization [:artifact :fidelity])
              :motionRuntime
              (select-keys (get-in materialization [:artifact :motionRuntime])
                           [:schemaName :schemaVersion :programId :status
                            :trackCount :partCount :requestedDrivers
                            :previewAdapters :translationModes :issues])}))))

(defn cache-key
  [web-object]
  (str (random-uuid) "-" (hash [(:id web-object) (:revision web-object)])))

(defn cache-artifact!
  [web-object materialization]
  (when-let [artifact (:artifact materialization)]
    (let [key (cache-key web-object)]
      (contract/cache-artifact! key artifact))))
