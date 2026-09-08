;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns app.main.ui.sayhi.web-preview
  (:require-macros [app.main.style :as stl])
  (:require
   [app.main.data.sayhi.artifact-motion-playback :as artifact-playback]
   [app.main.data.sayhi.web-materializer.contract :as contract]
   [app.main.data.sayhi.web-runtime-host.v1 :as runtime-host]
   [app.main.fonts :as fonts]
   [beicon.v2.core :as rx]
   [clojure.string :as str]
   [goog.object :as gobj]
   [rumext.v2 :as mf]))

(mf/defc standalone-runtime*
  {::mf/props :obj}
  [{:keys [file-id page-id component-id artifact runtime-uri shadow on-state]}]
  (let [href                  (runtime-host/host-href runtime-uri)
        target-origin         (when href (.-origin (js/URL. href)))
        iframe-ref            (mf/use-ref nil)
        host-ready-origin-ref (mf/use-ref nil)
        artifact-component-id (get-in artifact [:identity :componentId])
        revision              (get-in artifact [:identity :revision])
        root-shape-id         (get-in artifact [:identity :rootShapeId])
        theme                 (or (some-> js/document
                                          .-documentElement
                                          .-dataset
                                          (gobj/get "theme"))
                                  "light")
        locale                (or (.-language js/navigator) "en")
        context-message       (runtime-host/context-message
                               {:file-id file-id
                                :page-id page-id
                                :root-shape-id root-shape-id
                                :component-id artifact-component-id
                                :revision revision
                                :component-name component-id
                                :theme theme
                                :locale locale})
        post-message!
        (mf/use-fn
         (mf/deps target-origin)
         (fn [message]
           (when (= target-origin (mf/ref-val host-ready-origin-ref))
             (when-let [frame (mf/ref-val iframe-ref)]
               (when-let [content-window (.-contentWindow frame)]
                 (.postMessage content-window (clj->js message) target-origin))))))
        post-context!
        (mf/use-fn
         (mf/deps context-message post-message!)
         (fn []
           (post-message! context-message)))
        on-load
        (mf/use-fn
         (mf/deps post-context! target-origin)
         (fn [_]
           (mf/set-ref-val! host-ready-origin-ref target-origin)
           (post-context!)))
        on-message
        (mf/use-fn
         (mf/deps artifact post-context! post-message! on-state target-origin)
         (fn [event]
           (let [frame (mf/ref-val iframe-ref)]
             (when (and frame
                        (runtime-host/same-cross-origin-window?
                         (.-source event)
                         (.-contentWindow frame))
                        (= (.-origin event) target-origin))
               (when-let [message (runtime-host/inbound-message (.-data event))]
                 (mf/set-ref-val! host-ready-origin-ref target-origin)
                 (let [type    (:type message)
                       payload (:payload message)]
                   (cond
                     (contains? #{"runtime.ready" "runtime.context.request"} type)
                     (post-context!)

                     (= "runtime.artifact.request" type)
                     (if (runtime-host/current-artifact-request? payload artifact)
                       (post-message! (runtime-host/artifact-message artifact))
                       (post-message!
                        (runtime-host/error-message
                         "web_runtime_artifact_context_changed"
                         "The selected component revision changed before the artifact could be read."
                         true)))

                     (= "runtime.preview.state" type)
                     (when on-state
                       (on-state payload)))))))))]
    (mf/use-effect
     (mf/deps on-message)
     (fn []
       (.addEventListener js/window "message" on-message)
       (fn []
         (.removeEventListener js/window "message" on-message))))
    (mf/use-effect
     (mf/deps context-message post-context!)
     (fn []
       (post-context!)
       js/undefined))
    (when href
      [:iframe
       {:ref iframe-ref
        :aria-hidden (true? shadow)
        :class (stl/css-case :web-preview-frame true
                             :web-preview-shadow-frame (true? shadow))
        :on-load on-load
        :referrer-policy "strict-origin-when-cross-origin"
        :sandbox "allow-same-origin allow-scripts"
        :src href
        :tab-index (when shadow -1)
        :title (if shadow
                 (str "Shadow web runtime for " component-id)
                 (str "Standalone web runtime for " component-id))}])))

(mf/defc web-preview-page*
  {::mf/props :obj}
  [{:keys [component-id file-id page-id provider artifact href runtime-mode runtime-uri]}]
  (let [loaded* (mf/use-state false)
        fidelity* (mf/use-state nil)
        runtime-error* (mf/use-state nil)
        font-css* (mf/use-state "")
        provider* (mf/use-state provider)
        iframe-ref (mf/use-ref nil)
        artifact-controller-ref (mf/use-ref nil)
        artifact? (contract/portable-artifact? artifact)
        compare? (and artifact? (string? href))
        portable? (= @provider* contract/portable-provider)
        runtime-mode (runtime-host/normalize-mode runtime-mode)
        runtime-href (runtime-host/host-href runtime-uri)
        standalone-active? (and portable?
                                (= runtime-mode runtime-host/standalone-mode)
                                (some? runtime-href))
        standalone-shadow? (and portable?
                                (= runtime-mode runtime-host/shadow-mode)
                                (some? runtime-href))
        srcdoc (when (and portable? (not standalone-active?))
                 (contract/artifact-srcdoc artifact @font-css*))
        dispose-artifact-controller!
        (mf/use-callback
         (fn []
           (when-let [{:keys [controller document on-key-down]}
                      (mf/ref-val artifact-controller-ref)]
             (when (and document on-key-down)
               (.removeEventListener document "keydown" on-key-down))
             ((:dispose controller))
             (mf/set-ref-val! artifact-controller-ref nil))))
        select-projection
        (mf/use-callback
         (mf/deps artifact)
         (fn [_]
           (reset! loaded* false)
           (reset! fidelity* nil)
           (reset! runtime-error* nil)
           (reset! provider* contract/projection-provider)))
        select-portable
        (mf/use-callback
         (mf/deps artifact)
         (fn [_]
           (reset! loaded* false)
           (reset! fidelity* (:fidelity artifact))
           (reset! runtime-error* nil)
           (reset! provider* contract/portable-provider)))
        on-runtime-state
        (mf/use-fn
         (mf/deps artifact standalone-active?)
         (fn [state]
           (when standalone-active?
             (case (:status state)
               "ready"
               (do
                 (reset! fidelity* (or (:fidelity state) (:fidelity artifact)))
                 (reset! runtime-error* nil)
                 (reset! loaded* true))

               "loading"
               (do
                 (reset! runtime-error* nil)
                 (reset! loaded* false))

               "error"
               (do
                 (reset! runtime-error* (:error state))
                 (reset! loaded* true))

               nil))))
        on-load
        (mf/use-callback
         (mf/deps artifact portable? dispose-artifact-controller!)
         (fn [event]
           (dispose-artifact-controller!)
           (when portable?
             (let [frame    (.-currentTarget event)
                   document (.-contentDocument frame)
                   plan     (:motionRuntime artifact)]
               (when (and document (pos? (:trackCount plan 0)))
                 (let [controller (artifact-playback/create-runtime document plan)
                       on-key-down
                       (fn [key-event]
                         (when-let [direction
                                    (artifact-playback/keyboard-direction
                                     (.-key key-event)
                                     (.-shiftKey key-event))]
                           (.preventDefault key-event)
                           ((:trigger controller)
                            {:detail {:direction direction}})))]
                   (.addEventListener document "keydown" on-key-down)
                   (mf/set-ref-val!
                    artifact-controller-ref
                    {:controller controller
                     :document document
                     :on-key-down on-key-down})))))
           (reset! loaded* true)))
        on-message
        (mf/use-callback
         (mf/deps href)
         (fn [event]
           (let [frame (mf/ref-val iframe-ref)
                 data  (.-data event)]
             (when (and frame
                        (string? href)
                        (identical? (.-source event) (.-contentWindow frame))
                        (= (.-origin event) (.-origin (js/URL. href)))
                        (= (gobj/get data "type") "sayhi:component-fidelity"))
               (reset! fidelity* (js->clj (gobj/get data "fidelity") :keywordize-keys true))))))]
    (mf/use-effect
     (mf/deps artifact provider)
     (fn []
       (reset! loaded* false)
       (reset! provider* provider)
       (reset! runtime-error* nil)
       (reset! fidelity* (when (= provider contract/portable-provider)
                           (:fidelity artifact)))
       (reset! font-css* "")
       (when (and artifact? (seq (:fonts artifact)))
         (let [subscription
               (->> (rx/from (:fonts artifact))
                    (rx/merge-map fonts/fetch-font-css)
                    (rx/reduce conj [])
                    (rx/subs! #(reset! font-css* (str/join "\n" %))))]
           (fn []
             (rx/dispose! subscription))))))
    (mf/use-effect
     (mf/deps on-message)
     (fn []
       (.addEventListener js/window "message" on-message)
       (fn []
         (.removeEventListener js/window "message" on-message))))
    (mf/use-effect
     (mf/deps artifact portable? dispose-artifact-controller!)
     (fn []
       (fn []
         (dispose-artifact-controller!))))
    [:main {:class (stl/css :web-preview)}
     (if (or (some? href) (some? srcdoc) standalone-active?)
       [:*
        (when-not @loaded*
          [:div {:class (stl/css :web-preview-status)
                 :role "status"}
           "Opening live component…"])
        (when compare?
          [:nav {:class (stl/css :web-preview-provider-switch)
                 :aria-label "Preview implementation"}
           [:button
            {:type "button"
             :aria-pressed (not portable?)
             :on-click select-projection}
            "Runtime v1"]
           [:button
            {:type "button"
             :aria-pressed portable?
             :on-click select-portable}
            "Artifact v2"]])
        (if standalone-active?
          [:> standalone-runtime*
           {:file-id file-id
            :page-id page-id
            :component-id component-id
            :artifact artifact
            :runtime-uri runtime-uri
            :on-state on-runtime-state}]
          [:iframe
           {:ref iframe-ref
            :allow "clipboard-read; clipboard-write"
            :class (stl/css :web-preview-frame)
            :on-load on-load
            :referrer-policy "strict-origin-when-cross-origin"
            :sandbox (if portable?
                       "allow-forms allow-same-origin"
                       "allow-forms allow-modals allow-popups allow-same-origin allow-scripts")
            :src (when-not portable? href)
            :src-doc srcdoc
            :title (str "Live preview of " component-id)}])
        (when standalone-shadow?
          [:> standalone-runtime*
           {:file-id file-id
            :page-id page-id
            :component-id component-id
            :artifact artifact
            :runtime-uri runtime-uri
            :shadow true}])
        (when @runtime-error*
          [:aside {:class (stl/css :web-preview-runtime-error)
                   :role "alert"}
           (or (:message @runtime-error*)
               "The standalone web runtime could not render this artifact.")])
        (when (= "partial" (:status @fidelity*))
          (let [count (count (or (:missing @fidelity*) (:issues @fidelity*)))]
            [:aside {:class (stl/css :web-preview-fidelity)
                     :role "status"}
             (str "Preview has " count " unresolved fidelity "
                  (if (= 1 count) "item" "items") ".")]))]
       [:section {:class (stl/css :web-preview-error)
                  :role "alert"}
        [:h1 "Live preview unavailable"]
        [:p "This object does not have a valid SayHi runtime target."]])]))
