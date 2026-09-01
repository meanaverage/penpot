;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns app.main.ui.sayhi.motion-editor
  "Thin Penpot dock for the standalone private SayHi Motion Studio."
  (:require
   [app.main.data.sayhi.motion-host.v1 :as motion-host]
   [app.main.data.sayhi.motion-preview.v1 :as motion-preview]
   [rumext.v2 :as mf]))

(mf/defc motion-editor*
  {::mf/props :obj}
  [{:keys [file-id page-id component-source studio-uri theme locale
           canvas-bottom-inset preview-state on-preview on-close]}]
  (let [href (motion-host/host-href studio-uri)
        target-origin (when href (.-origin (js/URL. href)))
        frame-ref (mf/use-ref nil)
        ready-origin-ref (mf/use-ref nil)
        context-message (motion-host/context-message
                         {:file-id file-id
                          :page-id page-id
                          :component-source component-source
                          :theme theme
                          :locale locale})
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
        on-load
        (mf/use-fn
         (mf/deps post-context! target-origin)
         (fn [_]
           (mf/set-ref-val! ready-origin-ref target-origin)
           (post-context!)))
        on-message
        (mf/use-fn
         (mf/deps component-source on-preview post-context! post-message! target-origin)
         (fn [event]
           (let [frame (mf/ref-val frame-ref)]
             (when (and frame
                        (= (.-origin event) target-origin)
                        (motion-host/same-window?
                         (.-source event)
                         (.-contentWindow frame)))
               (when-let [message (motion-host/inbound-message (.-data event))]
                 (mf/set-ref-val! ready-origin-ref target-origin)
                 (let [type (:type message)
                       payload (:payload message)
                       component-id (get-in component-source
                                            [:web-object :component-id])]
                   (case type
                     ("studio.ready" "studio.context.request")
                     (post-context!)

                     "studio.motion.read"
                     (if (= component-id (:componentId payload))
                       (post-message!
                        (motion-host/motion-document-message component-source))
                       (post-message!
                        (motion-host/error-message
                         "motion_selection_changed"
                         "The selected component changed before its motion document could be read."
                         true)))

                     ("studio.preview.recipe" "studio.preview.command")
                     (when on-preview (on-preview message))

                     nil)))))))]
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
     (mf/deps post-message! preview-state)
     (fn []
       (when-let [message (and preview-state
                               (motion-preview/motion-host-message preview-state))]
         (post-message! message))
       js/undefined))

    (when href
      [:section
       {:id "sayhi-motion-studio-dock"
        :aria-label "SayHi Motion Studio"
        :data-testid "sayhi-motion-studio-dock"
        :style {:position "absolute"
                :z-index 30
                :inset-inline "clamp(20rem, 18vw, 24rem)"
                :inset-block-end (str (+ 18 (or canvas-bottom-inset 0)) "px")
                :block-size "clamp(16rem, 34vh, 24rem)"
                :border "1px solid rgba(120, 116, 150, .24)"
                :border-radius "16px"
                :background "var(--color-background-primary)"
                :box-shadow "0 20px 55px rgba(22, 20, 35, .24)"
                :overflow "hidden"}}
       [:button
        {:type "button"
         :aria-label "Close Motion Studio"
         :on-click on-close
         :style {:position "absolute"
                 :z-index 1
                 :inset-block-start "8px"
                 :inset-inline-end "8px"
                 :inline-size "28px"
                 :block-size "28px"
                 :border 0
                 :border-radius "8px"
                 :background "transparent"
                 :cursor "pointer"}}
        "×"]
       [:iframe
        {:ref frame-ref
         :on-load on-load
         :referrer-policy "strict-origin-when-cross-origin"
         :sandbox "allow-same-origin allow-scripts"
         :src href
         :style {:border 0 :inline-size "100%" :block-size "100%"}
         :title "Standalone SayHi Motion Studio"}]])))
