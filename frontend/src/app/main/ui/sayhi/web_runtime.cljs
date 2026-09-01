;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns app.main.ui.sayhi.web-runtime
  "Thin Penpot host for the standalone private SayHi Web Runtime."
  (:require
   [app.main.data.sayhi.component-artifact :as component-artifact]
   [app.main.data.sayhi.motion-preview.v1 :as motion-preview]
   [app.main.data.sayhi.web-runtime-host.v1 :as runtime-host]
   [goog.object :as gobj]
   [rumext.v2 :as mf]))

(mf/defc standalone-runtime*
  {::mf/props :obj}
  [{:keys [file-id page-id component-source runtime-uri title on-state
           motion-message on-motion-state style]}]
  (let [artifact              (:artifact component-source)
        web-object            (:web-object component-source)
        href                  (runtime-host/host-href runtime-uri)
        target-origin         (when href (.-origin (js/URL. href)))
        frame-ref             (mf/use-ref nil)
        ready-origin-ref      (mf/use-ref nil)
        theme                 (or (some-> js/document
                                          .-documentElement
                                          .-dataset
                                          (gobj/get "theme"))
                                  "light")
        context-message       (runtime-host/context-message
                               {:file-id file-id
                                :page-id page-id
                                :shape-id (:shape-id component-source)
                                :component-id (:component-id web-object)
                                :revision (:revision web-object)
                                :component-name (:component-id web-object)
                                :theme theme
                                :locale (or (.-language js/navigator) "en")})
        post-message!
        (mf/use-fn
         (mf/deps target-origin)
         (fn [message]
           (when (= target-origin (mf/ref-val ready-origin-ref))
             (when-let [frame (mf/ref-val frame-ref)]
               (when-let [content-window (.-contentWindow frame)]
                 (.postMessage content-window (clj->js message) target-origin))))))
        post-context!
        (mf/use-fn
         (mf/deps context-message post-message!)
         #(post-message! context-message))
        motion-relay-message
        (when-let [studio-message (:message motion-message)]
          (case (:type studio-message)
            "studio.preview.recipe"
            (motion-preview/recipe-message
             studio-message artifact (:motion-document component-source))

            "studio.preview.command"
            (motion-preview/command-message studio-message)

            nil))
        post-motion!
        (mf/use-fn
         (mf/deps motion-relay-message post-message!)
         #(when motion-relay-message
            (post-message! motion-relay-message)))
        on-load
        (mf/use-fn
         (mf/deps post-context! post-motion! target-origin)
         (fn [_]
           (mf/set-ref-val! ready-origin-ref target-origin)
           (post-context!)
           (post-motion!)))
        on-message
        (mf/use-fn
         (mf/deps artifact on-motion-state on-state post-context!
                  post-message! post-motion! target-origin)
         (fn [event]
           (let [frame (mf/ref-val frame-ref)]
             (when (and frame
                        (= (.-origin event) target-origin)
                        (runtime-host/same-window?
                         (.-source event)
                         (.-contentWindow frame)))
               (if-let [message (motion-preview/inbound-message (.-data event))]
                 (when on-motion-state (on-motion-state message))
                 (when-let [message (runtime-host/inbound-message (.-data event))]
                   (mf/set-ref-val! ready-origin-ref target-origin)
                   (let [type (:type message)
                         payload (:payload message)]
                     (case type
                       ("runtime.ready" "runtime.context.request")
                       (do
                         (post-context!)
                         (post-motion!))

                       "runtime.artifact.request"
                       (if (and artifact
                                (runtime-host/current-artifact-request?
                                 payload artifact))
                         (post-message! (runtime-host/artifact-message artifact))
                         (post-message!
                          (runtime-host/error-message
                           "web_runtime_artifact_context_changed"
                           "The selected component revision changed before the artifact could be read."
                           true)))

                       "runtime.preview.state"
                       (do
                         (when on-state (on-state payload))
                         (when (= "ready" (:status payload))
                           (post-motion!)))

                       nil))))))))]
    (mf/use-effect
     (mf/deps on-message)
     (fn []
       (.addEventListener js/window "message" on-message)
       #(.removeEventListener js/window "message" on-message)))

    (mf/use-effect
     (mf/deps context-message post-context!)
     (fn []
       (post-context!)
       js/undefined))

    (mf/use-effect
     (mf/deps motion-relay-message post-motion!)
     (fn []
       (post-motion!)
       js/undefined))

    (when (and href artifact)
      [:iframe
       {:ref frame-ref
        :on-load on-load
        :referrer-policy "strict-origin-when-cross-origin"
        :sandbox "allow-same-origin allow-scripts"
        :src href
        :style style
        :title (or title "SayHi live web component runtime")}])))

(mf/defc canvas-runtime*
  {::mf/props :obj}
  [{:keys [file-id page-id component-source shape vbox zoom runtime-uri
           on-state motion-message on-motion-state]}]
  (let [container* (mf/use-state nil)
        layout (component-artifact/canvas-runtime-layout shape vbox zoom)]
    (mf/use-effect
     (mf/deps page-id)
     (fn []
       (reset! container*
               (.getElementById js/document
                                component-artifact/canvas-runtime-root-id))
       js/undefined))

    (when (and @container* layout (:artifact component-source))
      (mf/portal
       (mf/html
        [:section
         {:aria-label "Live SayHi component on the Penpot canvas"
          :style {:position "absolute"
                  :inset-inline-start (:left layout)
                  :inset-block-start (:top layout)
                  :inline-size (:screen-width layout)
                  :block-size (:screen-height layout)
                  :overflow "hidden"
                  :pointer-events "none"}}
         [:> standalone-runtime*
          {:file-id file-id
           :page-id page-id
           :component-source component-source
           :runtime-uri runtime-uri
           :title "Live component motion preview"
           :on-state on-state
           :motion-message motion-message
           :on-motion-state on-motion-state
           :style {:border 0
                   :display "block"
                   :inline-size (:design-width layout)
                   :block-size (:design-height layout)
                   :transform-origin "top left"
                   :transform (str "scale(" (:scale layout) ")")}}]])
       @container*))))
