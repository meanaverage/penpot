;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns app.main.ui.sayhi.motion-canvas-preview
  (:require-macros [app.main.style :as stl])
  (:require
   [app.main.data.sayhi.motion-studio :as motion-studio]
   [rumext.v2 :as mf]))

(mf/defc motion-canvas-preview*
  [{:keys [page-id preview-ref preview-src preview-srcdoc portable
           shape vbox zoom on-load hidden]}]
  (let [container* (mf/use-state nil)
        container  @container*
        layout     (motion-studio/canvas-preview-layout shape vbox zoom)]
    (mf/use-effect
     (mf/deps page-id)
     (fn []
       (reset! container*
               (.getElementById js/document motion-studio/canvas-preview-root-id))
       ;; Effects may return a cleanup function, but a DOM node is not a
       ;; closable resource.  `reset!` returns the value it stores, so make the
       ;; no-cleanup result explicit when the active Penpot page changes.
       js/undefined))

    (when (and container layout (not hidden))
      (mf/portal
       (mf/html
        [:section
         {:class (stl/css :motion-canvas-preview)
          :style {:inset-inline-start (:left layout)
                  :inset-block-start (:top layout)
                  :inline-size (:screen-width layout)
                  :block-size (:screen-height layout)}
          :aria-label "Live component motion preview on canvas"}
         [:iframe
          {:ref preview-ref
           :class (stl/css :motion-canvas-preview-frame)
           :style {:inline-size (:design-width layout)
                   :block-size (:design-height layout)
                   :transform (str "scale(" (:scale layout) ")")}
           :on-load on-load
           :referrer-policy "strict-origin-when-cross-origin"
           :sandbox (if portable
                      "allow-same-origin"
                      "allow-forms allow-modals allow-popups allow-same-origin allow-scripts")
           :src preview-src
           :src-doc preview-srcdoc
           :title "Live component motion preview"}]])
       container))))
