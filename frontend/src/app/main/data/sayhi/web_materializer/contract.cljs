;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns app.main.data.sayhi.web-materializer.contract
  "Stable contract shared by SayHi web materializer implementations."
  (:require
   [app.util.json :as json]
   [app.util.storage :as sto]
   [cuerdas.core :as str]))

(def schema-name "sayhi.portable-web-artifact")
(def schema-version "1.0")
(def materialization-schema-name "sayhi.web-materialization")
(def materialization-schema-version "1.0")
(def projection-provider "projection-v1")
(def portable-provider "portable-v2")
(def shadow-provider "shadow")

(def ^:private max-artifact-characters 2000000)
(def ^:private storage-prefix "sayhi:web-artifact:")

(defn valid-mode?
  [mode]
  (contains? #{projection-provider portable-provider shadow-provider} mode))

(defn normalize-mode
  [mode]
  (if (valid-mode? mode) mode projection-provider))

(defn portable-artifact?
  [artifact]
  (and (map? artifact)
       (= schema-name (:schemaName artifact))
       (= schema-version (:schemaVersion artifact))
       (= portable-provider (:provider artifact))
       (string? (get-in artifact [:identity :componentId]))
       (string? (get-in artifact [:document :markup]))
       (string? (get-in artifact [:document :styles]))
       (map? (:selectors artifact))
       (map? (:anatomy artifact))))

(defn- protect-raw-element
  [value element]
  (str/replace (or value "")
               (js/RegExp. (str "</" element) "gi")
               (str "<\\/" element)))

(defn artifact-srcdoc
  "Build a non-scripted document from a validated portable artifact.

  The consumer iframe remains sandboxed without `allow-scripts` while v2 is a
  shadow candidate. Motion control will be added at the artifact-runtime
  boundary rather than by accepting executable Inspector output."
  ([artifact]
   (artifact-srcdoc artifact ""))
  ([artifact font-css]
   (when (portable-artifact? artifact)
     (let [styles (str (protect-raw-element font-css "style")
                       "\n"
                       (protect-raw-element (get-in artifact [:document :styles]) "style"))
           markup (get-in artifact [:document :markup])]
       (str "<!doctype html><html><head><meta charset=\"utf-8\">"
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            "<style>" styles "</style></head><body>"
            markup
            "</body></html>")))))

(defn cache-artifact!
  "Put one bounded artifact in tab-scoped storage for a same-origin preview.

  Returns the opaque key. New preview windows receive a copy of the opener's
  session storage; the artifact itself never enters a URL."
  [key artifact]
  (when (and (string? key)
             (re-matches #"^[A-Za-z0-9-]{1,128}$" key)
             (portable-artifact? artifact))
    (let [encoded (json/encode artifact)]
      (when (<= (count encoded) max-artifact-characters)
        (try
          (sto/set-item sto/session-storage (str storage-prefix key) encoded)
          key
          (catch :default _
            nil))))))

(defn read-cached-artifact
  [key]
  (when (and (string? key)
             (re-matches #"^[A-Za-z0-9-]{1,128}$" key))
    (try
      (let [encoded (sto/get-item sto/session-storage (str storage-prefix key))
            artifact (when (and (string? encoded)
                                (<= (count encoded) max-artifact-characters))
                       (json/decode encoded))]
        (when (portable-artifact? artifact) artifact))
      (catch :default _
        nil))))
