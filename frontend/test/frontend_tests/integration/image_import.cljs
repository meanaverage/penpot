;; Optional private-importer conformance harness. Not shipped in the app and
;; deliberately separate from the public unit-test runner/dependency graph.
(ns frontend-tests.integration.image-import
  (:require
   [app.common.test-helpers.files :as files]
   [app.common.test-helpers.tokens :as tokens]
   [app.main.fonts :as fonts]
   [app.main.store :as st]
   [app.plugins.api :as api]
   [app.plugins.register :as preg]
   [app.render-wasm.api :as wasm-api]
   [frontend-tests.helpers.state :as state]
   [frontend-tests.helpers.wasm :as wasm]
   [potok.v2.core :as ptk]))

(defn setup
  []
  (wasm/setup-wasm-mocks!)
  ;; The final zoom is a renderer boundary, not a shape/token mutation.
  (set! wasm-api/set-view-box (fn [& _] nil))
  (let [file (tokens/add-tokens-lib (files/sample-file :image-file :page-label :image-page))
        store (state/setup-store file)
        id (str (random-uuid))]
    (set! st/state store)
    (set! st/stream (ptk/input-stream store))
    (preg/register-session-plugin! id #{"content:read" "content:write" "library:read" "library:write"})
    (fonts/register! :google fonts/google-fonts)
    (ptk/emit! store #(assoc-in % [:workspace-local :vbox] {:x 0 :y 0 :width 1000 :height 1000}))
    (api/create-context id)))

(defn snapshot
  []
  (clj->js (state/get-file-from-state @st/state)))
