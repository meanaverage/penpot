;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns app.main.ui.sayhi.page-canvas-preview
  (:require-macros [app.main.style :as stl])
  (:require
   [app.main.data.sayhi.motion-studio :as motion-studio]
   [app.main.data.sayhi.page-preview :as page-preview]
   [app.main.data.sayhi.studio-canvas :as studio-canvas]
   [app.main.data.sayhi.web-materializer.contract :as contract]
   [app.main.data.sayhi.web-materializer.portable-v2 :as portable-v2]
   [app.main.fonts :as fonts]
   [app.main.refs :as refs]
   [beicon.v2.core :as rx]
   [clojure.string :as str]
   [rumext.v2 :as mf]))

(def ^:private mode-options
  [{:id :design :label "Design" :description "Editable Penpot layers"}
   {:id :live :label "Live Web" :description "Portable browser rendition"}
   {:id :compare :label "Compare" :description "Blend web and design"}])

(mf/defc page-canvas-preview*
  [{:keys [page-id]}]
  (let [objects       (mf/deref refs/workspace-page-objects)
        local         (mf/deref refs/workspace-local)
        vbox          (mf/deref refs/vbox)
        zoom          (mf/deref refs/selected-zoom)
        location-href* (mf/use-state (.-href (.-location js/window)))
        location      (js/URL. @location-href*)
        active-app    (studio-canvas/studio-app-from-location
                       (.-search location)
                       (.-hash location))
        page          (when (= :pages active-app)
                        (page-preview/resolve-page-root
                         objects
                         (:selected local)))
        page-shape    (get objects (:shape-id page))
        page-object   (page-preview/web-object page)
        mode*         (mf/use-state :live)
        mode          (page-preview/normalize-preview-mode @mode*)
        container*    (mf/use-state nil)
        container     @container*
        font-css*     (mf/use-state "")
        layout        (motion-studio/canvas-preview-layout page-shape vbox zoom)
        artifact      (mf/use-memo
                       (mf/deps objects
                                (:shape-id page-object)
                                (:revision page-object)
                                (:id page-object))
                       (fn []
                         (when page-object
                           (portable-v2/materialize objects page-object))))
        srcdoc        (contract/artifact-srcdoc artifact @font-css*)
        visible?      (and container layout srcdoc (not= :design mode))]

    (mf/use-effect
     (mf/deps page-id)
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
     (mf/deps page-id active-app)
     (fn []
       (reset! container*
               (.getElementById js/document motion-studio/canvas-preview-root-id))
       js/undefined))

    (mf/use-effect
     (mf/deps (:shape-id page-object))
     (fn []
       (reset! mode* :live)
       js/undefined))

    (mf/use-effect
     (mf/deps artifact)
     (fn []
       (reset! font-css* "")
       (when (and (contract/portable-artifact? artifact)
                  (seq (:fonts artifact)))
         (let [subscription
               (->> (rx/from (:fonts artifact))
                    (rx/merge-map fonts/fetch-font-css)
                    (rx/reduce conj [])
                    (rx/subs! #(reset! font-css* (str/join "\n" %))))]
           (fn []
             (rx/dispose! subscription))))))

    (when (and container layout page)
      (mf/portal
       (mf/html
        [:section
         {:class (stl/css-case :page-canvas-preview true
                               :compare (= :compare mode))
          :style {:inset-inline-start (:left layout)
                  :inset-block-start (:top layout)
                  :inline-size (:screen-width layout)
                  :block-size (:screen-height layout)}
          :data-mode (name mode)
          :data-testid "sayhi-page-canvas-preview"
          :aria-label (str "SayHi Page canvas view for " (:route-path page))}
         [:header
          {:class (stl/css :page-canvas-preview-toolbar)
           :data-testid "sayhi-page-preview-toolbar"}
          [:div {:class (stl/css :page-canvas-preview-identity)}
           [:strong (or (:source-title page) (:page-id page))]
           [:span (:route-path page)]]
          [:nav {:aria-label "Page canvas representation"}
           (for [{:keys [id label description]} mode-options]
             [:button
              {:key (name id)
               :type "button"
               :title description
               :aria-pressed (= id mode)
               :on-click #(reset! mode* id)
               :data-testid (str "sayhi-page-preview-" (name id))}
              label])]]
         (when visible?
           [:iframe
            {:class (stl/css :page-canvas-preview-frame)
             :style {:inline-size (:design-width layout)
                     :block-size (:design-height layout)
                     :transform (str "scale(" (:scale layout) ")")}
             :referrer-policy "strict-origin-when-cross-origin"
             :sandbox "allow-same-origin"
             :src-doc srcdoc
             :tab-index -1
             :title (str "Live web rendition of " (:route-path page))}])])
       container))))
