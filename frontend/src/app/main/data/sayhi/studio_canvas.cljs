;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns app.main.data.sayhi.studio-canvas
  (:require
   [clojure.string :as str]))

(def assist-capability "studio.assist")

(def studio-apps
  [{:id :components
    :label "Components"
    :description "Penpot design and motion"}
   {:id :pages
    :label "Pages"
    :description "Web pages and publication"}
   {:id :video
    :label "Video"
    :description "MiniMax H3"}
   {:id :paper
    :label "Paper"
    :description "Documents and diagrams"}
   {:id :artifacts
    :label "Artifacts"
    :description "Shared project objects"}])

(def ^:private studio-mode-apps
  {"canvas" :components
   "components" :components
   "pages" :pages
   "video" :video
   "paper" :paper})

(def assist-output-schema
  {:type "object"
   :properties
   {:kind {:type "string"
           :enum ["design" "motion" "context" "explain"]}
    :summary {:type "string"
              :minLength 1
              :maxLength 600}
    :next_step {:type "string"
                :minLength 1
                :maxLength 1200}}
   :required ["kind" "summary" "next_step"]
   :additionalProperties false})

(defn- location-params
  [search hash]
  (let [hash-query (second (str/split (or hash "") #"\?" 2))
        search     (str/replace-first (or search "") #"^\?" "")]
    (js/URLSearchParams. (str/join "&" (remove str/blank? [search hash-query])))))

(defn studio-canvas-mode-from-location?
  [search hash]
  (let [params (location-params search hash)]
    (or (contains? studio-mode-apps (.get params "studioMode"))
        (= "1" (.get params "sayhiStudio")))))

(defn studio-surface-enabled?
  [configured-surface]
  (and (string? configured-surface)
       (= "canvas" (-> configured-surface str/trim str/lower-case))))

(defn studio-app-from-location
  [search hash]
  (let [params (location-params search hash)]
    (or (get studio-mode-apps (.get params "studioMode"))
        :components)))

(defn studio-canvas-mode?
  ([]
   (studio-canvas-mode? nil))
  ([configured-surface]
   (or (studio-surface-enabled? configured-surface)
       (studio-canvas-mode-from-location?
        (.-search (.-location js/window))
        (.-hash (.-location js/window))))))

(defn studio-controls-shortcut?
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

(defn studio-app-href
  [studio-uri app-id]
  (when (and (string? studio-uri)
             (not (str/blank? studio-uri)))
    (try
      (let [base (js/URL. studio-uri)]
        (case app-id
          :pages (.-href (js/URL. "/studio/pages/" base))
          :video (.-href (js/URL. "/studio/canvas/?studioMode=video" base))
          :paper (.-href (js/URL. "/studio/canvas/?studioMode=paper" base))
          :artifacts (.-href (js/URL. "/artifacts/" base))
          nil))
      (catch :default _
        nil))))

(defn studio-app-location-href
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
                      "canvas")]
      (.set params "studioMode" mode)
      (set! (.-hash url) (str hash-path "?" (.toString params)))
      (.-href url))
    (catch :default _
      nil)))

(defn canvas-bottom-inset
  [layout palette-size]
  ;; Palette measurements survive the palette being hidden. Only reserve that
  ;; stale measurement while one of Penpot's bottom palettes is actually open.
  (if (and (not (contains? layout :hide-ui))
           (or (contains? layout :colorpalette)
               (contains? layout :textpalette)))
    (if (number? palette-size)
      (max 0 palette-size)
      0)
    0))

(defn select-assist-route
  [manifest]
  (some (fn [route]
          (when (and (= assist-capability (:capability route))
                     (true? (:available route))
                     (string? (:endpoint route))
                     (str/starts-with? (:endpoint route) "/"))
            route))
        (:routes manifest)))

(defn assist-request
  [request selection]
  (let [selection (->> selection
                       (keep #(some-> % str str/trim not-empty))
                       (take 8)
                       vec)]
    {:capability assist-capability
     :prompt
     (str
      "You are Qwen 3.8 assisting inside the SayHi Studio Penpot canvas. "
      "Interpret the user's intent using the active selection. Give one concise plan and one concrete next step. "
      "Do not claim that you changed the document; this surface is currently advisory.\n\n"
      "USER_REQUEST\n" (str/trim request) "\n\n"
      "ACTIVE_SELECTION\n" (if (seq selection) (str/join "\n" selection) "No named object selected."))
     :output_schema assist-output-schema}))

(defn agent-api-url
  [motion-studio-uri path]
  (when (and (string? motion-studio-uri)
             (not (str/blank? motion-studio-uri))
             (string? path)
             (str/starts-with? path "/"))
    (let [base (js/URL. motion-studio-uri (.-href (.-location js/window)))]
      (.-href (js/URL. path (str (.-origin base) "/"))))))
