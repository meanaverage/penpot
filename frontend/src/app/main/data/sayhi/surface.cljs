;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns app.main.data.sayhi.surface
  "Small Penpot-owned helpers for mounting external SayHi capabilities."
  (:require
   [clojure.string :as str]))

(def ^:private studio-mode-apps
  {"canvas" :components
   "components" :components
   "pages" :pages
   "video" :video
   "paper" :paper
   "artifacts" :artifacts})

(defn- location-params
  [search hash]
  (let [hash-query (second (str/split (or hash "") #"\?" 2))
        search     (str/replace-first (or search "") #"^\?" "")]
    (js/URLSearchParams. (str/join "&" (remove str/blank? [search hash-query])))))

(defn enabled?
  [configured-surface search hash]
  (let [surface (some-> configured-surface str/trim str/lower-case)
        params  (location-params search hash)]
    (or (= "canvas" surface)
        (= "1" (.get params "sayhiStudio"))
        (contains? studio-mode-apps (.get params "studioMode")))))

(defn active-app
  [search hash]
  (let [params (location-params search hash)]
    (or (get studio-mode-apps (.get params "studioMode"))
        :components)))

(defn app-location-href
  [href app-id]
  (try
    (let [url       (js/URL. href)
          hash      (or (.-hash url) "")
          parts     (str/split hash #"\?" 2)
          hash-path (first parts)
          params    (js/URLSearchParams. (or (second parts) ""))
          mode      (case app-id
                      :pages "pages"
                      :video "video"
                      :paper "paper"
                      :artifacts "artifacts"
                      "canvas")]
      (.set params "studioMode" mode)
      (set! (.-hash url) (str hash-path "?" (.toString params)))
      (.-href url))
    (catch :default _
      nil)))

(defn controls-shortcut?
  [^js event]
  (let [target    (.-target event)
        tag-name  (some-> target .-tagName str/lower-case)
        editable? (or (contains? #{"input" "textarea" "select"} tag-name)
                      (true? (some-> target .-isContentEditable)))]
    (and (= "\\" (.-key event))
         (not (.-repeat event))
         (not (.-altKey event))
         (not (.-ctrlKey event))
         (not (.-metaKey event))
         (not editable?))))

(defn canvas-bottom-inset
  [layout palette-size]
  (if (and (not (contains? layout :hide-ui))
           (or (contains? layout :colorpalette)
               (contains? layout :textpalette)))
    (if (number? palette-size)
      (max 0 palette-size)
      0)
    0))
