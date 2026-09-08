;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns app.main.ui.sayhi.studio-chrome
  (:require-macros [app.main.style :as stl])
  (:require
   [app.config :as cf]
   [app.main.data.sayhi.image-component :as image-component]
   [app.main.data.sayhi.motion-studio :as motion-studio]
   [app.main.data.sayhi.studio-canvas :as studio-canvas]
   [app.main.data.sayhi.studio-chrome.v1 :as chrome-v1]
   [app.main.refs :as refs]
   [app.main.store :as st]
   [rumext.v2 :as mf]))

(mf/defc studio-chrome-host*
  [{:keys [controls-visible on-toggle-controls canvas-bottom-inset]}]
  (let [objects       (mf/deref refs/workspace-page-objects)
        local         (mf/deref refs/workspace-local)
        shapes        (mf/deref refs/selected-shapes)
        profile       (mf/deref refs/profile)
        motion-open?  (true? (get-in local [:sayhi-motion-studio :open?]))
        motion-eligible? (motion-studio/eligible-selection? objects (:selected local))
        location-href* (mf/use-state (.-href (.-location js/window)))
        location      (js/URL. @location-href*)
        active-app    (studio-canvas/studio-app-from-location
                       (.-search location)
                       (.-hash location))
        components-uri (studio-canvas/studio-app-location-href
                        (.-href location)
                        :components)
        pages-uri     (studio-canvas/studio-app-location-href
                       (.-href location)
                       :pages)
        href          (chrome-v1/host-href
                       cf/sayhi-studio-chrome-uri
                       cf/sayhi-motion-studio-uri
                       {:active-app active-app
                        :components-uri components-uri
                        :pages-uri pages-uri})
        target-origin (when href (.-origin (js/URL. href)))
        frame-ref     (mf/use-ref nil)
        ready-origin-ref (mf/use-ref nil)
        image-handler (mf/use-memo (fn [] (image-component/create-handler)))
        ;; The private surface uses a 1rem transparent block gutter around the
        ;; 3rem prompt. The iframe must include that paint area or the browser
        ;; clips the prompt shadow at the cross-origin viewport boundary.
        height*       (mf/use-state 80)
        width*        (mf/use-state nil)
        context       {:theme (chrome-v1/public-theme (:theme profile))
                       :locale (chrome-v1/public-locale (:lang profile))
                       :selection (chrome-v1/selection-context shapes)
                       :canvasBottomInset (or canvas-bottom-inset 0)
                       :controlsVisible (true? controls-visible)
                       :motion {:available (true? motion-eligible?)
                                :open motion-open?}}
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
         (mf/deps on-toggle-controls post-context! target-origin image-handler)
         (fn [event]
           (let [frame (mf/ref-val frame-ref)]
             (when (and frame
                        (chrome-v1/same-window?
                         (.-source event)
                         (.-contentWindow frame))
                        (= (.-origin event) target-origin))
               (image-handler (.-data event)
                              (fn [message]
                                (.postMessage (.-contentWindow frame) message target-origin)))
               (when-let [message (chrome-v1/inbound-message (.-data event))]
                 (mf/set-ref-val! ready-origin-ref target-origin)
                 (case (:type message)
                   ("studio.ready" "studio.context.request")
                   (post-context!)

                   "studio.bounds"
                   (reset! height* (get-in message [:payload :height]))

                   "studio.width"
                   (reset! width* (get-in message [:payload :width]))

                   "studio.command"
                   (case (get-in message [:payload :command])
                     "motion.toggle" (st/emit! (motion-studio/toggle))
                     "penpot.controls.toggle" (when on-toggle-controls
                                                (on-toggle-controls))
                     nil)
                   nil))))))]

    (mf/use-effect
     (fn []
       (let [sync-location
             (fn []
               (reset! location-href* (.-href (.-location js/window))))]
         (.addEventListener js/window "hashchange" sync-location)
         (.addEventListener js/window "popstate" sync-location)
         (fn []
           (.removeEventListener js/window "hashchange" sync-location)
           (.removeEventListener js/window "popstate" sync-location)))))

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
        :class (stl/css :studio-chrome-host)
        :src href
        :title "SayHi Studio controls"
        :data-studio-app (name active-app)
        :height @height*
        :style {:height (str @height* "px")
                :--sayhi-studio-chrome-width (when @width* (str @width* "px"))}
        :on-load on-load}])))
