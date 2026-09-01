;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS SUBSIDIARY SL

(ns app.main.ui.workspace
  (:require-macros [app.main.style :as stl])
  (:require
   [app.common.data.macros :as dm]
   [app.config :as cf]
   [app.main.data.common :as dcm]
   [app.main.data.helpers :as dsh]
   [app.main.data.persistence :as dps]
   [app.main.data.plugins :as dpl]
   [app.main.data.sayhi.component-artifact :as sayhi.component-artifact]
   [app.main.data.sayhi.surface :as sayhi.surface]
   [app.main.data.workspace :as dw]
   [app.main.features :as features]
   [app.main.fonts :as fonts]
   [app.main.refs :as refs]
   [app.main.router :as-alias rt]
   [app.main.store :as st]
   [app.main.ui.context :as ctx]
   [app.main.ui.ds.product.loader :refer [loader*]]
   [app.main.ui.hooks :as hooks]
   [app.main.ui.hooks.resize :refer [use-resize-observer]]
   [app.main.ui.modal :refer [modal-container*]]
   [app.main.ui.sayhi.motion-context :as sayhi.motion-context]
   [app.main.ui.sayhi.motion-editor :refer [motion-editor*]]
   [app.main.ui.sayhi.surface-chrome :refer [surface-chrome-host*]]
   [app.main.ui.sayhi.web-runtime :refer [canvas-runtime*]]
   [app.main.ui.workspace.colorpicker]
   [app.main.ui.workspace.components-debugger :refer [components-debugger*]]
   [app.main.ui.workspace.context-menu :refer [context-menu*]]
   [app.main.ui.workspace.coordinates :as coordinates]
   [app.main.ui.workspace.libraries]
   [app.main.ui.workspace.nudge]
   [app.main.ui.workspace.palette :refer [palette*]]
   [app.main.ui.workspace.plugins]
   [app.main.ui.workspace.sidebar :refer [sidebar*]]
   [app.main.ui.workspace.sidebar.history :refer [history-toolbox*]]
   [app.main.ui.workspace.tokens.export]
   [app.main.ui.workspace.tokens.export.modal]
   [app.main.ui.workspace.tokens.import]
   [app.main.ui.workspace.tokens.import.modal]
   [app.main.ui.workspace.tokens.management.forms.modals]
   [app.main.ui.workspace.tokens.management.forms.rename-node-modal]
   [app.main.ui.workspace.tokens.remapping-modal]
   [app.main.ui.workspace.tokens.settings]
   [app.main.ui.workspace.tokens.themes.create-modal]
   [app.main.ui.workspace.viewport :refer [viewport*]]
   [app.main.ui.workspace.webgl-unavailable-modal]
   [app.util.debug :as dbg]
   [app.util.dom :as dom]
   [app.util.i18n :as i18n :refer [tr]]
   [goog.events :as events]
   [okulary.core :as l]
   [rumext.v2 :as mf]))

