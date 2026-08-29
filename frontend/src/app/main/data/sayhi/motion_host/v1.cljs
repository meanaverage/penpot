;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns app.main.data.sayhi.motion-host.v1
  "Bounded adapter contract between Penpot and standalone Motion Studio."
  (:require
   [clojure.set :as set]
   [clojure.string :as str]))

(def schema-name "io.sayhi.penpot.motion-host")
(def schema-version "1.0")
(def capabilities
  ["selection.read" "motion.read" "preview.control" "artifact.summary"])

(def ^:private max-envelope-bytes 1050000)
(def ^:private max-document-bytes 1000000)
(def ^:private inbound-types
  #{"studio.ready"
    "studio.context.request"
    "studio.motion.read"
    "studio.preview.recipe"
    "studio.preview.command"})
(def ^:private preview-commands
  #{"prepare" "play" "pause" "seek" "reset" "simulate"})
(def ^:private identifier-pattern
  #"^(?!__proto__$|prototype$|constructor$)[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")

(defn- invalid! [message]
  (throw (ex-info message {:code "penpot_motion_host_message_invalid"})))

(defn- exact-keys! [value required optional]
  (let [actual (set (keys value))
        required (set required)
        allowed (set/union required (set optional))]
    (when (or (seq (set/difference actual allowed))
              (seq (set/difference required actual)))
      (invalid! "Motion host message properties are invalid.")))
  value)

(defn- identifier? [value]
  (and (string? value)
       (boolean (re-matches identifier-pattern value))))

(defn- revision? [value]
  (and (js/Number.isSafeInteger value) (not (neg? value))))

(defn- finite-range? [value minimum maximum]
  (and (number? value)
       (js/Number.isFinite value)
       (<= minimum value maximum)))

(defn- json-bytes [value]
  (try
    (let [encoded (.stringify js/JSON (clj->js value))]
      (when (string? encoded)
        (.-byteLength (.encode (js/TextEncoder.) encoded))))
    (catch :default _ nil)))

(defn- decode-envelope [value]
  (let [size (json-bytes value)]
    (when-not (and size (<= size max-envelope-bytes))
      (invalid! "Motion host message exceeds the bounded envelope."))
    (js->clj (.parse js/JSON (.stringify js/JSON value))
             :keywordize-keys true)))

(defn- validate-payload [type payload]
  (when-not (map? payload)
    (invalid! "Motion host payload must be an object."))
  (case type
    "studio.ready"
    (do
      (exact-keys! payload [:clientVersion] [])
      (when-not (and (string? (:clientVersion payload))
                     (not (str/blank? (:clientVersion payload)))
                     (<= (count (:clientVersion payload)) 64))
        (invalid! "Motion Studio version is invalid.")))

    "studio.context.request"
    (exact-keys! payload [] [])

    "studio.motion.read"
    (do
      (exact-keys! payload [:componentId] [])
      (when-not (identifier? (:componentId payload))
        (invalid! "Motion component identifier is invalid.")))

    "studio.preview.recipe"
    (do
      (exact-keys! payload [:componentId :revision :recipe] [])
      (when-not (and (identifier? (:componentId payload))
                     (revision? (:revision payload))
                     (map? (:recipe payload))
                     (<= (or (json-bytes (:recipe payload))
                             (inc max-document-bytes))
                         max-document-bytes))
        (invalid! "Motion preview recipe is invalid.")))

    "studio.preview.command"
    (do
      (exact-keys! payload [:command] [:progress :direction :responseId])
      (when-not (contains? preview-commands (:command payload))
        (invalid! "Motion preview command is invalid."))
      (when (contains? payload :progress)
        (when-not (finite-range? (:progress payload) 0 1)
          (invalid! "Motion preview progress is invalid.")))
      (when (contains? payload :direction)
        (when-not (contains? #{-1 1} (:direction payload))
          (invalid! "Motion preview direction is invalid.")))
      (when (contains? payload :responseId)
        (when-not (identifier? (:responseId payload))
          (invalid! "Motion response identifier is invalid."))))

    (invalid! "Motion host message type is not supported."))
  payload)

(defn assert-inbound-message [value]
  (let [message (decode-envelope value)]
    (when-not (map? message)
      (invalid! "Motion host message must be an object."))
    (exact-keys! message [:schema :schemaVersion :type :payload] [:requestId])
    (when-not (and (= schema-name (:schema message))
                   (= schema-version (:schemaVersion message))
                   (contains? inbound-types (:type message)))
      (invalid! "Motion host message version or type is not supported."))
    (when (contains? message :requestId)
      (when-not (identifier? (:requestId message))
        (invalid! "Motion request identifier is invalid.")))
    (assoc message :payload (validate-payload (:type message) (:payload message)))))

(defn inbound-message [value]
  (try
    (assert-inbound-message value)
    (catch :default _ nil)))

(defn host-href [studio-uri]
  (when (string? studio-uri)
    (try
      (let [base (js/URL. studio-uri)]
        (when (and (contains? #{"http:" "https:"} (.-protocol base))
                   (str/blank? (.-username base))
                   (str/blank? (.-password base)))
          (let [value (if (.endsWith studio-uri "/") studio-uri (str studio-uri "/"))
                href (js/URL. "studio/penpot-motion-host/" value)]
            (.set (.-searchParams href) "embed" "1")
            (str href))))
      (catch :default _ nil))))

(defn same-window? [left right]
  (js/Object.is left right))

(defn context-message
  [{:keys [file-id page-id component-source theme locale]}]
  (let [web-object (:web-object component-source)
        artifact (:artifact component-source)]
    {:schema schema-name
     :schemaVersion schema-version
     :type "host.context"
     :payload
     {:context
      (cond->
       {:fileId (str file-id)
        :pageId (str page-id)
        :selection
        [{:id (str (:shape-id component-source))
          :name (:component-id web-object)
          :type "frame"
          :componentId (:component-id web-object)}]
        :capabilities capabilities
        :theme (if (= "dark" theme) "dark" "light")
        :locale (or (not-empty locale) "en")}
        artifact
        (assoc :webMaterialization
               {:schemaName "sayhi.web-materialization"
                :schemaVersion "1.0"
                :mode "portable-v2"
                :activeProvider "portable-v2"}))}}))

(defn motion-document-payload [component-source]
  (let [document (:motion-document component-source)]
    {:componentId (get-in component-source [:web-object :component-id])
     :revision (get-in document [:$extensions :io.sayhi.motion :revision] 0)
     :document document}))

(defn motion-document-message [component-source]
  {:schema schema-name
   :schemaVersion schema-version
   :type "host.motion.document"
   :payload (motion-document-payload component-source)})

(defn error-message [code message retryable]
  {:schema schema-name
   :schemaVersion schema-version
   :type "host.error"
   :payload {:code code :message message :retryable (true? retryable)}})
