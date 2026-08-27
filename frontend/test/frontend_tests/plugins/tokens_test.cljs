;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC Sucursal en España SL

(ns frontend-tests.plugins.tokens-test
  (:require
   [app.common.test-helpers.compositions :as ctho]
   [app.common.test-helpers.files :as cthf]
   [app.common.test-helpers.ids-map :as cthi]
   [app.common.test-helpers.tokens :as ctht]
   [app.common.types.tokens-lib :as ctob]
   [app.common.uuid :as uuid]
   [app.main.data.tokenscript :as ts]
   [app.main.data.workspace.tokens.application :as dwta]
   [app.main.data.workspace.tokens.library-edit :as dwtl]
   [app.main.store :as st]
   [app.plugins.api :as api]
   [app.plugins.shape :as pshape]
   [app.plugins.system-events :as se]
   [app.plugins.tokens :as ptok]
   [app.plugins.utils :as u]
   [cljs.test :as t :include-macros true]
   [frontend-tests.helpers.mock :as mock]
   [frontend-tests.helpers.state :as ths]
   [potok.v2.core :as ptk]))

(t/use-fixtures :each {:before cthi/reset-idmap!})

(def ^:private get-resolved-value @#'ptok/get-resolved-value)

;; Regression coverage for issue #9162.
;;
;; Plugin code calling `shape.applyToken(token, ["fill"])` or
;; `token.applyToShapes([rect], ["fill"])` from JavaScript supplies a JS
;; array of strings. Penpot's plugin proxies expect a Clojure set of
;; keywords. Two coupled defects made these calls silently no-op (or, with
;; `throwValidationErrors` enabled, throw a "check error"):
;;
;; 1. `token-attr-plugin->token-attr` only consulted its alias map when
;;    the input was already a keyword — string inputs like "fill" or
;;    "border-radius-top-left" fell through to the identity branch
;;    unchanged, so the downstream `cto/token-attr?` predicate (which
;;    checks against a set of keywords) returned false.
;; 2. The `applyToken` / `applyToShapes` / `applyToSelected` schemas need an
;;    internal set, while the public API takes an array. The registered
;;    `[::sm/set ...]` decoder handles same-realm arrays, but arrays arriving
;;    from a sandboxed plugin iframe may not satisfy the host realm's array or
;;    collection predicates. The API boundary now normalizes finite array-like
;;    values before schema validation.
;;
;; These helper-level tests pin the string-friendly conversion contract;
;; the schema-level fix is covered by the existing plugin integration
;; suite that exercises `applyToken` end-to-end.

(t/deftest token-attr-plugin->token-attr-passes-canonical-form-through
  ;; Both already-canonical short names and unaliased names pass through
  ;; unchanged.
  (t/is (= :fill (ptok/token-attr-plugin->token-attr :fill)))
  (t/is (= :stroke-color (ptok/token-attr-plugin->token-attr :stroke-color)))
  (t/is (= :r1 (ptok/token-attr-plugin->token-attr :r1)))
  (t/is (= :p2 (ptok/token-attr-plugin->token-attr :p2))))

(t/deftest token-attr-plugin->token-attr-resolves-verbose-plugin-aliases
  ;; Plugin-side verbose names (e.g. `:border-radius-top-left`) map to
  ;; their canonical short internal form (`:r1`) so plugin authors can
  ;; spell the corner explicitly without the engine having to know both.
  (t/is (= :r1 (ptok/token-attr-plugin->token-attr :border-radius-top-left)))
  (t/is (= :r2 (ptok/token-attr-plugin->token-attr :border-radius-top-right)))
  (t/is (= :r3 (ptok/token-attr-plugin->token-attr :border-radius-bottom-right)))
  (t/is (= :r4 (ptok/token-attr-plugin->token-attr :border-radius-bottom-left)))
  ;; The public Plugin API deliberately uses the DTCG token type spelling
  ;; `fontFamilies`, while Penpot stores the applied property as singular
  ;; `:font-family`.
  (t/is (= :font-family (ptok/token-attr-plugin->token-attr :font-families))))

(t/deftest token-attr-plugin->token-attr-resolves-padding-margin-side-aliases
  (t/is (= :p1 (ptok/token-attr-plugin->token-attr :padding-top)))
  (t/is (= :p2 (ptok/token-attr-plugin->token-attr :padding-right)))
  (t/is (= :p3 (ptok/token-attr-plugin->token-attr :padding-bottom)))
  (t/is (= :p4 (ptok/token-attr-plugin->token-attr :padding-left)))
  (t/is (= :m1 (ptok/token-attr-plugin->token-attr :margin-top)))
  (t/is (= :m2 (ptok/token-attr-plugin->token-attr :margin-right)))
  (t/is (= :m3 (ptok/token-attr-plugin->token-attr :margin-bottom)))
  (t/is (= :m4 (ptok/token-attr-plugin->token-attr :margin-left))))

(t/deftest token-attr-plugin->token-attr-coerces-string-input
  ;; This is the actual regression — JS plugin calls supply strings.
  (t/is (= :fill (ptok/token-attr-plugin->token-attr "fill")))
  (t/is (= :stroke-color (ptok/token-attr-plugin->token-attr "stroke-color")))
  (t/is (= :stroke-color (ptok/token-attr-plugin->token-attr "strokeColor")))
  (t/is (= :font-family (ptok/token-attr-plugin->token-attr "fontFamilies")))
  ;; Verbose plugin aliases work via the string path too.
  (t/is (= :r1 (ptok/token-attr-plugin->token-attr "border-radius-top-left")))
  (t/is (= :m3 (ptok/token-attr-plugin->token-attr "margin-bottom"))))

(t/deftest token-attr?-accepts-keyword-input
  (t/is (true? (boolean (ptok/token-attr? :fill))))
  (t/is (true? (boolean (ptok/token-attr? :stroke-color))))
  (t/is (true? (boolean (ptok/token-attr? :r1))))
  (t/is (true? (boolean (ptok/token-attr? :p2)))))

(t/deftest token-attr?-accepts-string-input
  ;; Same JS-array-of-strings reproducer as the issue, exercised at the
  ;; predicate layer the plugin schemas call into.
  (t/is (true? (boolean (ptok/token-attr? "fill"))))
  (t/is (true? (boolean (ptok/token-attr? "stroke-color"))))
  (t/is (true? (boolean (ptok/token-attr? "strokeColor"))))
  (t/is (true? (boolean (ptok/token-attr? "fontFamilies"))))
  (t/is (true? (boolean (ptok/token-attr? "r1"))))
  (t/is (true? (boolean (ptok/token-attr? "m3")))))

(t/deftest normalize-token-attrs-accepts-sandbox-array-like-values
  ;; A plain array-like object reproduces the important cross-realm property:
  ;; it has indexed values and a finite length, but is not a host collection.
  (let [attrs #js {"0" "fill"
                   "1" "strokeColor"
                   "length" 2}]
    (t/is (= #{"fill" "strokeColor"}
             (ptok/normalize-token-attrs attrs)))))

