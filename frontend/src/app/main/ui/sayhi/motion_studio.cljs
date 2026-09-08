;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns app.main.ui.sayhi.motion-studio
  (:require-macros [app.main.style :as stl])
  (:require
   [app.config :as cf]
   [app.main.data.sayhi.artifact-motion-playback :as artifact-playback]
   [app.main.data.sayhi.motion-host.document-preview :as document-preview]
   [app.main.data.sayhi.motion-host.v1 :as motion-host-v1]
   [app.main.data.sayhi.motion-studio :as motion-studio]
   [app.main.data.sayhi.studio-canvas :as studio-canvas]
   [app.main.data.sayhi.web-materializer :as web-materializer]
   [app.main.data.sayhi.web-materializer.contract :as materializer-contract]
   [app.main.data.workspace.shortcuts :as sc]
   [app.main.fonts :as fonts]
   [app.main.refs :as refs]
   [app.main.store :as st]
   [app.main.ui.context :as ctx]
   [app.main.ui.icons :as deprecated-icon]
   [app.main.ui.sayhi.motion-canvas-preview :as motion-canvas-preview]
   [app.main.ui.sayhi.motion-dock-sizing :as dock-sizing]
   [app.util.json :as json]
   [beicon.v2.core :as rx]
   [clojure.string :as str]
   [goog.object :as gobj]
   [okulary.core :as l]
   [rumext.v2 :as mf]))

