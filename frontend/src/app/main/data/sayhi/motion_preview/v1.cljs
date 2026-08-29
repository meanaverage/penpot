;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns app.main.data.sayhi.motion-preview.v1
  "Bounded relay between the public Penpot host and private web runtime."
  (:require
   [clojure.set :as set]))

(def schema-name "io.sayhi.penpot.motion-preview")
(def schema-version "1.0")

(def ^:private max-envelope-bytes 1050000)
(def ^:private states
  #{"empty" "loading" "ready" "playing" "paused" "reversing" "finished" "reduced"})
(def ^:private identifier-pattern
  #"^(?!__proto__$|prototype$|constructor$)[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")

(defn- identifier? [value]
  (and (string? value) (boolean (re-matches identifier-pattern value))))

(defn- finite-range? [value minimum maximum]
  (and (number? value) (js/Number.isFinite value) (<= minimum value maximum)))

(defn- exact-keys? [value required optional]
  (let [actual (set (keys value))
        required (set required)
        allowed (set/union required (set optional))]
    (and (empty? (set/difference actual allowed))
         (empty? (set/difference required actual)))))

(defn- json-bytes [value]
  (try
    (let [encoded (.stringify js/JSON value)]
      (when (string? encoded)
        (.-byteLength (.encode (js/TextEncoder.) encoded))))
    (catch :default _ nil)))

(defn inbound-message [value]
  (try
    (let [size (json-bytes value)
          message (when (and size (<= size max-envelope-bytes))
                    (js->clj (.parse js/JSON (.stringify js/JSON value))
                             :keywordize-keys true))
          type (:type message)
          payload (:payload message)]
      (when (and (map? message)
                 (exact-keys? message [:schema :schemaVersion :type :payload] [])
                 (= schema-name (:schema message))
                 (= schema-version (:schemaVersion message))
                 (map? payload)
                 (case type
                   "runtime.motion.state"
                   (and (exact-keys? payload
                                     [:status :progress :time :duration
                                      :responseId :direction]
                                     [])
                        (contains? states (:status payload))
                        (finite-range? (:progress payload) 0 1)
                        (finite-range? (:time payload) 0 600)
                        (finite-range? (:duration payload) 0 600)
                        (<= (:time payload) (:duration payload))
                        (or (nil? (:responseId payload))
                            (identifier? (:responseId payload)))
                        (contains? #{-1 1} (:direction payload)))

                   "runtime.motion.error"
                   (and (exact-keys? payload [:code :message :retryable] [])
                        (identifier? (:code payload))
                        (string? (:message payload))
                        (not-empty (:message payload))
                        (<= (count (:message payload)) 400)
                        (boolean? (:retryable payload)))

                   false))
        message))
    (catch :default _ nil)))

(defn same-window? [left right]
  (js/Object.is left right))

(defn recipe-message [studio-message artifact motion-document]
  (let [payload (:payload studio-message)
        motion-revision (get-in motion-document
                                [:$extensions :io.sayhi.motion :revision])]
    (when (and (= "studio.preview.recipe" (:type studio-message))
               (= (:componentId payload)
                  (get-in artifact [:identity :componentId]))
               (= (:revision payload) motion-revision)
               (string? (get-in artifact [:identity :revision])))
      {:schema schema-name
       :schemaVersion schema-version
       :type "host.motion.recipe"
       :payload {:componentId (:componentId payload)
                 :revision (get-in artifact [:identity :revision])
                 :recipe (:recipe payload)}})))

(defn command-message [studio-message]
  (when (= "studio.preview.command" (:type studio-message))
    {:schema schema-name
     :schemaVersion schema-version
     :type "host.motion.command"
     :payload (:payload studio-message)}))

(defn motion-host-message [runtime-message]
  (case (:type runtime-message)
    "runtime.motion.state"
    {:schema "io.sayhi.penpot.motion-host"
     :schemaVersion "1.0"
     :type "host.preview.state"
     :payload (:payload runtime-message)}

    "runtime.motion.error"
    {:schema "io.sayhi.penpot.motion-host"
     :schemaVersion "1.0"
     :type "host.error"
     :payload (:payload runtime-message)}

    nil))
