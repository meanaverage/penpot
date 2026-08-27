;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns app.main.ui.workspace.presence-model)

(defn active-people
  "Project raw collaboration sessions into the distinct people shown by the
  header. Sessions remain available on each person so cursors and connection
  details retain their original session-level fidelity."
  [sessions]
  (->> sessions
       (filter :profile-id)
       (group-by :profile-id)
       (map
        (fn [[profile-id sessions]]
          (let [sessions       (->> sessions
                                    (sort-by #(str (:id %)))
                                    (into []))
                representative (first sessions)]
            {:profile-id profile-id
             :color (:color representative)
             :session-count (count sessions)
             :sessions sessions})))
       (sort-by #(str (:profile-id %)))
       (into [])))
