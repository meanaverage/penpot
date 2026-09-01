;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns app.main.data.sayhi.surface-chrome.v1
  "Bounded public adapter contract for the externally hosted SayHi chrome."
  (:require
   [clojure.string :as str]))

(def schema-name "io.sayhi.studio.surface-chrome")
(def schema-version "1.0")

(def ^:private inbound-types
  #{"studio.ready" "studio.context.request" "studio.bounds" "studio.command"})
(def ^:private commands
  #{"motion.toggle" "penpot.controls.toggle"})

(defn host-href
  [configured-uri {:keys [active-app components-uri pages-uri]}]
  (when (and (string? configured-uri) (not (str/blank? configured-uri)))
    (try
      (let [url (js/URL. configured-uri)]
        (when (and (contains? #{"http:" "https:"} (.-protocol url))
                   (str/blank? (.-username url))
                   (str/blank? (.-password url)))
          (when (contains? #{:components :pages :video :paper :artifacts} active-app)
            (.set (.-searchParams url) "activeApp" (name active-app)))
          (when (and (string? components-uri) (not (str/blank? components-uri)))
            (.set (.-searchParams url) "componentsUri" components-uri))
          (when (and (string? pages-uri) (not (str/blank? pages-uri)))
            (.set (.-searchParams url) "pagesUri" pages-uri))
          (.-href url)))
      (catch :default _ nil))))

(defn context-message
  [context]
  {:schemaName schema-name
   :schemaVersion schema-version
   :type "host.context"
   :payload {:context context}})

(defn inbound-message
  [value]
  (try
    (let [message (js->clj (.parse js/JSON (.stringify js/JSON value))
                           :keywordize-keys true)
          type    (:type message)
          payload (:payload message)]
      (when (and (= #{:schemaName :schemaVersion :type :payload} (set (keys message)))
                 (= schema-name (:schemaName message))
                 (= schema-version (:schemaVersion message))
                 (contains? inbound-types type)
                 (map? payload))
        (case type
          "studio.ready"
          (when (and (= #{:capabilities} (set (keys payload)))
                     (vector? (:capabilities payload))
                     (<= (count (:capabilities payload)) 16)
                     (every? #(and (string? %) (<= (count %) 64)) (:capabilities payload)))
            message)

          "studio.context.request"
          (when (empty? payload) message)

          "studio.bounds"
          (when (and (= #{:height} (set (keys payload)))
                     (js/Number.isSafeInteger (:height payload))
                     (<= 48 (:height payload) 640))
            message)

          "studio.command"
          (when (and (= #{:command} (set (keys payload)))
                     (contains? commands (:command payload)))
            message)

          nil)))
    (catch :default _ nil)))

(defn same-window?
  [left right]
  (js/Object.is left right))

(defn public-theme
  [value]
  (if (contains? #{:dark "dark"} value) "dark" "light"))

(defn public-locale
  [value]
  (let [locale (when (string? value) (str/trim value))]
    (if (and (not (str/blank? locale)) (<= (count locale) 32)) locale "en")))

(defn public-canvas-inset
  [value]
  (if (number? value)
    (-> value (max 0) (min 512))
    0))

(defn- bounded-label
  [value fallback maximum]
  (let [label (when (string? value) (str/trim value))]
    (if (str/blank? label)
      fallback
      (subs label 0 (min maximum (count label))))))

(defn selection-context
  [shapes]
  (->> shapes
       (filter :id)
       (take 32)
       (mapv (fn [{:keys [id type] shape-name :name}]
               {:id (str id)
                :name (bounded-label shape-name "Unnamed object" 256)
                :type (bounded-label (some-> type name) "unknown" 64)}))))
