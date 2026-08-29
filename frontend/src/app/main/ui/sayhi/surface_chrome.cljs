;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns app.main.ui.sayhi.surface-chrome
  (:require-macros [app.main.style :as stl])
  (:require
   [app.config :as cf]
   [app.main.data.sayhi.surface :as surface]
   [app.main.data.sayhi.surface-chrome.v1 :as chrome-v1]
   [app.main.refs :as refs]
   [rumext.v2 :as mf]))

(mf/defc surface-chrome-host*
  [{:keys [controls-visible on-toggle-controls canvas-bottom-inset
           motion-available motion-open on-toggle-motion]}]
  (let [shapes        (mf/deref refs/selected-shapes)
        profile       (mf/deref refs/profile)
        location-href (.-href (.-location js/window))
        location      (js/URL. location-href)
        active-app    (surface/active-app (.-search location) (.-hash location))
        href          (chrome-v1/host-href
                       cf/sayhi-studio-chrome-uri
                       {:active-app active-app
                        :components-uri (surface/app-location-href location-href :components)
                        :pages-uri (surface/app-location-href location-href :pages)})
        target-origin (when href (.-origin (js/URL. href)))
        frame-ref     (mf/use-ref nil)
        ready-origin-ref (mf/use-ref nil)
        height*       (mf/use-state 80)
        context       {:theme (chrome-v1/public-theme (:theme profile))
                       :locale (chrome-v1/public-locale (:lang profile))
                       :selection (chrome-v1/selection-context shapes)
                       :canvasBottomInset (chrome-v1/public-canvas-inset canvas-bottom-inset)
                       :controlsVisible (true? controls-visible)
                       :motion {:available (true? motion-available)
                                :open (true? motion-open)}}
        message       (chrome-v1/context-message context)

        post-context!
        (mf/use-fn
         (mf/deps message target-origin)
         (fn []
           (when (= target-origin (mf/ref-val ready-origin-ref))
             (when-let [frame (mf/ref-val frame-ref)]
               (when-let [content-window (.-contentWindow frame)]
                 (.postMessage content-window (clj->js message) target-origin))))))

        on-load
        (mf/use-fn
         (mf/deps post-context! target-origin)
         (fn [_]
           (mf/set-ref-val! ready-origin-ref target-origin)
           (post-context!)))

        on-message
        (mf/use-fn
         (mf/deps on-toggle-controls on-toggle-motion post-context! target-origin)
         (fn [event]
           (let [frame (mf/ref-val frame-ref)]
             (when (and frame
                        (chrome-v1/same-window? (.-source event) (.-contentWindow frame))
                        (= (.-origin event) target-origin))
               (when-let [message (chrome-v1/inbound-message (.-data event))]
                 (mf/set-ref-val! ready-origin-ref target-origin)
                 (case (:type message)
                   ("studio.ready" "studio.context.request") (post-context!)
                   "studio.bounds" (reset! height* (get-in message [:payload :height]))
                   "studio.command"
                   (case (get-in message [:payload :command])
                     "motion.toggle" (when on-toggle-motion (on-toggle-motion))
                     "penpot.controls.toggle" (when on-toggle-controls (on-toggle-controls))
                     nil)
                   nil))))))]

    (mf/use-effect
     (mf/deps on-message)
     (fn []
       (.addEventListener js/window "message" on-message)
       #(.removeEventListener js/window "message" on-message)))

    (mf/use-effect
     (mf/deps message post-context!)
     (fn []
       (post-context!)
       js/undefined))

    (when href
      [:iframe
       {:ref frame-ref
        :class (stl/css :surface-chrome-host)
        :src href
        :title "SayHi Studio controls"
        :data-studio-app (name active-app)
        :height @height*
        :style {:height (str @height* "px")}
        :on-load on-load}])))
