;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns app.main.data.sayhi.page-preview
  "Resolve an imported SayHi Page projection into the portable web boundary.

  Page routing and publication remain SayHi concerns. This adapter only reads
  the bounded metadata written by the Pages importer and identifies the exact
  Penpot board that should be materialized for an in-canvas live web view."
  (:require
   [app.util.json :as json]
   [clojure.string :as str]))

(def shared-namespace
  (keyword "shared" "io.sayhi.pages"))

(def preview-modes #{:live :design :compare})

(def ^:private max-parent-depth 64)
(def ^:private max-capture-characters 16384)
(def ^:private identifier-pattern
  #"^[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}$")

(defn normalize-preview-mode
  [mode]
  (if (contains? preview-modes mode) mode :live))

(defn- shared-data
  [shape]
  (get-in shape [:plugin-data shared-namespace]))

(defn- valid-identifier?
  [value]
  (and (string? value)
       (boolean (re-matches identifier-pattern value))))

(defn- route-path?
  [value]
  (and (string? value)
       (str/starts-with? value "/")
       (<= (count value) 2048)))

(defn- route-from-source-url
  [value]
  (when (string? value)
    (try
      (let [url      (js/URL. value)
            protocol (.-protocol url)
            path     (.-pathname url)]
        (when (and (contains? #{"https:" "http:"} protocol)
                   (route-path? path))
          path))
      (catch :default _
        nil))))

(defn- decode-capture
  [value]
  (when (and (string? value)
             (<= (count value) max-capture-characters))
    (try
      (let [capture (json/decode value)]
        (when (map? capture) capture))
      (catch :default _
        nil))))

(defn page-contract
  "Return the trusted imported-page identity stored on one native root board."
  [shape]
  (let [data       (shared-data shape)
        page-id    (get data "page-id")
        source-url (get data "source-url")
        route-path (or (get data "route-path")
                       (route-from-source-url source-url))]
    (when (and (= :frame (:type shape))
               (= "native" (get data "representation"))
               (valid-identifier? page-id)
               (route-path? route-path)
               (number? (:width shape))
               (pos? (:width shape))
               (number? (:height shape))
               (pos? (:height shape)))
      (let [capture  (decode-capture (get data "capture"))
            revision (or (:capturedAt capture)
                         (get data "import-version")
                         "unknown")]
        {:page-id page-id
         :route-path route-path
         :canonical-url (get data "route-canonical-url")
         :source-url source-url
         :source-title (get data "source-title")
         :site-id (get data "site-id")
         :site-origin (get data "site-origin")
         :revision revision
         :shape-id (:id shape)}))))

(defn- resolve-from-shape
  [objects shape-id]
  (loop [shape-id shape-id
         visited #{}
         depth 0]
    (when (and shape-id
               (< depth max-parent-depth)
               (not (contains? visited shape-id)))
      (when-let [shape (get objects shape-id)]
        (or (page-contract shape)
            (recur (:parent-id shape)
                   (conj visited shape-id)
                   (inc depth)))))))

(defn- unique-native-page
  [objects]
  (let [pages (into [] (keep page-contract) (vals objects))]
    (when (= 1 (count pages))
      (first pages))))

(defn resolve-page-root
  "Resolve the selected page projection, falling back only when the Penpot
  page contains exactly one native SayHi Page board.

  The unique fallback lets Pages mode work immediately after import without
  forcing the user to select the outer board. Multiple page boards fail closed
  until the user selects one explicitly."
  [objects selected]
  (or (some #(resolve-from-shape objects %) selected)
      (unique-native-page objects)))

(defn web-object
  "Map an imported Page contract to the existing portable materializer input.

  These identifiers describe an exact page projection; they do not make a
  Penpot revision, private SayHi Artifact version, and public publication
  snapshot share a counter or identity."
  [page]
  (when page
    {:id (str "sayhi.page/" (:page-id page))
     :component-id (str "sayhi.page/" (:page-id page))
     :story-id (str "route:" (:route-path page))
     :revision (str (:revision page))
     :shape-id (:shape-id page)
     :source :page-projection}))