(def ^:private motion-state-ref
  (l/derived #(get-in % [:workspace-local :sayhi-motion-studio]) st/state))

(mf/defc motion-studio-button*
  []
  (let [objects     (mf/deref refs/workspace-page-objects)
        local       (mf/deref refs/workspace-local)
        selected    (:selected local)
        motion      (mf/deref motion-state-ref)
        open?       (true? (:open? motion))
        eligible?   (motion-studio/eligible-selection? objects selected)
        disabled?   (and (not eligible?) (not open?))
        title       (if eligible?
                      (str "Motion Studio (" (sc/get-tooltip :toggle-motion-studio) ")")
                      "Select a SayHi web component to open Motion Studio")
        on-click    (mf/use-fn #(st/emit! (motion-studio/toggle)))]
    (when (motion-studio/enabled?)
      [:li
       [:button
        {:type "button"
         :title title
         :aria-label title
         :aria-controls "sayhi-motion-studio-dock"
         :aria-expanded open?
         :disabled disabled?
         :on-click on-click
         :class (stl/css-case :motion-studio-toolbar-button true
                              :selected open?)
         :data-testid "sayhi-motion-studio-btn"}
        deprecated-icon/play]])))

(mf/defc motion-studio-dock*
  []
  (let [file-id       (mf/use-ctx ctx/current-file-id)
        page-id       (mf/use-ctx ctx/current-page-id)
        objects       (mf/deref refs/workspace-page-objects)
        shapes        (mf/deref refs/selected-shapes)
        vbox          (mf/deref refs/vbox)
        zoom          (mf/deref refs/selected-zoom)
        local         (mf/deref refs/workspace-local)
        profile       (mf/deref refs/profile)
        motion        (mf/deref motion-state-ref)
        open?         (true? (:open? motion))
        collapsed?    (true? (:collapsed? motion))
        studio-canvas? (studio-canvas/studio-canvas-mode? cf/sayhi-surface)
        href          (motion-studio/host-href cf/sayhi-motion-studio-uri)
        web-object    (motion-studio/selected-web-object objects (:selected local))
        web-object-shape-id (:shape-id web-object)
        web-object-revision (:revision web-object)
        web-object-component-id (:component-id web-object)
        web-object-story-id (:story-id web-object)
        canvas-preview? (motion-studio/canvas-preview-surface?
                         cf/sayhi-motion-preview-surface)
        motion-payload (motion-studio/motion-document objects (:selected local))
        materialization
        (mf/use-memo
         ;; selected-web-object returns a fresh map on every render. Depending
         ;; on that map rebuilt the artifact after every preview-state update,
         ;; which changed srcdoc, reloaded the iframe, and destroyed playback.
         ;; The source object graph and stable web-object identity fields are
         ;; the actual materialization inputs.
         (mf/deps objects
                  web-object-shape-id
                  web-object-revision
                  web-object-component-id
                  web-object-story-id
                  cf/sayhi-web-materializer-mode)
         (fn []
           (when web-object
             (web-materializer/materialize
              {:mode cf/sayhi-web-materializer-mode
               :studio-uri cf/sayhi-studio-uri
               :objects objects
               :web-object web-object}))))
        materialization-summary (web-materializer/host-summary materialization)
        artifact      (:artifact materialization)
        portable-preview? (and (= materializer-contract/portable-provider
                                  (:activeProvider materialization))
                               (materializer-contract/portable-artifact? artifact))
        font-css*     (mf/use-state "")
        preview-srcdoc (when portable-preview?
                         (materializer-contract/artifact-srcdoc artifact @font-css*))
        preview-src   (when (and (not portable-preview?)
                                 (motion-studio/native-v2-mode? cf/sayhi-motion-studio-mode)
                                 (or (not canvas-preview?) motion-payload))
                        (motion-studio/motion-preview-href
                         cf/sayhi-studio-uri
                         objects
                         web-object
                         canvas-preview?))
        preview-enabled? (or (some? preview-src) (some? preview-srcdoc))
        preview-shape (get objects (:shape-id web-object))
        context       (motion-studio/host-context
                       {:file-id file-id
                        :page-id page-id
                        :objects objects
                        :selected (:selected local)
                        :shapes shapes
                        :theme (:theme profile)
                        :locale (:lang profile)
                        :preview-enabled? preview-enabled?
                        :materialization materialization-summary})
        message       (motion-studio/context-message context)
        message-key   (json/encode message)
        iframe-ref    (mf/use-ref nil)
        dock-ref      (mf/use-ref nil)
        resize-ref    (mf/use-ref nil)
        preview-ref   (mf/use-ref nil)
        artifact-controller-ref (mf/use-ref nil)
        document-preview-ref (mf/use-ref false)
        host-ready-origin-ref (mf/use-ref nil)
        preview-ready-origin-ref (mf/use-ref nil)
        preview-message-queue-ref (mf/use-ref [])
        target-origin (when href (.-origin (js/URL. href)))
        preview-origin (when preview-src (.-origin (js/URL. preview-src)))

        post-message!
        (mf/use-fn
         (mf/deps target-origin)
         (fn [host-message]
           (when (motion-studio/frame-ready-for-origin?
                  target-origin
                  (mf/ref-val host-ready-origin-ref))
             (when-let [frame (mf/ref-val iframe-ref)]
               (when-let [content-window (.-contentWindow frame)]
                 (.postMessage content-window (clj->js host-message) target-origin))))))

        post-artifact-state!
        (mf/use-fn
         (mf/deps post-message!)
         (fn [state]
           (post-message!
            {:schema motion-studio/schema-name
             :schemaVersion motion-studio/schema-version
             :type "host.preview.state"
             :payload {:status (motion-host-v1/public-preview-status
                                (:status state))
                       :progress (:progress state)
                       :time (:time state)
                       :duration (:duration state)
                       :responseId (:responseId state)
                       :direction (:direction state)}})))

        post-preview!
        (mf/use-fn
         (mf/deps portable-preview? preview-origin)
         (fn [preview-message]
           (if portable-preview?
             (if-let [controller (mf/ref-val artifact-controller-ref)]
               (artifact-playback/dispatch-command! controller preview-message)
               (mf/set-ref-val!
                preview-message-queue-ref
                (motion-studio/enqueue-preview-message
                 (mf/ref-val preview-message-queue-ref)
                 preview-message)))
             (when preview-origin
               (if (motion-studio/frame-ready-for-origin?
                    preview-origin
                    (mf/ref-val preview-ready-origin-ref))
                 (when-let [frame (mf/ref-val preview-ref)]
                   (when-let [content-window (.-contentWindow frame)]
                     (.postMessage content-window (clj->js preview-message) preview-origin)))
                 (mf/set-ref-val!
                  preview-message-queue-ref
                  (motion-studio/enqueue-preview-message
                   (mf/ref-val preview-message-queue-ref)
                   preview-message)))))))

        flush-preview-queue!
        (mf/use-fn
         (mf/deps post-preview! preview-origin)
         (fn []
           (let [queued (mf/ref-val preview-message-queue-ref)]
             (when preview-origin
               (mf/set-ref-val! preview-ready-origin-ref preview-origin))
             (mf/set-ref-val! preview-message-queue-ref [])
             (doseq [preview-message queued]
               (post-preview! preview-message)))))

        post-context!
        (mf/use-fn
         (mf/deps message post-message!)
         (fn []
           (post-message! message)))

        restore-document-preview!
        (mf/use-fn
         (mf/deps artifact post-artifact-state!)
         (fn []
           (when (mf/ref-val document-preview-ref)
             (mf/set-ref-val! document-preview-ref false)
             (when-let [document (some-> (mf/ref-val preview-ref) .-contentDocument)]
               (let [plan       (:motionRuntime artifact)
                     controller (document-preview/replace-controller!
                                 document (mf/ref-val artifact-controller-ref)
                                 plan (:id (first (:responses plan))) post-artifact-state!)]
                 (mf/set-ref-val! artifact-controller-ref controller))))))

        on-load
        (mf/use-fn
         (mf/deps post-context! target-origin restore-document-preview!)
         (fn [_]
           ;; Reloading the Studio frame cannot leave an orphaned proposal
           ;; playing against an apparently canonical timeline.
           (restore-document-preview!)
           (mf/set-ref-val! host-ready-origin-ref target-origin)
           (post-context!)))

        on-preview-load
        (mf/use-fn
         (mf/deps artifact portable-preview? post-artifact-state! post-message! flush-preview-queue!)
         (fn [event]
           (when (mf/ref-val document-preview-ref)
             (post-message!
              {:schema motion-studio/schema-name
               :schemaVersion motion-studio/schema-version
               :type "host.error"
               :payload {:code "motion_preview_reloaded"
                         :message "The artifact preview reloaded. Preview the proposal again before accepting."
                         :retryable true}}))
           (mf/set-ref-val! document-preview-ref false)
           (if portable-preview?
             (let [frame      (.-currentTarget event)
                   document   (.-contentDocument frame)
                   plan       (:motionRuntime artifact)
                   prior      (mf/ref-val artifact-controller-ref)]
               (when prior ((:dispose prior)))
               (if (and document (pos? (:trackCount plan 0)))
                 (let [controller
                       (artifact-playback/create-runtime
                        document
                        plan
                        {:on-state post-artifact-state!})]
                   (mf/set-ref-val! artifact-controller-ref controller)
                   (flush-preview-queue!))
                 (post-message!
                  {:schema motion-studio/schema-name
                   :schemaVersion motion-studio/schema-version
                   :type "host.error"
                   :payload {:code "artifact_motion_plan_unavailable"
                             :message "The portable artifact has no playable motion tracks."
                             :retryable false}})))
             (flush-preview-queue!))))

        on-message
        (mf/use-fn
         (mf/deps file-id page-id context web-object motion-payload artifact portable-preview? post-artifact-state! post-context! post-message! post-preview! target-origin restore-document-preview!)
         (fn [event]
           (let [frame (mf/ref-val iframe-ref)]
             (when (and frame
                        (motion-studio/same-cross-origin-window?
                         (.-source event)
                         (.-contentWindow frame))
                        (= (.-origin event) target-origin))
               (when-let [message (motion-host-v1/inbound-message (.-data event))]
                 (let [type       (:type message)
                       payload    (:payload message)
                       request-id (:requestId message)]
                   (mf/set-ref-val! host-ready-origin-ref target-origin)
                   (cond
                     (contains? #{"studio.ready" "studio.context.request"} type)
                     (post-context!)

                     (and (= "studio.motion.read" type)
                          motion-payload
                          (= (:componentId payload)
                             (:componentId motion-payload)))
                     (post-message! (motion-studio/motion-document-message motion-payload))

                     (= "studio.preview.recipe" type)
                     (if (motion-host-v1/current-preview-recipe?
                          payload
                          motion-payload)
                       (if (document-preview/document-recipe? (:recipe payload))
                         (try
                           (when-not (and portable-preview?
                                          (mf/ref-val artifact-controller-ref)
                                          (some? (some-> (mf/ref-val preview-ref) .-contentDocument)))
                             (throw (ex-info "The native artifact preview is not ready."
                                             {:code "motion_preview_unavailable"})))
                           (let [identity   {:fileId (str file-id)
                                             :pageId (str page-id)
                                             :shapeId (some #(when (:componentId %) (:id %)) (:selection context))}
                                 recipe     (:recipe payload)
                                 plan       (document-preview/prepare-plan artifact motion-payload identity recipe)
                                 _          (mf/set-ref-val! document-preview-ref true)
                                 controller (document-preview/replace-controller!
                                             (.-contentDocument (mf/ref-val preview-ref))
                                             (mf/ref-val artifact-controller-ref)
                                             plan (:responseId recipe) post-artifact-state!)]
                             (mf/set-ref-val! artifact-controller-ref controller)
                             (mf/set-ref-val! document-preview-ref (some? (:document recipe)))
                             (when request-id
                               (post-message!
                                {:schema motion-studio/schema-name
                                 :schemaVersion motion-studio/schema-version
                                 :type "host.ack"
                                 :requestId request-id
                                 :payload {:requestType "studio.preview.recipe"
                                           :revision (:revision motion-payload)}})))
                           (catch :default error
                             (try (restore-document-preview!) (catch :default _ nil))
                             (post-message!
                              (cond-> {:schema motion-studio/schema-name
                                       :schemaVersion motion-studio/schema-version
                                       :type "host.error"
                                       :payload {:code (or (:code (ex-data error)) "motion_preview_failed")
                                                 :message (or (.-message error) "The preview could not be applied.")
                                                 :retryable true}}
                                request-id (assoc :requestId request-id)))))
                         (post-preview!
                          {:type "sayhi:component-motion-command"
                           :action "replace-recipe"
                           :value (:recipe payload)}))
                       (post-message!
                        (cond-> {:schema motion-studio/schema-name
                                 :schemaVersion motion-studio/schema-version
                                 :type "host.error"
                                 :payload {:code "motion_preview_context_changed"
                                           :message "The selected component revision changed before the preview recipe arrived."
                                           :retryable true}}
                          request-id (assoc :requestId request-id))))

                     (= "studio.preview.command" type)
                     (let [{:keys [command direction responseId progress]} payload]
                       (case command
                         "prepare" (when responseId
                                     (post-preview!
                                      {:type "sayhi:component-motion-command"
                                       :action "select-response"
                                       :value responseId}))
                         "play" (post-preview!
                                 {:type "sayhi:component-motion-command"
                                  :action (if (= -1 direction) "reverse" "play")})
                         "pause" (post-preview!
                                  {:type "sayhi:component-motion-command"
                                   :action "pause"})
                         "seek" (post-preview!
                                 {:type "sayhi:component-motion-command"
                                  :action "seek"
                                  :value progress})
                         "simulate" (when-let [simulation
                                               (motion-studio/preview-simulation
                                                motion-payload
                                                responseId)]
                                      (post-preview!
                                       {:type "sayhi:component-motion-command"
                                        :action "simulate-event"
                                        :value simulation}))
                         "reset" (post-preview!
                                  {:type "sayhi:component-motion-command"
                                   :action "restart"})
                         nil))

                     (= "studio.anatomy.highlight" type)
                     (post-preview!
                      {:type "sayhi:component-motion-command"
                       :action "highlight-parts"
                       :value (:partIds payload)})

                     (and (= "studio.motion.write" type)
                          web-object)
                     (st/emit!
                      (motion-studio/write-motion-document
                       {:file-id file-id
                        :page-id page-id
                        :shape-id (:shape-id web-object)
                        :component-id (:componentId payload)
                        :revision (:revision payload)
                        :document (:document payload)
                        :on-result
                        (fn [{:keys [ok revision document code message currentRevision]}]
                          (if ok
                            (do
                              (post-message!
                               (cond-> {:schema motion-studio/schema-name
                                        :schemaVersion motion-studio/schema-version
                                        :type "host.ack"
                                        :payload {:requestType "studio.motion.write"
                                                  :revision revision}}
                                 request-id (assoc :requestId request-id)))
                              (post-message!
                               (motion-studio/motion-document-message
                                {:componentId (:componentId payload)
                                 :revision revision
                                 :document document})))
                            (post-message!
                             (cond-> {:schema motion-studio/schema-name
                                      :schemaVersion motion-studio/schema-version
                                      :type "host.error"
                                      :payload {:code code
                                                :message (if currentRevision
                                                           (str message " Current revision: " currentRevision ".")
                                                           message)
                                                :retryable (= code "motion_revision_conflict")}}
                               request-id (assoc :requestId request-id)))))})))))))))

        on-preview-message
        (mf/use-fn
         (mf/deps flush-preview-queue! post-message! preview-origin)
         (fn [event]
           (let [frame (mf/ref-val preview-ref)
                 data  (.-data event)
                 type  (gobj/get data "type")]
             (when (and frame
                        (motion-studio/same-cross-origin-window?
                         (.-source event)
                         (.-contentWindow frame))
                        (= (.-origin event) preview-origin))
               (flush-preview-queue!)
               (case type
                 "sayhi:component-motion-ready"
                 nil

                 "sayhi:component-motion-state"
                 (let [state  (gobj/get data "state")
                       status (gobj/get state "status")]
                   (post-message!
                    {:schema motion-studio/schema-name
                     :schemaVersion motion-studio/schema-version
                     :type "host.preview.state"
                     :payload {:status (motion-host-v1/public-preview-status
                                        status)
                               :progress (gobj/get state "progress")
                               :time (gobj/get state "time")
                               :duration (gobj/get state "duration")
                               :responseId (gobj/get state "responseId")
                               :direction (if (= status "reversing") -1 1)}}))

                 "sayhi:component-motion-error"
                 (post-message!
                  {:schema motion-studio/schema-name
                   :schemaVersion motion-studio/schema-version
                   :type "host.error"
                   :payload {:code (or (gobj/get data "code")
                                       "component_motion_preview_failed")
                             :message (or (gobj/get data "message")
                                          "The component motion preview failed.")
                             :retryable false}})

                 nil)))))

        close
        (mf/use-fn #(st/emit! (motion-studio/close)))

        toggle-collapsed
        (mf/use-fn #(st/emit! (motion-studio/toggle-collapsed)))]

    (mf/use-effect
     (mf/deps artifact portable-preview?)
     (fn []
       (reset! font-css* "")
       (when (and portable-preview? (seq (:fonts artifact)))
         (let [subscription
               (->> (rx/from (:fonts artifact))
                    (rx/merge-map fonts/fetch-font-css)
                    (rx/reduce conj [])
                    (rx/subs! #(reset! font-css* (str/join "\n" %))))]
           (fn []
             (rx/dispose! subscription))))))

    (mf/use-effect
     (mf/deps artifact portable-preview?)
     (fn []
       (fn []
         (when-let [controller (mf/ref-val artifact-controller-ref)]
           ((:dispose controller))
           (mf/set-ref-val! artifact-controller-ref nil)))))

    (mf/use-effect
     (mf/deps on-message on-preview-message)
     (fn []
       (.addEventListener js/window "message" on-message)
       (.addEventListener js/window "message" on-preview-message)
       (fn []
         (.removeEventListener js/window "message" on-message)
         (.removeEventListener js/window "message" on-preview-message))))

    (mf/use-effect
     (mf/deps message-key open? collapsed?)
     (fn []
       (when (and open? (not collapsed?))
         (post-context!))
       js/undefined))

    (mf/use-effect
     (mf/deps open? collapsed? target-origin)
     (fn []
       (when (and open? (not collapsed?) target-origin
                  (mf/ref-val dock-ref) (mf/ref-val iframe-ref) (mf/ref-val resize-ref))
         (dock-sizing/install! (mf/ref-val dock-ref) (mf/ref-val iframe-ref)
                               (mf/ref-val resize-ref) target-origin))))

    (when (and (motion-studio/enabled?) open? href)
      [:*
       (when (and preview-enabled? canvas-preview?)
         [:> motion-canvas-preview/motion-canvas-preview*
          {:page-id page-id
           :preview-ref preview-ref
           :preview-src preview-src
           :preview-srcdoc preview-srcdoc
           :portable portable-preview?
           :shape preview-shape
           :vbox vbox
           :zoom zoom
           :hidden collapsed?
           :on-load on-preview-load}])
       (when (and preview-enabled? (not canvas-preview?))
         [:section
          {:class (stl/css-case :motion-studio-preview true
                                :collapsed collapsed?
                                :studio-canvas studio-canvas?)
           :aria-label "Live component motion preview"}
          [:iframe
           {:ref preview-ref
            :class (stl/css :motion-studio-preview-frame)
            :hidden collapsed?
            :on-load on-preview-load
            :referrer-policy "strict-origin-when-cross-origin"
            :sandbox (if portable-preview?
                       "allow-same-origin"
                       "allow-forms allow-modals allow-popups allow-same-origin allow-scripts")
            :src preview-src
            :src-doc preview-srcdoc
            :title "Live component motion preview"}]])
       [:aside
        {:id "sayhi-motion-studio-dock"
         :ref dock-ref
         :class (stl/css-case :motion-studio-dock true
                              :collapsed collapsed?
                              :studio-canvas studio-canvas?)
         :aria-label "Motion Studio"
         :data-testid "sayhi-motion-studio-dock"}
        [:div {:ref resize-ref
               :class (stl/css :motion-studio-dock-resize)
               :role "separator"
               :tab-index 0
               :hidden collapsed?
               :aria-orientation "horizontal"
               :aria-label "Resize Motion timeline"
               :aria-controls "sayhi-motion-studio-frame"
               :title "Drag upward to resize. Double-click or press Enter to fit tracks."}]
        [:header {:class (stl/css :motion-studio-dock-header)}
         [:div {:class (stl/css :motion-studio-dock-title)}
          deprecated-icon/play
          [:strong "Motion Studio"]
          [:span (if (seq (:selection context))
                   (get-in context [:selection 0 :name])
                   "No component selected")]]
         [:div {:class (stl/css :motion-studio-dock-actions)}
          [:button
           {:type "button"
            :title (if collapsed? "Expand Motion Studio" "Collapse Motion Studio")
            :aria-label (if collapsed? "Expand Motion Studio" "Collapse Motion Studio")
            :aria-expanded (not collapsed?)
            :on-click toggle-collapsed}
           [:span {:aria-hidden true} (if collapsed? "+" "−")]]
          [:button
           {:type "button"
            :title "Close Motion Studio"
            :aria-label "Close Motion Studio"
            :on-click close}
           deprecated-icon/close-small]]]
        [:iframe
         {:ref iframe-ref
          :id "sayhi-motion-studio-frame"
          :class (stl/css :motion-studio-dock-frame)
          :hidden collapsed?
          :on-load on-load
          :referrer-policy "strict-origin-when-cross-origin"
          :sandbox "allow-same-origin allow-scripts"
          :src href
          :title "SayHi Motion Studio"}]]])))
