;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC Sucursal en España SL

(ns app.main.ui.workspace.presence
  (:require-macros [app.main.style :as stl])
  (:require
   [app.common.data :as d]
   [app.common.data.macros :as dm]
   [app.config :as cfg]
   [app.main.refs :as refs]
   [app.main.ui.components.dropdown :refer [dropdown]]
   [app.main.ui.workspace.presence-model :as presence-model]
   [app.util.i18n :as i18n :refer [tr]]
   [rumext.v2 :as mf]))

(defn- session-count-label
  [session-count]
  (tr "workspace.presence.session-count" (i18n/c session-count)))

(mf/defc person-avatar*
  {::mf/props :obj
   ::mf/memo true}
  [{:keys [color profile index session-count]}]
  (let [profile       (assoc profile :color color)
        full-name     (:fullname profile)
        title         (dm/str full-name " — " (session-count-label session-count))]
    [:span {:class (stl/css :session-icon)
            :style {:z-index (dm/str (+ 2 (* -1 index)))
                    :background-color color}
            :title title}
     [:img {:alt full-name
            :style {:background-color color}
            :src (cfg/resolve-profile-photo-url profile)}]]))

(mf/defc active-person*
  {::mf/props :obj
   ::mf/memo true}
  [{:keys [person profile]}]
  (let [session-count (:session-count person)]
    [:li {:class (stl/css :active-person)}
     [:> person-avatar*
      {:color (:color person)
       :index 0
       :profile profile
       :session-count session-count}]
     [:span {:class (stl/css :active-person-copy)}
      [:span {:class (stl/css :active-person-name)} (:fullname profile)]
      [:span {:class (stl/css :active-person-sessions)}
       (session-count-label session-count)]]]))

(mf/defc active-sessions*
  {::mf/memo true}
  []
  (let [profiles     (mf/deref refs/profiles)
        presence     (mf/deref refs/workspace-presence)

        sessions     (vals presence)
        people       (presence-model/active-people sessions)
        num-people   (count people)
        max-avatar-count 3
        avatar-count (if (> num-people max-avatar-count)
                       (dec max-avatar-count)
                       num-people)

        open*        (mf/use-state false)
        open?        (and ^boolean (deref open*) (pos? num-people))
        container-ref (mf/use-ref nil)
        on-toggle
        (mf/use-fn
         (fn [_]
           (swap! open* not)))

        on-close     (mf/use-fn #(reset! open* false))
        label        (tr "workspace.presence.active-collaborators")]

    (when (pos? num-people)
      [:div {:class (stl/css :active-users-control)
             :ref container-ref}
       [:button {:class (stl/css-case :active-users true
                                      :selected open?)
                 :type "button"
                 :title label
                 :aria-label label
                 :aria-expanded open?
                 :aria-haspopup "dialog"
                 :aria-controls "active-collaborators-popover"
                 :on-click on-toggle}
        [:span {:class (stl/css :active-users-list)
                :data-testid "active-users-list"
                :aria-hidden true}
         (when (> num-people max-avatar-count)
           [:span {:class (stl/css :users-num)}
            (dm/str "+" (- num-people avatar-count))])

         (for [[index person] (d/enumerate (take avatar-count people))]
           [:> person-avatar*
            {:color (:color person)
             :index index
             :profile (get profiles (:profile-id person))
             :session-count (:session-count person)
             :key (dm/str (:profile-id person))}])]]

       [:& dropdown
        {:show open?
         :on-close on-close
         :container container-ref}
        [:div {:id "active-collaborators-popover"
               :class (stl/css :active-users-popover)
               :role "dialog"
               :aria-label label}
         [:div {:class (stl/css :active-users-title)} label]
         [:ul {:class (stl/css :active-people-list)
               :data-testid "active-collaborators-list"}
          (for [person people]
            [:> active-person*
             {:person person
              :profile (get profiles (:profile-id person))
              :key (dm/str (:profile-id person))}])]]]])))
