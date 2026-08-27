;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns app.main.ui.sayhi.web-preview
  (:require-macros [app.main.style :as stl])
  (:require
   [app.main.data.sayhi.artifact-motion-playback :as artifact-playback]
   [app.main.data.sayhi.web-materializer.contract :as contract]
   [app.main.fonts :as fonts]
   [beicon.v2.core :as rx]
   [clojure.string :as str]
   [goog.object :as gobj]
   [rumext.v2 :as mf]))

(mf/defc web-preview-page*
  {::mf/props :obj}
  [{:keys [component-id provider artifact href]}]
  (let [loaded* (mf/use-state false)
        fidelity* (mf/use-state nil)
        font-css* (mf/use-state "")
        provider* (mf/use-state provider)
        iframe-ref (mf/use-ref nil)
        artifact-controller-ref (mf/use-ref nil)
        artifact? (contract/portable-artifact? artifact)
        compare? (and artifact? (string? href))
        portable? (= @provider* contract/portable-provider)
        srcdoc (when portable?
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
           (reset! provider* contract/projection-provider)))
        select-portable
        (mf/use-callback
         (mf/deps artifact)
         (fn [_]
           (reset! loaded* false)
           (reset! fidelity* (:fidelity artifact))
           (reset! provider* contract/portable-provider)))
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
     (if (or (some? href) (some? srcdoc))
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
          :title (str "Live preview of " component-id)}]
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
