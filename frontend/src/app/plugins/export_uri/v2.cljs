;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS SUBSIDIARY SL

(ns app.plugins.export-uri.v2)

(defn resolve-download-uri
  "Keep a trusted exporter path on the active browser origin when Penpot's
  backend and frontend share a hostname but use different candidate ports."
  [export-uri browser-uri]
  (let [export-uri-text (str export-uri)]
    (if-not (re-find #"^https?://" export-uri-text)
      export-uri
      (try
        (let [export-url  (js/URL. export-uri-text)
              browser-url (js/URL. browser-uri)]
          (if (and (= (.-hostname export-url) (.-hostname browser-url))
                   (not= (.-origin export-url) (.-origin browser-url)))
            (str (.-origin browser-url)
                 (.-pathname export-url)
                 (.-search export-url)
                 (.-hash export-url))
            export-uri))
        (catch :default _
          export-uri)))))