(t/deftest decode-token-application-args-preserves-proxy-and-normalizes-attrs
  (let [token #js {"kind" "token-proxy"}
        attrs #js {"0" "borderRadiusTopLeft"
                   "length" 1}
        decoded (ptok/decode-token-application-args
                 #js [token attrs]
                 1)]
    (t/is (identical? token (first decoded)))
    (t/is (= #{"borderRadiusTopLeft"} (second decoded)))))

(t/deftest shape-apply-token-accepts-padding-top
  (t/async
    done
    (let [set-id    (cthi/new-id! :token-set)
          token-id  (cthi/new-id! :spacing-token)
          file      (-> (cthf/sample-file :file1 :page-label :page1)
                        (ctho/add-frame :frame1 {:layout :flex})
                        (ctht/add-tokens-lib)
                        (ctht/update-tokens-lib
                         #(-> %
                              (ctob/add-set
                               (ctob/make-token-set :id set-id
                                                    :name "spacing"))
                              (ctob/add-theme
                               (ctob/make-token-theme :name "theme"
                                                      :sets #{"spacing"}))
                              (ctob/set-active-themes #{"/theme"})
                              (ctob/add-token
                               set-id
                               (ctob/make-token :id token-id
                                                :name "spacing.medium"
                                                :type :spacing
                                                :value 16)))))
          store     (ths/setup-store file)
          _         (set! st/state store)
          _         (set! st/stream (ptk/input-stream store))
          ^js context   (api/create-context "00000000-0000-0000-0000-000000000000")
          ^js page      (.-currentPage context)
          ^js shape     (.getShapeById page (str (cthi/id :frame1)))
          ^js library   (.-library context)
          ^js local     (.-local library)
          ^js catalog   (.-tokens local)
          ^js token-set (.getSetById catalog (str set-id))
          ^js token     (.getTokenById token-set (str token-id))]
      (.applyToken shape token #js ["paddingTop"])
      (js/setTimeout
       (fn []
         (let [shape-id (cthi/id :frame1)
               page-id  (cthf/current-page-id file)]
           (t/is (= "spacing.medium" (.. shape -tokens -paddingTop)))
           (t/is (= "spacing.medium"
                    (get-in @store
                            [:files (:id file) :data :pages-index page-id
                             :objects shape-id :applied-tokens :p1])))
           (done)))
       0))))

(t/deftest shape-apply-token-accepts-public-font-families-property
  (let [plugin-id "00000000-0000-0000-0000-000000000000"
        file-id   (uuid/next)
        page-id   (uuid/next)
        shape-id  (uuid/next)
        set-id    (uuid/next)
        token-id  (uuid/next)
        token     (ctob/make-token :id token-id
                                   :name "type.family.primary"
                                   :type :font-family
                                   :value ["Instrument Sans"])
        ^js shape (pshape/shape-proxy plugin-id file-id page-id shape-id)
        ^js proxy (ptok/token-proxy plugin-id file-id set-id token-id)
        captured  (atom nil)]
    (with-redefs [u/locate-token    (constantly token)
                  dwta/toggle-token (fn [attrs]
                                      (reset! captured attrs)
                                      :toggle-token)
                  se/add-event      (fn [event _plugin-id] event)
                  st/emit!          mock/noop]
      (.applyToken shape proxy #js ["fontFamilies"])
      (t/is (= #{:font-family} (:attrs @captured)))
      (t/is (= [shape-id] (:shape-ids @captured))))))

(t/deftest token-attr?-rejects-unknown-input
  (t/is (false? (boolean (ptok/token-attr? :not-a-real-attr))))
  (t/is (false? (boolean (ptok/token-attr? "not-a-real-attr"))))
  (t/is (false? (boolean (ptok/token-attr? nil)))))

;; Regression coverage for issue #10070.
;;
;; The Plugin API's `addToken` rejected reference tokens whose target
;; lives in an *inactive* token set, even though the referenced token
;; exists structurally. The proxy `:fn` resolved the new token against
;; active sets only (`get-tokens-in-active-sets`), so a reference into an
;; inactive set never resolved and fell into the generic `not-valid`
;; error path.
;;
;; The fix resolves against *all* tokens in the library (inactive sets
;; included), mirroring the workspace token-creation form. These tests
;; reproduce the exact `tokens-tree` construction from both the buggy and
;; the fixed `addToken` `:fn` and assert resolution behaviour directly —
;; the proxy `:fn` itself drives the global store and `st/emit!`, so it is
;; not unit-testable, but the resolve step it gates on is.

(defn- inactive-set-library
  "A library with `primitives` (holding `color.gray.50`) left inactive and
  an active, empty `semantic` set — the repro from the issue."
  []
  (-> (ctob/make-tokens-lib)
      (ctob/add-set (ctob/make-token-set :id (cthi/new-id! :primitives)
                                         :name "primitives"))
      (ctob/add-set (ctob/make-token-set :id (cthi/new-id! :semantic)
                                         :name "semantic"))
      (ctob/add-token (cthi/id :primitives)
                      (ctob/make-token {:name "color.gray.50"
                                        :value "#fafafa"
                                        :type :color}))
      ;; `add-set` does not activate sets, so activate only `semantic`,
      ;; leaving `primitives` (the reference target) inactive.
      (ctob/toggle-set-in-theme ctob/hidden-theme-id "semantic")))

(t/deftest add-token-active-sets-only-fails-to-resolve-cross-set-reference
  ;; Demonstrates the bug: resolving the new token against active sets
  ;; only leaves the reference unresolved.
  (let [tokens-lib (inactive-set-library)
        token (ctob/make-token {:name "color.bg.default"
                                :value "{color.gray.50}"
                                :type :color})
        tokens-tree (-> (ctob/get-tokens-in-active-sets tokens-lib)
                        (assoc (:name token) token))
        resolved (ts/resolve-tokens tokens-tree)
        {:keys [errors resolved-value]} (get resolved (:name token))]
    (t/is (nil? resolved-value))
    (t/is (seq errors))))

(t/deftest add-token-resolves-cross-set-reference-into-inactive-set
  ;; The fix: resolving against all tokens in the library (inactive sets
  ;; included) resolves the reference even though `primitives` is inactive.
  (let [tokens-lib (inactive-set-library)
        token (ctob/make-token {:name "color.bg.default"
                                :value "{color.gray.50}"
                                :type :color})
        tokens-tree (-> (merge (ctob/get-all-tokens-map tokens-lib)
                               (ctob/get-tokens tokens-lib (cthi/id :semantic)))
                        (assoc (:name token) token))
        resolved (ts/resolve-tokens tokens-tree)
        {:keys [errors resolved-value]} (get resolved (:name token))]
    (t/is (some? resolved-value))
    (t/is (empty? errors))))

(t/deftest add-token-still-fails-for-references-missing-from-every-set
  ;; A reference to a token that exists in *no* set must still fail, even
  ;; with the all-tokens resolution.
  (let [tokens-lib (inactive-set-library)
        token (ctob/make-token {:name "color.bg.default"
                                :value "{color.does.not.exist}"
                                :type :color})
        tokens-tree (-> (merge (ctob/get-all-tokens-map tokens-lib)
                               (ctob/get-tokens tokens-lib (cthi/id :semantic)))
                        (assoc (:name token) token))
        resolved (ts/resolve-tokens tokens-tree)
        {:keys [errors resolved-value]} (get resolved (:name token))]
    (t/is (nil? resolved-value))
    (t/is (seq errors))))

(t/deftest token-set-duplicate-returns-the-duplicated-set
  (let [file-id (cthi/new-id! :file)
        set-id  (cthi/new-id! :set)
        dup-id  (cthi/new-id! :dup)
        proxy   (ptok/token-set-proxy "plugin-id" file-id set-id)]
    (with-redefs [dwtl/duplicate-token-set
                  (mock/stub (fn [id {:keys [id-ref]}]
                               (t/is (= set-id id))
                               (reset! id-ref dup-id)
                               :duplicate-token-set))
                  st/emit! mock/noop]
      (let [dup (.duplicate proxy)]
        (t/is (ptok/token-set-proxy? dup))
        (t/is (= (str dup-id) (.-id dup)))))))

(t/deftest token-set-add-token-coerces-a-numeric-font-weight
  ;; DTCG represents variable font weights as numbers. The public Plugin API
  ;; accepts numeric token inputs and stores them in Penpot's canonical text
  ;; representation. Exercise the actual JS proxy boundary rather than only
  ;; the DTCG importer's materialization helper.
  (let [plugin-id  "plugin-id"
        file-id    (cthi/new-id! :file)
        set-id     (cthi/new-id! :set)
        tokens-lib (-> (ctob/make-tokens-lib)
                       (ctob/add-set
                        (ctob/make-token-set :id set-id :name "Typography")))
        captured   (atom nil)]
    (with-redefs [u/locate-tokens-lib (constantly tokens-lib)
                  dwtl/create-token
                  (fn
                    ([token]
                     (reset! captured {:set-id nil :token token})
                     :create-token)
                    ([created-set-id token]
                     (reset! captured {:set-id created-set-id :token token})
                     :create-token))
                  se/add-event (fn [event _plugin-id] event)
                  st/emit! mock/noop]
      (let [set-proxy   (ptok/token-set-proxy plugin-id file-id set-id)
            token-proxy (.addToken set-proxy
                                   #js {"type" "fontWeights"
                                        "name" "type.weight.medium"
                                        "value" 560})]
        (t/is (ptok/token-proxy? token-proxy))
        (t/is (= set-id (:set-id @captured)))
        (t/is (= :font-weight (get-in @captured [:token :type])))
        (t/is (= "560" (get-in @captured [:token :value])))))))

(t/deftest token-value-update-normalizes-public-dtcg-values
  ;; Re-importing an existing component uses Token.value setters rather than
  ;; TokenSet.addToken. Keep the update boundary symmetric with creation.
  (t/is (= "560" (ptok/normalize-token-value :font-weight 560)))
  (t/is (= [{:color "#000000"
             :inset false
             :offset-x "1px"
             :offset-y "2px"
             :spread "0px"
             :blur "4px"}]
           (ptok/normalize-token-value
            :shadow
            #js [#js {"color" "#000000"
                      "inset" "false"
                      "offsetX" "1px"
                      "offsetY" "2px"
                      "spread" "0px"
                      "blur" "4px"}])))
  (t/is (= {:font-family ["{font.family}"]
            :font-size "{font.size}"
            :font-weight "{font.weight}"
            :letter-spacing "0px"
            :line-height "1.2"
            :text-case "none"
            :text-decoration "none"}
           (ptok/normalize-token-value
            :typography
            #js {"fontFamilies" "{font.family}"
                 "fontSizes" "{font.size}"
                 "fontWeight" "{font.weight}"
                 "letterSpacing" "0px"
                 "lineHeight" "1.2"
                 "textCase" "none"
                 "textDecoration" "none"}))))

(t/deftest token-value-getter-projects-public-composite-shapes
  (let [file-id  (cthi/new-id! :file)
        set-id   (cthi/new-id! :set)
        token-id (cthi/new-id! :token)]
    (with-redefs [u/locate-token
                  (constantly {:id token-id
                               :name "type.heading"
                               :type :typography
                               :value {:font-family ["{font.family}"]
                                       :font-size "{font.size}"
                                       :font-weight "{font.weight}"
                                       :letter-spacing "0px"
                                       :line-height "1.2"
                                       :text-case "none"
                                       :text-decoration "none"}})]
      (let [value (.-value (ptok/token-proxy "plugin-id" file-id set-id token-id))]
        (t/is (= ["{font.family}"] (vec (aget value "fontFamilies"))))
        (t/is (= "{font.size}" (aget value "fontSizes")))
        (t/is (= "{font.weight}" (aget value "fontWeight")))
        (t/is (= "0px" (aget value "letterSpacing")))
        (t/is (= "1.2" (aget value "lineHeight")))))))

(t/deftest theme-add-set-and-remove-set-use-the-set-name
  (let [file-id  (cthi/new-id! :file)
        theme-id (cthi/new-id! :theme)
        set-id   (cthi/new-id! :set)
        set      (ptok/token-set-proxy "plugin-id" file-id set-id "Primitives")
        theme    (ptok/token-theme-proxy "plugin-id" file-id theme-id)
        captured (atom [])]
    (with-redefs [u/locate-token-theme
                  (fn [_file _theme]
                    (ctob/make-token-theme :id theme-id
                                           :name "Theme"
                                           :sets #{"Primitives"}))
                  dwtl/update-token-theme
                  (fn [id theme]
                    (swap! captured conj {:id id :theme theme})
                    :update-token-theme)
                  st/emit! identity]
      (.addSet theme set)
      (.removeSet theme set)
      (t/is (= [theme-id theme-id] (mapv :id @captured)))
      (t/is (contains? (-> @captured first :theme :sets) "Primitives"))
      (t/is (not (contains? (-> @captured second :theme :sets) "Primitives"))))))

(t/deftest font-family-token-value-accepts-a-string
  (let [file-id  (cthi/new-id! :file)
        set-id   (cthi/new-id! :set)
        token-id (cthi/new-id! :token)
        captured (atom nil)]
    (with-redefs [u/locate-token (constantly {:id token-id
                                              :name "font.primary"
                                              :type :font-family
                                              :value ["Inter"]})
                  dwtl/update-token (mock/stub (fn [set-id token-id attrs]
                                                 (reset! captured {:set-id set-id
                                                                   :token-id token-id
                                                                   :attrs attrs})
                                                 :update-token))
                  st/emit! mock/noop]
      (let [token (ptok/token-proxy "plugin-id" file-id set-id token-id)]
        (set! (.-value token) "Inter, Arial")
        (t/is (= set-id (:set-id @captured)))
        (t/is (= token-id (:token-id @captured)))
        (t/is (= ["Inter" "Arial"] (get-in @captured [:attrs :value])))))))

(t/deftest token-description-event-uses-the-calling-plugin-id
  (let [plugin-id "00000000-0000-0000-0000-000000000000"
        file-id   (uuid/next)
        set-id    (uuid/next)
        token-id  (uuid/next)
        captured  (atom nil)]
    (with-redefs [u/locate-token    (constantly {:id token-id
                                                 :name "color.panel"
                                                 :type :color
                                                 :value "#ffffff"})
                  dwtl/update-token (mock/stub
                                     (fn [_set-id _token-id attrs]
                                       {:attrs attrs}))
                  se/add-event      (mock/stub
                                     (fn [event event-plugin-id]
                                       (reset! captured {:event event
                                                         :plugin-id event-plugin-id})
                                       event))
                  st/emit!          mock/noop]
      (let [token (ptok/token-proxy plugin-id file-id set-id token-id)]
        (set! (.-description token) "Panel surface")
        (t/is (= plugin-id (:plugin-id @captured)))
        (t/is (= "Panel surface" (get-in @captured [:event :attrs :description])))))))

(t/deftest typography-token-resolved-value-is-plugin-array-shape
  (let [token (ctob/make-token
               {:name "type.body"
                :type :typography
                :value {:font-family ["Inter" "Arial"]
                        :font-size "16px"
                        :font-weight "600"
                        :line-height "20px"
                        :letter-spacing "1"
                        :text-case "uppercase"
                        :text-decoration "underline"}})
        result (get-resolved-value token {(:name token) token})
        entry  (aget result 0)]
    (t/is (array? result))
    (t/is (= ["Inter" "Arial"] (vec (aget entry "fontFamilies"))))
    (t/is (= 16 (aget entry "fontSizes")))
    (t/is (= "600" (aget entry "fontWeights")))
    (t/is (= 20 (aget entry "lineHeight")))
    (t/is (= "uppercase" (aget entry "textCase")))
    (t/is (= "underline" (aget entry "textDecoration")))))

(t/deftest shadow-token-resolved-value-is-plugin-array-shape
  (let [token (ctob/make-token
               {:name "shadow.card"
                :type :shadow
                :value [{:offset-x "1px"
                         :offset-y "2px"
                         :blur "3px"
                         :spread "4px"
                         :color "#000000"
                         :inset false}]})
        result (get-resolved-value token {(:name token) token})
        entry  (aget result 0)]
    (t/is (array? result))
    (t/is (= 1 (aget entry "offsetX")))
    (t/is (= 2 (aget entry "offsetY")))
    (t/is (= 3 (aget entry "blur")))
    (t/is (= 4 (aget entry "spread")))))

(t/deftest font-family-token-resolved-value-is-string-array
  (let [token (ctob/make-token
               {:name "font.primary"
                :type :font-family
                :value ["Inter" "Arial"]})
        result (get-resolved-value token {(:name token) token})]
    (t/is (array? result))
    (t/is (= ["Inter" "Arial"] (vec result)))))

(t/deftest token-theme-add-set-accepts-token-set-id
  (let [plugin-id "plugin-id"
        file-id   (uuid/next)
        theme-id  (uuid/next)
        set-id    (uuid/next)
        token-set (ctob/make-token-set :id set-id :name "Core")
        theme     (ctob/make-token-theme :id theme-id :group "mode" :name "Light")
        emitted   (atom [])
        invalid   (atom [])]
    (with-redefs [u/locate-token-set   (fn [_ id] (when (= id set-id) token-set))
                  u/locate-token-theme (fn [_ id] (when (= id theme-id) theme))
                  u/not-valid          (fn [_ code value] (swap! invalid conj [code value]))
                  dwtl/update-token-theme (fn [id theme] {:id id :theme theme})
                  st/emit!             (fn ([event] (swap! emitted conj event) nil)
                                         ([event & _] (swap! emitted conj event) nil))]
      (let [theme-proxy (ptok/token-theme-proxy plugin-id file-id theme-id)]
        (.addSet theme-proxy (str set-id))
        (t/is (= #{"Core"} (-> @emitted first :theme :sets)))
        (t/is (empty? @invalid))))))

(t/deftest token-theme-add-set-accepts-token-set-proxy
  (let [plugin-id "plugin-id"
        file-id   (uuid/next)
        theme-id  (uuid/next)
        set-id    (uuid/next)
        token-set (ctob/make-token-set :id set-id :name "Core")
        theme     (ctob/make-token-theme :id theme-id :group "mode" :name "Light")
        emitted   (atom [])
        invalid   (atom [])]
    (with-redefs [u/locate-token-set   (fn [_ id] (when (= id set-id) token-set))
                  u/locate-token-theme (fn [_ id] (when (= id theme-id) theme))
                  u/not-valid          (fn [_ code value] (swap! invalid conj [code value]))
                  dwtl/update-token-theme (fn [id theme] {:id id :theme theme})
                  st/emit!             (fn ([event] (swap! emitted conj event) nil)
                                         ([event & _] (swap! emitted conj event) nil))]
      (let [theme-proxy (ptok/token-theme-proxy plugin-id file-id theme-id)
            set-proxy   (ptok/token-set-proxy plugin-id file-id set-id "Core")]
        (.addSet theme-proxy set-proxy)
        (t/is (= #{"Core"} (-> @emitted first :theme :sets)))
        (t/is (empty? @invalid))))))

(t/deftest token-theme-add-set-rejects-invalid-arguments
  (let [plugin-id "plugin-id"
        file-id   (uuid/next)
        theme-id  (uuid/next)
        theme     (ctob/make-token-theme :id theme-id :group "mode" :name "Light")
        emitted   (atom [])
        invalid   (atom [])]
    (with-redefs [u/locate-token-set   (constantly nil)
                  u/locate-token-theme (fn [_ id] (when (= id theme-id) theme))
                  u/not-valid          (fn [_ code value] (swap! invalid conj [code value]))
                  dwtl/update-token-theme (fn [id theme] {:id id :theme theme})
                  st/emit!             (fn ([event] (swap! emitted conj event) nil)
                                         ([event & _] (swap! emitted conj event) nil))]
      (let [theme-proxy (ptok/token-theme-proxy plugin-id file-id theme-id)]
        ;; Non-id, non-proxy arguments are rejected by the schema coercer.
        (.addSet theme-proxy 42)
        (.removeSet theme-proxy nil)
        (t/is (empty? @emitted))
        (t/is (= 2 (count @invalid)))
        (t/is (every? #(= :error (first %)) @invalid))))))
