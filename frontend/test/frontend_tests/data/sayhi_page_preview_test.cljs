;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns frontend-tests.data.sayhi-page-preview-test
  (:require
   [app.main.data.sayhi.page-preview :as page-preview]
   [cljs.test :as t :include-macros true]))

(defn- page-shape
  [id representation]
  {:id id
   :type :frame
   :parent-id :root
   :width 1440
   :height 2768
   :plugin-data
   {page-preview/shared-namespace
    {"page-id" "sayhi-homepage"
     "representation" representation
     "source-title" "sayhi.io | Full-stack product engineering"
     "source-url" "https://www.sayhi.io/"
     "site-id" "sayhi-site"
     "site-origin" "https://www.sayhi.io"
     "route-path" "/"
     "route-canonical-url" "https://www.sayhi.io/"
     "import-version" "sayhi.pages-projection/v2"
     "capture" (js/JSON.stringify
                #js {:engine "chromium"
                     :capturedAt "2026-08-28T18:00:00.000Z"})}}})

(t/deftest selected-descendant-resolves-its-native-page-board
  (let [objects {:root {:id :root :type :root}
                 :page (page-shape :page "native")
                 :child {:id :child :type :text :parent-id :page}}]
    (t/is (= :page
             (:shape-id
              (page-preview/resolve-page-root objects [:child]))))))

(t/deftest unique-native-page-resolves-without-a-selection
  (let [objects {:page (page-shape :page "native")
                 :reference (page-shape :reference "fidelity-reference")}]
    (t/is (= :page
             (:shape-id
              (page-preview/resolve-page-root objects #{}))))))

(t/deftest multiple-native-pages-require-an-explicit-selection
  (let [objects {:first (page-shape :first "native")
                 :second (-> (page-shape :second "native")
                             (assoc-in [:plugin-data page-preview/shared-namespace "page-id"]
                                       "sayhi-sparkops"))}]
    (t/is (nil? (page-preview/resolve-page-root objects #{})))
    (t/is (= :second
             (:shape-id
              (page-preview/resolve-page-root objects [:second]))))))

(t/deftest page-projection-maps-to-a-stable-portable-materializer-input
  (let [page (page-preview/page-contract (page-shape :page "native"))]
    (t/is (= {:id "sayhi.page/sayhi-homepage"
              :component-id "sayhi.page/sayhi-homepage"
              :story-id "route:/"
              :revision "2026-08-28T18:00:00.000Z"
              :shape-id :page
              :source :page-projection}
             (page-preview/web-object page)))))

(t/deftest legacy-page-projection-derives-route-from-its-source-url
  (let [shape (update-in (page-shape :page "native")
                         [:plugin-data page-preview/shared-namespace]
                         dissoc
                         "route-path")]
    (t/is (= "/"
             (:route-path (page-preview/page-contract shape))))))

(t/deftest preview-mode-fails-closed-to-live-web
  (t/is (= :live (page-preview/normalize-preview-mode :unknown)))
  (t/is (= :design (page-preview/normalize-preview-mode :design))))
