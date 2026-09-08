;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC Sucursal en España SL

(ns app.main.ui.thumbnail-recovery
  (:require
   [app.util.timers :as tm]))

(defn dispose-scheduled-task!
  "Dispose an idle thumbnail task through the protocol returned by timers."
  [task]
  (some-> task tm/dispose!))

(defn cached-image-visible?
  "A disposable thumbnail is usable only until that exact URI has failed."
  [cache-enabled? thumbnail-uri failed-thumbnail-uri]
  (and cache-enabled?
       (some? thumbnail-uri)
       (not= thumbnail-uri failed-thumbnail-uri)))

(defn native-content-visible?
  "Reveal the source object tree whenever its cached thumbnail is unavailable."
  [cache-enabled? thumbnail-uri failed-thumbnail-uri]
  (not (cached-image-visible? cache-enabled?
                              thumbnail-uri
                              failed-thumbnail-uri)))
