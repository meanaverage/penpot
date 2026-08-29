;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns app.main.ui.sayhi.motion-context
  "Shared host controls for SayHi Motion affordances inside Penpot."
  (:require
   [rumext.v2 :as mf]))

(def controls
  (mf/create-context nil))

(defn toolbar-model
  [value]
  (when value
    (let [available? (true? (:available? value))]
      {:available? available?
       :open? (true? (:open? value))
       :label (if available?
                "Motion Studio"
                "Select a SayHi web component with motion")
       :on-toggle (:on-toggle value)})))
