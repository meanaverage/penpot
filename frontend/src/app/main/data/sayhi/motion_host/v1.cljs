;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns app.main.data.sayhi.motion-host.v1
  "Fail-closed validation for messages sent from Motion Studio to Penpot."
  (:require
   [clojure.set :as set]
   [clojure.string :as str]))

(def schema-name "io.sayhi.penpot.motion-host")
(def schema-version "1.0")

(def ^:private max-envelope-bytes 1050000)
(def ^:private max-document-bytes 1000000)
(def ^:private max-part-ids 64)
(def ^:private inbound-message-types
  #{"studio.ready"
    "studio.context.request"
    "studio.motion.read"
    "studio.motion.write"
    "studio.preview.recipe"
    "studio.preview.command"
    "studio.anatomy.highlight"})
(def ^:private preview-commands
  #{"prepare" "play" "pause" "seek" "reset" "simulate"})
(def ^:private public-preview-statuses
  #{"empty" "loading" "ready" "playing" "paused" "reversing" "finished" "reduced"})

(defn- invalid!
  [code message]
  (throw (ex-info message {:code code})))

(defn- bounded-text?
  [value maximum]
  (and (string? value)
       (not (str/blank? value))
       (<= (count value) maximum)))

(defn- identifier?
  [value]
  (and (string? value)
       (some? (re-matches
               #"^(?!__proto__$|prototype$|constructor$)[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$"
               value))))

(defn- safe-revision?
  [value]
  (and (js/Number.isSafeInteger value)
       (not (neg? value))))

(defn- finite-range?
  [value minimum maximum]
  (and (number? value)
       (js/Number.isFinite value)
       (<= minimum value maximum)))

(defn- record!
  [value label]
  (when-not (map? value)
    (invalid! "penpot_motion_host_payload_invalid"
              (str label " must be an object.")))
  value)

(defn- exact-keys!
  [value required optional]
  (let [actual  (set (keys value))
        required (set required)
        allowed (set/union required (set optional))]
    (when-let [unknown (first (set/difference actual allowed))]
      (invalid! "penpot_motion_host_property_unknown"
                (str "Unknown motion host property " (name unknown) ".")))
    (when-let [missing (first (set/difference required actual))]
      (invalid! "penpot_motion_host_property_missing"
                (str "Missing motion host property " (name missing) "."))))
  value)

(defn- json-bytes
  [value]
  (try
    (let [encoded (.stringify js/JSON (clj->js value))]
      (when-not (string? encoded)
        (invalid! "penpot_motion_host_json_invalid"
                  "Motion host data must be serializable JSON."))
      (.-byteLength (.encode (js/TextEncoder.) encoded)))
    (catch :default error
      (if (ex-data error)
        (throw error)
        (invalid! "penpot_motion_host_json_invalid"
                  "Motion host data must be serializable JSON.")))))

(defn- decode-envelope
  [value]
  (try
    (let [encoded (.stringify js/JSON value)]
      (when-not (string? encoded)
        (invalid! "penpot_motion_host_message_invalid"
                  "Motion host message must be an object."))
      (when (> (.-byteLength (.encode (js/TextEncoder.) encoded))
               max-envelope-bytes)
        (invalid! "penpot_motion_host_message_too_large"
                  "Motion host message exceeds the bounded host envelope."))
      (js->clj (.parse js/JSON encoded) :keywordize-keys true))
    (catch :default error
      (if (ex-data error)
        (throw error)
        (invalid! "penpot_motion_host_message_invalid"
                  "Motion host message must be serializable JSON.")))))

(defn- assert-id!
  [value label]
  (when-not (identifier? value)
    (invalid! "penpot_motion_host_identifier_invalid"
              (str label " identifier is invalid.")))
  value)

(defn- assert-revision!
  [value]
  (when-not (safe-revision? value)
    (invalid! "penpot_motion_host_revision_invalid"
              "Motion host revision must be a non-negative safe integer."))
  value)

(defn- assert-document!
  [value label]
  (record! value label)
  (when (> (json-bytes value) max-document-bytes)
    (invalid! "penpot_motion_host_document_too_large"
              (str label " exceeds the one-megabyte host boundary.")))
  value)

(defn- validate-ready
  [payload]
  (exact-keys! payload [:clientVersion] [])
  (when-not (bounded-text? (:clientVersion payload) 64)
    (invalid! "penpot_motion_host_payload_invalid"
              "Motion Studio client version is invalid."))
  payload)

(defn- validate-context-request
  [payload]
  (exact-keys! payload [] [])
  payload)

(defn- validate-motion-read
  [payload]
  (exact-keys! payload [:componentId] [])
  (assert-id! (:componentId payload) "Component")
  payload)

(defn- validate-motion-write
  [payload]
  (exact-keys! payload [:componentId :revision :document :label] [])
  (assert-id! (:componentId payload) "Component")
  (assert-revision! (:revision payload))
  (assert-document! (:document payload) "Motion document")
  (when-not (bounded-text? (:label payload) 120)
    (invalid! "penpot_motion_host_payload_invalid"
              "Motion write label is invalid."))
  payload)

(defn- validate-preview-recipe
  [payload]
  (exact-keys! payload [:componentId :revision :recipe] [])
  (assert-id! (:componentId payload) "Component")
  (assert-revision! (:revision payload))
  (assert-document! (:recipe payload) "Preview recipe")
  payload)

(defn- validate-preview-command
  [payload]
  (exact-keys! payload [:command] [:progress :direction :responseId])
  (when-not (contains? preview-commands (:command payload))
    (invalid! "penpot_motion_host_payload_invalid"
              "Motion preview command is invalid."))
  (when (contains? payload :progress)
    (when-not (finite-range? (:progress payload) 0 1)
      (invalid! "penpot_motion_host_payload_invalid"
                "Motion preview progress is invalid.")))
  (when (contains? payload :direction)
    (when-not (contains? #{-1 1} (:direction payload))
      (invalid! "penpot_motion_host_payload_invalid"
                "Motion preview direction is invalid.")))
  (when (contains? payload :responseId)
    (assert-id! (:responseId payload) "Response"))
  (when (and (= "seek" (:command payload))
             (not (contains? payload :progress)))
    (invalid! "penpot_motion_host_payload_invalid"
              "Motion preview seek requires progress."))
  payload)

(defn- validate-anatomy-highlight
  [payload]
  (exact-keys! payload [:partIds] [])
  (let [part-ids (:partIds payload)]
    (when-not (and (vector? part-ids)
                   (<= (count part-ids) max-part-ids))
      (invalid! "penpot_motion_host_payload_invalid"
                "Motion anatomy highlight is invalid."))
    (doseq [part-id part-ids]
      (assert-id! part-id "Motion part")))
  payload)

(defn- validate-payload
  [type payload]
  (record! payload (str "Payload for " type))
  (case type
    "studio.ready" (validate-ready payload)
    "studio.context.request" (validate-context-request payload)
    "studio.motion.read" (validate-motion-read payload)
    "studio.motion.write" (validate-motion-write payload)
    "studio.preview.recipe" (validate-preview-recipe payload)
    "studio.preview.command" (validate-preview-command payload)
    "studio.anatomy.highlight" (validate-anatomy-highlight payload)
    (invalid! "penpot_motion_host_message_type_invalid"
              (str "Unsupported inbound motion host message " type "."))))

(defn assert-inbound-message
  "Return a bounded normalized Motion Studio message or throw a coded error."
  [value]
  (let [message (decode-envelope value)]
    (record! message "Motion host message")
    (exact-keys! message [:schema :schemaVersion :type :payload] [:requestId])
    (when-not (and (= schema-name (:schema message))
                   (= schema-version (:schemaVersion message)))
      (invalid! "penpot_motion_host_message_version_invalid"
                "Motion host message version is not supported."))
    (when-not (contains? inbound-message-types (:type message))
      (invalid! "penpot_motion_host_message_type_invalid"
                (str "Unsupported inbound motion host message " (:type message) ".")))
    (when (contains? message :requestId)
      (assert-id! (:requestId message) "Request"))
    (assoc message :payload (validate-payload (:type message) (:payload message)))))

(defn inbound-message
  "Return a normalized inbound message, or nil for any malformed message."
  [value]
  (try
    (assert-inbound-message value)
    (catch :default _
      nil)))

(defn current-preview-recipe?
  "Bind a preview recipe to the currently selected component revision."
  [payload motion-payload]
  (and (= (:componentId payload) (:componentId motion-payload))
       (= (:revision payload) (:revision motion-payload))))

(defn public-preview-status
  "Translate internal renderer state to the frozen public host vocabulary."
  [status]
  (cond
    (= "unavailable" status) "empty"
    (contains? public-preview-statuses status) status
    :else "empty"))
