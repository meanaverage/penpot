;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns app.main.data.sayhi.motion-host.document-preview
  "Temporary document preview carried by the frozen v1 recipe envelope.
  No store, persistence or undo authority. The artifact remains canonical."
  (:require
   [app.main.data.sayhi.artifact-motion-playback :as playback]
   [app.main.data.sayhi.motion-studio :as motion-studio]
   [app.main.data.sayhi.web-materializer.motion-runtime :as motion-runtime]))

(def format-name "sayhi.motion.document-preview")

(defn document-recipe?
  [recipe]
  (= format-name (:format recipe)))

(defn prepare-plan
  "Validate and compile against host-owned geometry/tokens, without changing
  the artifact or canonical revision. A partial render is not a valid preview."
  [artifact current identity recipe]
  (when-not (and (= #{:format :formatVersion :identity :responseId :document}
                    (set (keys recipe)))
                 (= format-name (:format recipe))
                 (= "1.0" (:formatVersion recipe))
                 (= identity (:identity recipe)))
    (throw (ex-info "The preview format or selected object changed."
                    {:code "motion_preview_context_changed"})))
  (let [canonical (:document current)
        proposed  (:document recipe)
        ;; Reuse the native write validator, but undo its proposed revision
        ;; increment. This is a value transform, never a write event.
        document  (if (nil? proposed)
                    canonical
                    (assoc-in (motion-studio/prepare-motion-write
                               canonical (:revision current) proposed)
                              [:$extensions :io.sayhi.motion :revision]
                              (:revision current)))
        plan      (motion-runtime/compile-plan (assoc artifact :motion document))]
    (when (and proposed (not= document proposed))
      (throw (ex-info "Preview cannot change canonical tokens or other extensions."
                      {:code "motion_preview_document_invalid"})))
    (when-not (and (= "ready" (:status plan))
                   (empty? (:issues plan))
                   (some #(= (:responseId recipe) (:id %)) (:responses plan)))
      (throw (ex-info "The proposed document cannot be fully rendered by this preview."
                      {:code "motion_preview_plan_invalid"})))
    plan))

(defn replace-controller!
  "Dispose old animations before mounting a validated plan. Buffer initial
  player state so the first response cannot replace the requested selection.
  Callers acknowledge only after this function succeeds."
  [document prior plan response-id on-state]
  (let [ready?      (atom false)
        controller* (atom nil)]
    (when prior ((:dispose prior)))
    (try
      (let [controller (playback/create-runtime
                        document plan
                        {:on-state #(when @ready? (on-state %))})]
        (reset! controller* controller)
        (when-not ((:select-response controller) response-id)
          (throw (ex-info "The preview response is unavailable."
                          {:code "motion_preview_response_invalid"})))
        (reset! ready? true)
        (on-state ((:get-state controller)))
        controller)
      (catch :default error
        (when-let [controller @controller*] ((:dispose controller)))
        ;; A browser can throw while creating a track, before create-runtime
        ;; returns a controller. This iframe contains only our artifact player.
        (when (.-getAnimations document)
          (doseq [animation (array-seq (.getAnimations document))]
            (.cancel animation)))
        (throw error)))))
