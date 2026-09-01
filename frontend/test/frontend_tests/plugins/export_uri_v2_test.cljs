;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS SUBSIDIARY SL

(ns frontend-tests.plugins.export-uri-v2-test
  (:require
   [app.plugins.export-uri.v2 :as export-uri]
   [cljs.test :as t :include-macros true]))

(t/deftest same-host-export-uses-the-active-browser-origin
  (t/is
   (= "https://dgx3.tail12f603.ts.net:9013/assets/by-id/export.png?token=abc#download"
      (export-uri/resolve-download-uri
       "https://dgx3.tail12f603.ts.net:8997/assets/by-id/export.png?token=abc#download"
       "https://dgx3.tail12f603.ts.net:9013/#/workspace"))))

(t/deftest external-export-origin-remains-unchanged
  (t/is
   (= "https://exports.example.test/assets/export.png"
      (export-uri/resolve-download-uri
       "https://exports.example.test/assets/export.png"
       "https://dgx3.tail12f603.ts.net:9013/#/workspace"))))

(t/deftest uri-object-from-the-exporter-uses-the-active-browser-origin
  (t/is
   (= "https://dgx3.tail12f603.ts.net:9014/assets/by-id/export.png"
      (export-uri/resolve-download-uri
       (js/URL. "https://dgx3.tail12f603.ts.net:8997/assets/by-id/export.png")
       "https://dgx3.tail12f603.ts.net:9014/#/workspace"))))

(t/deftest relative-and-non-http-export-uris-remain-unchanged
  (doseq [uri ["/assets/export.png" "blob:https://example.test/id" "data:image/png;base64,AA=="]]
    (t/is
     (= uri
        (export-uri/resolve-download-uri
         uri
         "https://dgx3.tail12f603.ts.net:9013/#/workspace")))))

(t/deftest invalid-export-uri-remains-unchanged
  (t/is
   (= "not a valid uri"
      (export-uri/resolve-download-uri
       "not a valid uri"
       "https://dgx3.tail12f603.ts.net:9013/#/workspace"))))
