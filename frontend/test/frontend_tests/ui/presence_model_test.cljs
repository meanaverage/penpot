;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns frontend-tests.ui.presence-model-test
  (:require
   [app.main.ui.workspace.presence-model :as presence-model]
   [cljs.test :as t :include-macros true]))

(t/deftest multiple-sessions-for-one-profile-produce-one-person
  (let [sessions [{:id :tab-c :profile-id :aaron :color "#cbaaff"}
                  {:id :tab-a :profile-id :aaron :color "#75cafc"}
                  {:id :tab-b :profile-id :aaron :color "#f49ef7"}]
        people   (presence-model/active-people sessions)]
    (t/is (= 1 (count people)))
    (t/is (= :aaron (:profile-id (first people))))
    (t/is (= 3 (:session-count (first people))))
    (t/is (= [:tab-a :tab-b :tab-c]
             (mapv :id (:sessions (first people)))))))

(t/deftest distinct-profiles-remain-distinct-people
  (let [sessions [{:id :tab-a :profile-id :aaron :color "#75cafc"}
                  {:id :tab-s :profile-id :suren :color "#f49ef7"}]
        people   (presence-model/active-people sessions)]
    (t/is (= [:aaron :suren] (mapv :profile-id people)))
    (t/is (= [1 1] (mapv :session-count people)))))

(t/deftest malformed-presence-without-a-profile-is-not-an-avatar
  (let [sessions [{:id :unknown :color "#75cafc"}
                  {:id :tab-a :profile-id :aaron :color "#f49ef7"}]]
    (t/is (= [:aaron]
             (mapv :profile-id
                   (presence-model/active-people sessions))))))