(mf/defc workspace-content*
  {::mf/private true}
  [{:keys [file layout page wglobal on-palette-inset-change]}]

  (let [palete-size (mf/use-state nil)
        selected    (mf/deref refs/selected-shapes)
        page-id     (get page :id)

        vport       (mf/deref refs/workspace-vport)
        {:keys [options-mode]} wglobal


        ;; FIXME: pass this down to viewport and reuse it from here
        ;; instead of making an other deref on viewport for the same
        ;; data
        drawing
        (mf/deref refs/workspace-drawing)

        colorpalette?  (:colorpalette layout)
        textpalette?   (:textpalette layout)
        hide-ui?       (:hide-ui layout)

        on-resize
        (mf/use-fn
         (mf/deps vport)
         (fn [resize-type size]
           (when (and vport (not= size vport))
             (st/emit! (dw/update-viewport-size resize-type size)
                       (dw/sync-wasm-workspace-viewport)))))

        on-resize-palette
        (mf/use-fn
         (fn [size]
           (reset! palete-size size)))

        node-ref (use-resize-observer on-resize)]

    (mf/with-effect [layout @palete-size on-palette-inset-change]
      (when on-palette-inset-change
        (on-palette-inset-change
         (sayhi.surface/canvas-bottom-inset layout @palete-size))))

    [:*
     (when (not ^boolean hide-ui?)
       [:> palette* {:layout layout
                     :on-change-size on-resize-palette}])

     [:section
      {:key (dm/str "workspace-" page-id)
       :class (stl/css :workspace-content)
       :ref node-ref}

      [:section {:class (stl/css :workspace-viewport)}
       (when (dbg/enabled? :coordinates)
         [:> coordinates/coordinates* {:is-colorpalette colorpalette?}])

       (when (dbg/enabled? :history-overlay)
         [:div {:class (stl/css :history-debug-overlay)}
          [:button {:on-click #(st/emit! dw/reinitialize-undo)} "CLEAR"]
          [:> history-toolbox*]])

       [:> viewport*
        {:file file
         :page page
         :wglobal wglobal
         :selected selected
         :layout layout
         :palete-size
         (when (and (or colorpalette? textpalette?) (not hide-ui?))
           @palete-size)}]]]

     (when-not hide-ui?
       [:> sidebar* {:layout layout
                     ;; FIXME
                     :file-id (get file :id)
                     :page-id page-id
                     :file file
                     :selected selected
                     :section options-mode
                     :drawing-tool (get drawing :tool)}])]))

(mf/defc workspace-loader*
  {::mf/private true}
  []
  [:> loader*  {:title (tr "labels.loading")
                :class (stl/css :workspace-loader)
                :overlay true
                :file-loading true}])

(defn- make-team-ref
  [team-id]
  (l/derived (fn [state]
               (let [teams (get state :teams)]
                 (get teams team-id)))
             st/state))

(defn- make-file-ref
  [file-id]
  (l/derived (fn [state]
               ;; NOTE: for ensure ordering of execution, we need to
               ;; wait the file initialization completly success until
               ;; mark this file availablea and unlock the rendering
               ;; of the following components
               (when (= (get state :current-file-id) file-id)
                 (let [files (get state :files)
                       file  (get files file-id)]
                   (-> file
                       (dissoc :data)
                       (assoc ::has-data (contains? file :data))))))
             st/state
             =))

(defn- make-page-ref
  [file-id page-id]
  (l/derived (fn [state]
               (let [current-page-id (get state :current-page-id)]
                 ;; NOTE: for ensure ordering of execution, we need to
                 ;; wait the page initialization completly success until
                 ;; mark this file availablea and unlock the rendering
                 ;; of the following components
                 (when (= current-page-id page-id)
                   (dsh/lookup-page state file-id page-id))))
             st/state))

(mf/defc workspace-inner*
  {::mf/private true}
  [{:keys [page-id file-id file layout wglobal on-palette-inset-change]}]
  (let [page-ref (mf/with-memo [file-id page-id]
                   (make-page-ref file-id page-id))
        page     (mf/deref page-ref)]

    (mf/with-effect []
      (let [focus-out #(st/emit! (dw/workspace-focus-lost))
            key       (events/listen js/window "blur" focus-out)]
        (partial events/unlistenByKey key)))

    (mf/with-effect [file-id page-id]
      (st/emit! (dw/initialize-page file-id page-id))
      (fn []
        (st/emit! (dw/finalize-page file-id page-id))))

    (if (some? page)
      [:> workspace-content* {:file file
                              :page page
                              :wglobal wglobal
                              :layout layout
                              :on-palette-inset-change on-palette-inset-change}]
      [:> workspace-loader*])))

(mf/defc workspace*
  {::mf/wrap [mf/memo]}
  [{:keys [team-id project-id file-id page-id layout-name]}]

  (let [layout           (mf/deref refs/workspace-layout)
        wglobal          (mf/deref refs/workspace-global)

        team-ref         (mf/with-memo [team-id]
                           (make-team-ref team-id))
        file-ref         (mf/with-memo [file-id]
                           (make-file-ref file-id))

        team             (mf/deref team-ref)
        file             (mf/deref file-ref)

        file-loaded?     (get file ::has-data)

        objects          (mf/deref refs/workspace-page-objects)
        local            (mf/deref refs/workspace-local)
        profile          (mf/deref refs/profile)

        file-name        (:name file)
        permissions      (:permissions team)

        read-only?       (mf/deref refs/workspace-read-only?)
        read-only?       (or read-only? (not (:can-edit permissions)))

        design-tokens?   (features/use-feature "design-tokens/v1")

        wasm-renderer-enabled? (features/use-feature "render-wasm/v1")

        first-frame-rendered?  (mf/use-state false)

        surface?         (sayhi.surface/enabled?
                          cf/sayhi-surface
                          (.-search (.-location js/window))
                          (.-hash (.-location js/window)))
        palette-inset*   (mf/use-state 0)
        motion-open*     (mf/use-state false)
        preview-message* (mf/use-state nil)
        motion-state*    (mf/use-state nil)
        controls-visible (not (contains? layout :hide-ui))

        component-source (when surface?
                           (sayhi.component-artifact/resolve-selected-component
                            objects
                            (:selected local)))
        component-shape  (when component-source
                           (get objects (:shape-id component-source)))
        motion-available (and (string? cf/sayhi-motion-studio-uri)
                              (string? cf/sayhi-web-runtime-uri)
                              (some? (:artifact component-source))
                              (some? (:motion-document component-source)))

        on-palette-inset-change
        (mf/use-fn
         (fn [value]
           (reset! palette-inset* value)))

        on-toggle-controls
        (mf/use-fn
         (fn []
           (st/emit! (dw/toggle-layout-flag :hide-ui))))

        on-toggle-motion
        (mf/use-fn
         (mf/deps motion-available)
         (fn []
           (when motion-available
             (swap! motion-open* not))))

        on-close-motion
        (mf/use-fn #(reset! motion-open* false))

        motion-controls
        (when surface?
          {:available? motion-available
           :open? @motion-open*
           :on-toggle on-toggle-motion})

        on-motion-preview
        (mf/use-fn
         (fn [message]
           (reset! preview-message*
                   {:sequence (random-uuid)
                    :message message})))

        on-motion-state
        (mf/use-fn
         (fn [message]
           (reset! motion-state* message)))

        background-color (:background-color wglobal)]

    (mf/with-effect []
      (st/emit! (dps/initialize-persistence)
                (dpl/update-plugins-permissions-peek)))

    ;; FLAG :font-preview — prefetch the preview sprite markup on workspace mount
    ;; (kept in memory, not the DOM) so the typography selector renders previews on
    ;; open with no network wait. Remove the flag check to drop the feature.
    (mf/with-effect []
      (when (contains? cf/flags :font-preview)
        (fonts/prefetch-preview-sprite!)))

    ;; Setting the layout preset by its name
    (mf/with-effect [layout-name]
      (st/emit! (dw/initialize-workspace-layout layout-name)))

    (mf/with-effect [file-name]
      (when file-name
        (dom/set-html-title (tr "title.workspace" file-name))))

    (mf/with-effect [team-id file-id]
      (st/emit! (dw/initialize-workspace team-id file-id))
      (fn []
        (st/emit! ::dps/force-persist
                  (dw/finalize-workspace team-id file-id))))

    (mf/with-effect [file-id page-id file-loaded?]
      (when (and file-loaded? (not page-id))
        (st/emit! (dcm/go-to-workspace :file-id file-id ::rt/replace true))))

    (mf/with-effect [motion-available]
      (when-not motion-available
        (reset! motion-open* false)
        (reset! preview-message* nil)
        (reset! motion-state* nil)))

    (mf/with-effect [file-id page-id]
      (reset! first-frame-rendered? false))

    (mf/with-effect []
      (let [handle-wasm-render
            (fn [_]
              (reset! first-frame-rendered? true))
            listener-key (events/listen js/document "penpot:wasm:render" handle-wasm-render)]
        (fn []
          (events/unlistenByKey listener-key))))

    [:> (mf/provider ctx/current-project-id) {:value project-id}
     [:> (mf/provider ctx/current-file-id) {:value file-id}
      [:> (mf/provider ctx/current-page-id) {:value page-id}
       [:> (mf/provider ctx/design-tokens) {:value design-tokens?}
        [:> (mf/provider ctx/workspace-read-only?) {:value read-only?}
         [:> (mf/provider sayhi.motion-context/controls) {:value motion-controls}
          [:> modal-container*]
          [:> components-debugger*]
          [:section {:class (stl/css-case :workspace true
                                          :sayhi-studio-surface surface?)
                     :style {:background-color background-color
                             :touch-action "none"
                             :position "relative"
                             :--sayhi-canvas-bottom-inset (dm/str @palette-inset* "px")}}
           [:> context-menu*]
           (when (and file-loaded? page-id)
             [:> workspace-inner*
              {:page-id page-id
               :file-id file-id
               :file file
               :wglobal wglobal
               :layout layout
               :on-palette-inset-change on-palette-inset-change}])

           (when surface?
             [:> surface-chrome-host*
              {:controls-visible controls-visible
               :on-toggle-controls on-toggle-controls
               :canvas-bottom-inset @palette-inset*
               :motion-available motion-available
               :motion-open @motion-open*
               :on-toggle-motion on-toggle-motion}])

           (when (and surface? @motion-open* component-shape)
             [:> canvas-runtime*
              {:file-id file-id
               :page-id page-id
               :component-source component-source
               :shape component-shape
               :vbox (:vbox local)
               :zoom (:zoom local)
               :runtime-uri cf/sayhi-web-runtime-uri
               :motion-message @preview-message*
               :on-motion-state on-motion-state}])

           (when (and surface? @motion-open* motion-available)
             [:> motion-editor*
              {:file-id file-id
               :page-id page-id
               :component-source component-source
               :studio-uri cf/sayhi-motion-studio-uri
               :theme (:theme profile)
               :locale (:lang profile)
               :canvas-bottom-inset @palette-inset*
               :preview-state @motion-state*
               :on-preview on-motion-preview
               :on-close on-close-motion}])

           (when (or (not (and file-loaded? page-id))
                     ;; in wasm renderer, extend the pixel loader until the first frame is rendered
                     ;; but do not apply it when switching pages
                     (and wasm-renderer-enabled?
                          (not file-loaded?)
                          (not @first-frame-rendered?)))
             [:> workspace-loader*])]]]]]]]))

(mf/defc workspace-page*
  {::mf/lazy-load true}
  [{:keys [file-id page-id] :as props}]
  (let [file-id (hooks/use-equal-memo file-id)
        page-id (hooks/use-equal-memo page-id)
        props   (mf/spread-props props {:file-id file-id
                                        :page-id page-id})]

    (when (uuid? file-id)
      [:> workspace* props])))
