;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns frontend-tests.data.sayhi-surface-chrome-test
  (:require
   [app.main.data.sayhi.surface-chrome.v1 :as chrome-v1]
   [cljs.test :as t :include-macros true]))

(t/deftest host-href-is-bounded-to-network-urls
  (let [href (chrome-v1/host-href
              "https://studio.example/chrome/"
              {:active-app :pages
               :components-uri "https://penpot.example/#/workspace?studioMode=canvas"
               :pages-uri "https://penpot.example/#/workspace?studioMode=pages"})
        url  (js/URL. href)]
    (t/is (= "pages" (.get (.-searchParams url) "activeApp")))
    (t/is (= "https://penpot.example/#/workspace?studioMode=pages"
             (.get (.-searchParams url) "pagesUri"))))
  (t/is (nil? (chrome-v1/host-href "javascript:alert(1)" {})))
  (t/is (nil? (chrome-v1/host-href "https://user:secret@studio.example/" {}))))

(t/deftest inbound-contract-is-closed-and-bounded
  (let [valid (fn [type payload]
                (clj->js {:schemaName chrome-v1/schema-name
                          :schemaVersion chrome-v1/schema-version
                          :type type
                          :payload payload}))]
    (t/is (= "studio.ready"
             (:type (chrome-v1/inbound-message
                     (valid "studio.ready" {:capabilities ["bounds"]})))))
    (t/is (= 80
             (get-in (chrome-v1/inbound-message
                      (valid "studio.bounds" {:height 80}))
                     [:payload :height])))
    (t/is (= "penpot.controls.toggle"
             (get-in (chrome-v1/inbound-message
                      (valid "studio.command" {:command "penpot.controls.toggle"}))
                     [:payload :command])))
    (t/is (nil? (chrome-v1/inbound-message
                 (valid "studio.command" {:command "model.invoke"}))))
    (t/is (nil? (chrome-v1/inbound-message
                 (valid "studio.bounds" {:height 2000}))))
    (t/is (nil? (chrome-v1/inbound-message
                 (clj->js {:schemaName chrome-v1/schema-name
                           :schemaVersion chrome-v1/schema-version
                           :type "studio.context.request"
                           :payload {}
                           :extra true}))))))

(t/deftest public-context-removes-private-and-unbounded-values
  (let [shapes (concat
                [{:id "shape-1" :name "Heading" :type :text}
                 {:name "missing id" :type :rect}]
                (repeat 40 {:id "shape-n" :name (apply str (repeat 400 "x")) :type :rect}))
        result (chrome-v1/selection-context shapes)]
    (t/is (= 32 (count result)))
    (t/is (= {:id "shape-1" :name "Heading" :type "text"} (first result)))
    (t/is (= 256 (count (:name (second result))))))
  (t/is (= "dark" (chrome-v1/public-theme :dark)))
  (t/is (= "light" (chrome-v1/public-theme :unknown)))
  (t/is (= "en" (chrome-v1/public-locale (apply str (repeat 40 "x")))))
  (t/is (= 0 (chrome-v1/public-canvas-inset -20)))
  (t/is (= 512 (chrome-v1/public-canvas-inset 900))))

(t/deftest cross-origin-window-identity-uses-object-identity
  (let [window-a (js-obj)
        window-b (js-obj)]
    (t/is (chrome-v1/same-window? window-a window-a))
    (t/is (not (chrome-v1/same-window? window-a window-b)))))
