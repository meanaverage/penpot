(ns frontend-tests.data.sayhi-image-component-test
  (:require
   [app.main.data.sayhi.image-component :as image]
   [app.plugins.register :as preg]
   [cljs.test :as t :include-macros true]))

(t/deftest image-import-envelope-is-closed
  (let [message {:schema image/schema :version "1.0" :type "studio.import"
                 :requestId "request-1"
                 :payload {:identity "image-0123456789abcdef" :mode "A"
                           :scene {} :fileId "file" :pageId "page"}}]
    (t/is (= message (image/inbound (clj->js message))))
    (doseq [invalid [(assoc message :code "arbitrary")
                     (assoc message :version "2.0")
                     (assoc message :type "studio.eval")
                     (assoc-in message [:payload :mode] "E")
                     (assoc-in message [:payload :url] "https://example.com/plugin.js")
                     (assoc-in message [:payload :identity] "sayhi.verification-method-selector")]]
      (t/is (nil? (image/inbound (clj->js invalid)))))))

(t/deftest session-plugin-authority-is-narrow-and-released
  (let [id (str (random-uuid))
        release! (preg/register-session-plugin! id #{"content:read" "content:write"})]
    (t/is (preg/check-permission id "content:write"))
    (t/is (not (preg/check-permission id "library:write")))
    (t/is (not (preg/check-permission id "comment:write")))
    (release!)
    (t/is (not (preg/check-permission id "content:write")))))
