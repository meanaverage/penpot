;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns app.main.ui.sayhi.studio-canvas
  (:require-macros [app.main.style :as stl])
  (:require
   [app.config :as cf]
   [app.main.data.sayhi.motion-studio :as motion-studio]
   [app.main.data.sayhi.studio-canvas :as studio-canvas]
   [app.main.refs :as refs]
   [app.main.store :as st]
   [app.util.json :as json]
   [clojure.string :as str]
   [rumext.v2 :as mf]))

(defn- fetch-json
  [url options]
  (-> (js/fetch url (clj->js options))
      (.then
       (fn [response]
         (-> (.text response)
             (.then
              (fn [body]
                {:ok (.-ok response)
                 :status (.-status response)
                 :body (if (str/blank? body) {} (json/decode body))})))))))

(defn- selection-labels
  [shapes]
  (mapv (fn [{:keys [id type] shape-name :name}]
          (str (or shape-name "Unnamed object") " [" (or (some-> type name) "unknown") "] #" id))
        shapes))

(mf/defc studio-canvas-prompt*
  [{:keys [controls-visible on-toggle-controls]}]
  (let [objects      (mf/deref refs/workspace-page-objects)
        local        (mf/deref refs/workspace-local)
        shapes       (mf/deref refs/selected-shapes)
        launcher-open* (mf/use-state false)
        input*       (mf/use-state "")
        status*      (mf/use-state :checking)
        route*       (mf/use-state nil)
        reply*       (mf/use-state nil)
        error*       (mf/use-state nil)
        capability-url (studio-canvas/agent-api-url
                        cf/sayhi-motion-studio-uri
                        "/v1/studio/canvas/agent-capabilities")
        app-hrefs      (into {}
                             (map (fn [{:keys [id]}]
                                    [id (studio-canvas/studio-app-href
                                         cf/sayhi-studio-uri
                                         id)]))
                             studio-canvas/studio-apps)
        motion-open? (true? (get-in local [:sayhi-motion-studio :open?]))
        motion-eligible? (motion-studio/eligible-selection? objects (:selected local))
        motion-disabled? (and (not motion-eligible?) (not motion-open?))

        probe!
        (mf/use-fn
         (mf/deps capability-url)
         (fn []
           (reset! status* :checking)
           (reset! error* nil)
           (if-not capability-url
             (reset! status* :unavailable)
             (-> (fetch-json capability-url
                             {:method "GET"
                              :headers {:Accept "application/json"}
                              :cache "no-store"})
                 (.then
                  (fn [{:keys [ok body]}]
                    (if-let [route (and ok (studio-canvas/select-assist-route body))]
                      (do
                        (reset! route* route)
                        (reset! status* :ready))
                      (do
                        (reset! route* nil)
                        (reset! status* :unavailable)))))
                 (.catch
                  (fn [_]
                    (reset! route* nil)
                    (reset! status* :unavailable)))))))

        submit!
        (mf/use-fn
         (mf/deps shapes @input* @route* @status*)
         (fn [event]
           (.preventDefault event)
           (let [request (str/trim @input*)]
             (when (and (seq request) (= :ready @status*) @route*)
               (let [url (studio-canvas/agent-api-url
                          cf/sayhi-motion-studio-uri
                          (:endpoint @route*))]
                 (reset! status* :busy)
                 (reset! error* nil)
                 (reset! reply* nil)
                 (-> (fetch-json
                      url
                      {:method "POST"
                       :headers {:Accept "application/json"
                                 :Content-Type "application/json"
                                 :X-SayHi-Studio-Request "studio-assist"
                                 :X-SayHi-Studio-Capability studio-canvas/assist-capability}
                       :body (json/encode
                              (studio-canvas/assist-request
                               request
                               (selection-labels shapes)))
                       :cache "no-store"})
                     (.then
                      (fn [{:keys [ok body]}]
                        (if ok
                          (let [result (json/decode (:response_text body))]
                            (reset! input* "")
                            (reset! reply* result)
                            (reset! status* :ready)
                            (.dispatchEvent
                             js/window
                             (js/CustomEvent.
                              "sayhi:studio-canvas-qwen-response"
                              #js {:detail (clj->js result)})))
                          (do
                            (reset! error* (or (:detail body) "Qwen could not answer that request."))
                            (reset! status* :ready)))))
                     (.catch
                      (fn [_]
                        (reset! error* "Qwen could not be reached from this canvas.")
                        (reset! status* :unavailable)))))))))]

    (mf/use-effect
     (mf/deps probe!)
     (fn []
       (probe!)
       js/undefined))

    [:*
     (when (or @reply* @error*)
       [:aside
        {:class (stl/css-case :studio-canvas-reply true
                              :error (some? @error*))
         :role "status"
         :aria-live "polite"}
        [:span
         [:small (if @error* "Qwen unavailable" "Qwen 3.8")]
         [:strong (or @error* (:summary @reply*))]
         (when (and @reply* (seq (:next_step @reply*)))
           [:p (:next_step @reply*)])]
        [:button
         {:type "button"
          :aria-label "Dismiss Qwen response"
          :on-click #(do (reset! reply* nil) (reset! error* nil))}
         "×"]])
     [:form
      {:class (stl/css :studio-canvas-prompt)
       :data-agent-status (name @status*)
       :on-submit submit!}
      [:div {:class (stl/css :studio-canvas-launcher)}
       [:button
        {:type "button"
         :class (stl/css :studio-canvas-app-toggle)
         :title "Choose Studio app"
         :aria-label "Choose Studio app"
         :aria-haspopup "menu"
        :aria-expanded @launcher-open*
         :on-click #(swap! launcher-open* not)}
        [:svg {:viewBox "0 0 24 24" :aria-hidden true}
         [:rect {:x "4" :y "4" :width "6" :height "6" :rx "1.5"}]
         [:rect {:x "14" :y "4" :width "6" :height "6" :rx "1.5"}]
         [:rect {:x "4" :y "14" :width "6" :height "6" :rx "1.5"}]
         [:rect {:x "14" :y "14" :width "6" :height "6" :rx "1.5"}]]]
       (when @launcher-open*
         [:div
          {:class (stl/css :studio-canvas-launcher-menu)
           :role "menu"}
          [:small {:class (stl/css :studio-canvas-launcher-heading)}
           "Studio apps"]
          (for [{:keys [id label description]} studio-canvas/studio-apps]
            (let [active? (= id :components)
                  href    (get app-hrefs id)]
              (if active?
                [:button
                 {:key (name id)
                  :type "button"
                  :role "menuitem"
                  :aria-current "page"
                  :on-click #(reset! launcher-open* false)}
                 [:span
                  [:strong label]
                  [:small description]]]
                (if href
                  [:a
                   {:key (name id)
                    :role "menuitem"
                    :href href
                    :on-click #(reset! launcher-open* false)}
                   [:span
                    [:strong label]
                    [:small description]]]
                  [:button
                   {:key (name id)
                    :type "button"
                    :role "menuitem"
                    :disabled true}
                   [:span
                    [:strong label]
                    [:small description]]]))))
          [:hr]
          [:small {:class (stl/css :studio-canvas-launcher-heading)}
           "Canvas tools"]
          [:button
           {:type "button"
            :role "menuitem"
            :disabled motion-disabled?
            :on-click #(do
                         (st/emit! (motion-studio/toggle))
                         (reset! launcher-open* false))}
           [:svg {:viewBox "0 0 24 24" :aria-hidden true}
            [:path {:d "m9 7 8 5-8 5V7Z"}]]
           [:span
            [:strong "Motion Studio"]
            [:small (if motion-open? "Close timeline" "Open timeline")]]]
          [:button
           {:type "button"
            :role "menuitem"
            :on-click #(do
                         (on-toggle-controls)
                         (reset! launcher-open* false))}
           [:svg {:viewBox "0 0 24 24" :aria-hidden true}
            [:path {:d "M4 7h10M18 7h2M4 17h2M10 17h10M14 4v6M7 14v6"}]]
           [:span
            [:strong "Penpot controls"]
            [:small (if controls-visible "Hide editor chrome" "Show editor chrome")]]]])]
      [:input
       {:type "text"
        :value @input*
        :disabled (not (contains? #{:ready} @status*))
        :autocomplete "off"
        :aria-label "Ask Qwen about the active Studio canvas"
        :placeholder (case @status*
                       :checking "Connecting to Qwen 3.8…"
                       :unavailable "Qwen 3.8 is offline"
                       :busy "Qwen is thinking…"
                       "Describe a change…")
        :on-change #(reset! input* (.. % -target -value))}]
      [:button
       {:type "submit"
        :class (stl/css :studio-canvas-submit)
        :disabled (or (not= :ready @status*) (str/blank? @input*))
        :title "Send to Qwen 3.8"
        :aria-label "Send to Qwen 3.8"}
       [:svg {:viewBox "0 0 24 24" :aria-hidden true}
        [:path {:d "m5 12 14-7-4.5 14-3-5.5L5 12Zm0 0 6.5 2"}]]]
      [:span
       {:class (stl/css :studio-canvas-agent-state)
        :title (case @status*
                 :ready "Qwen 3.8 is ready"
                 :busy "Qwen 3.8 is responding"
                 :checking "Checking Qwen 3.8"
                 "Qwen 3.8 is unavailable")
        :aria-label (case @status*
                      :ready "Qwen 3.8 ready"
                      :busy "Qwen 3.8 responding"
                      :checking "Checking Qwen 3.8"
                      "Qwen 3.8 unavailable")}
       [:i]]]]))
