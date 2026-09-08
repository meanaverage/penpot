;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns app.main.data.sayhi.web-runtime-host.v1
  "Bounded host adapter for the standalone SayHi Web Runtime."
  (:require
   [clojure.set :as set]
   [clojure.string :as str]))

(def schema-name "io.sayhi.penpot.web-runtime-host")
(def schema-version "1.0")
(def legacy-mode "legacy")
(def standalone-mode "standalone-v1")
(def shadow-mode "shadow")

(def host-capabilities
  ["selection.read" "artifact.read" "preview.render" "fidelity.report"])

(def ^:private max-envelope-bytes 1050000)
(def ^:private inbound-types
  #{"runtime.ready"
    "runtime.context.request"
    "runtime.artifact.request"
    "runtime.preview.state"})
(def ^:private preview-statuses
  #{"empty" "loading" "ready" "error"})

(defn- invalid!
  [code message]
  (throw (ex-info message {:code code})))

(defn- record!
  [value label]
  (when-not (map? value)
    (invalid! "penpot_web_runtime_host_payload_invalid"
              (str label " must be an object.")))
  value)

(defn- exact-keys!
  [value required optional]
  (let [actual   (set (keys value))
        required (set required)
        allowed  (set/union required (set optional))]
    (when (seq (set/difference actual allowed))
      (invalid! "penpot_web_runtime_host_property_unknown"
                "The web runtime message contains an unknown property."))
    (when (seq (set/difference required actual))
      (invalid! "penpot_web_runtime_host_property_missing"
                "The web runtime message is missing a required property.")))
  value)

(defn- identifier?
  [value]
  (and (string? value)
       (some? (re-matches
               #"^(?!__proto__$|prototype$|constructor$)[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}$"
               value))))

(defn- revision?
  [value]
  (and (string? value)
       (not (str/blank? value))
       (<= (count value) 256)))

(defn- json-bytes
  [value]
  (try
    (let [encoded (.stringify js/JSON value)]
      (when-not (string? encoded)
        (invalid! "penpot_web_runtime_host_message_invalid"
                  "Web runtime message must be serializable JSON."))
      (.-byteLength (.encode (js/TextEncoder.) encoded)))
    (catch :default error
      (if (ex-data error)
        (throw error)
        (invalid! "penpot_web_runtime_host_message_invalid"
                  "Web runtime message must be serializable JSON.")))))

(defn- decode-envelope
  [value]
  (when (> (json-bytes value) max-envelope-bytes)
    (invalid! "penpot_web_runtime_host_message_too_large"
              "Web runtime message exceeds the bounded host envelope."))
  (try
    (js->clj (.parse js/JSON (.stringify js/JSON value)) :keywordize-keys true)
    (catch :default error
      (if (ex-data error)
        (throw error)
        (invalid! "penpot_web_runtime_host_message_invalid"
                  "Web runtime message must be serializable JSON.")))))

(defn- assert-identifier!
  [value label]
  (when-not (identifier? value)
    (invalid! "penpot_web_runtime_host_identifier_invalid"
              (str label " identifier is invalid.")))
  value)

(defn- assert-revision!
  [value]
  (when-not (revision? value)
    (invalid! "penpot_web_runtime_host_revision_invalid"
              "Web runtime revision is invalid."))
  value)

(defn- validate-ready
  [payload]
  (exact-keys! payload [:clientVersion] [])
  (when-not (and (string? (:clientVersion payload))
                 (not (str/blank? (:clientVersion payload)))
                 (<= (count (:clientVersion payload)) 64))
    (invalid! "penpot_web_runtime_host_payload_invalid"
              "Web runtime client version is invalid."))
  payload)

(defn- validate-artifact-request
  [payload]
  (exact-keys! payload [:componentId :revision] [])
  (assert-identifier! (:componentId payload) "Component")
  (assert-revision! (:revision payload))
  payload)

(defn- validate-preview-state
  [payload]
  (exact-keys! payload [:status] [:componentId :revision :fidelity :error])
  (when-not (contains? preview-statuses (:status payload))
    (invalid! "penpot_web_runtime_host_payload_invalid"
              "Web runtime preview status is invalid."))
  (when (contains? payload :componentId)
    (assert-identifier! (:componentId payload) "Component"))
  (when (contains? payload :revision)
    (assert-revision! (:revision payload)))
  (when (contains? payload :fidelity)
    (record! (:fidelity payload) "Web runtime fidelity"))
  (when (contains? payload :error)
    (record! (:error payload) "Web runtime error"))
  payload)

(defn- validate-payload
  [type payload]
  (record! payload (str "Payload for " type))
  (case type
    "runtime.ready" (validate-ready payload)
    "runtime.context.request" (exact-keys! payload [] [])
    "runtime.artifact.request" (validate-artifact-request payload)
    "runtime.preview.state" (validate-preview-state payload)
    (invalid! "penpot_web_runtime_host_message_type_invalid"
              (str "Unsupported web runtime message " type "."))))

(defn assert-inbound-message
  [value]
  (let [message (decode-envelope value)]
    (record! message "Web runtime host message")
    (exact-keys! message [:schema :schemaVersion :type :payload] [:requestId])
    (when-not (and (= schema-name (:schema message))
                   (= schema-version (:schemaVersion message)))
      (invalid! "penpot_web_runtime_host_message_version_invalid"
                "Web runtime host message version is not supported."))
    (when-not (contains? inbound-types (:type message))
      (invalid! "penpot_web_runtime_host_message_type_invalid"
                (str "Unsupported web runtime message " (:type message) ".")))
    (when (contains? message :requestId)
      (assert-identifier! (:requestId message) "Request"))
    (assoc message :payload (validate-payload (:type message) (:payload message)))))

(defn inbound-message
  [value]
  (try
    (assert-inbound-message value)
    (catch :default _
      nil)))

(defn normalize-mode
  [mode]
  (if (contains? #{legacy-mode standalone-mode shadow-mode} mode)
    mode
    legacy-mode))

(defn host-href
  [runtime-uri]
  (when (string? runtime-uri)
    (try
      (let [base (js/URL. runtime-uri)]
        (when (contains? #{"http:" "https:"} (.-protocol base))
          (let [value (if (.endsWith runtime-uri "/")
                        runtime-uri
                        (str runtime-uri "/"))
                href  (js/URL. "studio/penpot-web-runtime-host/" value)]
            (.set (.-searchParams href) "embed" "1")
            (str href))))
      (catch :default _
        nil))))

(defn same-cross-origin-window?
  [left right]
  (identical? left right))

(defn current-artifact-request?
  [payload artifact]
  (and (= (:componentId payload) (get-in artifact [:identity :componentId]))
       (= (:revision payload) (get-in artifact [:identity :revision]))))

(defn context-message
  [{:keys [file-id page-id root-shape-id component-id revision component-name theme locale]}]
  {:schema schema-name
   :schemaVersion schema-version
   :type "host.context"
   :payload
   {:context
    {:fileId (str (or file-id "unknown-file"))
     :pageId (str (or page-id "unknown-page"))
     :selection
     (cond-> []
       (and component-id revision)
       (conj {:id (or root-shape-id component-id)
              :name (or (not-empty component-name) component-id)
              :type "frame"
              :componentId component-id
              :revision revision}))
     :capabilities host-capabilities
     :theme (if (= "dark" theme) "dark" "light")
     :locale (or (not-empty locale) "en")}}})

(defn artifact-message
  [artifact]
  {:schema schema-name
   :schemaVersion schema-version
   :type "host.artifact"
   :payload {:componentId (get-in artifact [:identity :componentId])
             :revision (get-in artifact [:identity :revision])
             :artifact artifact}})

(defn error-message
  [code message retryable]
  {:schema schema-name
   :schemaVersion schema-version
   :type "host.error"
   :payload {:code code :message message :retryable (true? retryable)}})
