;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns app.main.data.sayhi.image-component
  (:require
   [app.main.data.plugins :as plugins]
   [app.main.refs :as refs]
   [app.main.store :as st]
   [app.plugins.register :as preg]))

(def schema "io.sayhi.studio.image-component")

(defn inbound
  [data]
  (try
    (when (and data (<= (.-length (js/JSON.stringify data)) 240000))
      (let [m (js->clj data :keywordize-keys true)]
        (when (and (= #{:schema :version :type :requestId :payload} (set (keys m)))
                   (= schema (:schema m))
                   (= "1.0" (:version m))
                   (string? (:requestId m))
                   (re-matches #"[a-zA-Z0-9-]{1,80}" (:requestId m))
                   (map? (:payload m))
                   (case (:type m)
                     "studio.context.request" (empty? (:payload m))
                     "studio.import" (let [p (:payload m)]
                                       (and (= #{:scene :identity :mode :fileId :pageId} (set (keys p)))
                                            (map? (:scene p))
                                            (contains? #{"A" "B" "C" "D"} (:mode p))
                                            (string? (:identity p))
                                            (re-matches #"image-[a-f0-9]{16}" (:identity p))
                                            (every? string? [(:fileId p) (:pageId p)])))
                     false))
          m)))
    (catch :default _ nil)))

(defn context
  []
  {:fileId (some-> (:current-file-id @st/state) str)
   :pageId (some-> (:current-page-id @st/state) str)
   :canEdit (true? (:can-edit @refs/permissions))})

(defn create-handler
  "The UI host checks actual iframe identity and configured origin before here.
  The plugin path and permissions are first-party constants, never model input."
  []
  (let [active* (atom nil)
        receipts* (atom {})]
    (fn [data reply!]
      (when-let [{:keys [type requestId payload]} (inbound data)]
        (let [send! (fn [type payload]
                      (reply! (clj->js {:schema schema :version "1.0" :type type
                                        :requestId requestId :payload payload})))
              ctx (context)]
          (if (= type "studio.context.request")
            (send! "host.context" ctx)
            (cond
              (get @receipts* requestId)
              (send! "host.result" (get @receipts* requestId))

              @active*
              (send! "host.result" {:ok false :error "A component import is already in progress."})

              (not (and (:canEdit ctx)
                        (= (:fileId ctx) (:fileId payload))
                        (= (:pageId ctx) (:pageId payload))))
              (send! "host.result" {:ok false :error "Open the target page with edit access before importing."})

              :else
              (let [plugin-id (str (random-uuid))
                    permissions #{"content:read" "content:write" "library:read" "library:write"}
                    release! (preg/register-session-plugin! plugin-id permissions)
                    alive* (atom true)
                    timer* (atom nil)
                    finish! (fn [result]
                              (when (compare-and-set! alive* true false)
                                (js/clearTimeout @timer*)
                                (release!)
                                (reset! active* nil)
                                (swap! receipts* (fn [items]
                                                   (assoc (if (< (count items) 64) items {}) requestId result)))
                                (send! "host.result" result)))
                    can-import? #(and @alive*
                                      (= ctx (context)))
                    request-json (js/JSON.stringify (clj->js payload))]
                (reset! active* requestId)
                (reset! timer* (js/setTimeout #(finish! {:ok false :error "Import timed out. Inspect the canvas before retrying; Undo can remove a partial import."}) 60000))
                (send! "host.importing" {})
                (plugins/start-plugin!
                 {:plugin-id plugin-id
                  :name "SayHi image component"
                  :version 2
                  :host (str (.-origin js/location) "/plugins/sayhi-image-component/")
                  :code "image-plugin.js"
                  :permissions permissions}
                 #js {:imageComponent
                      #js {:request (constantly request-json)
                           :canImport can-import?
                           :complete (fn [value]
                                       (try
                                         (finish! (js->clj (js/JSON.parse value) :keywordize-keys true))
                                         (catch :default _
                                           (finish! {:ok false :error "Invalid importer receipt."}))))}})))))))))
