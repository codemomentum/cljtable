(ns cljtable.store.merger-test
  (:require [clojure.test :refer :all]
            [cljtable.store.merger :refer :all]
            [cljtable.store.segment :as s :refer :all]
            [cljtable.store.segment :as seg]
            [cljtable.store.writer :as w]
            [cljtable.store.reader :as r]))


(deftest merge-candidates
  (testing "get merge candidates"
    (is (= '(1000 1001) (get-merge-candidate-ids '(1000 1001))))
    (is (= '(1000 1001) (get-merge-candidate-ids '(1000 1001 1002))))
    (is (= '(0 1002) (get-merge-candidate-ids '(0 1002))))
    (is (nil? (get-merge-candidate-ids '(0))))
    (is (nil? (get-merge-candidate-ids '(1002))))
    ))

(def log1
  [{:key        "A"
    :new-offset 33
    :old-offset 0
    :op-type    41}
   {:key        "AAAA"
    :new-offset 72
    :old-offset 33
    :op-type    41}
   {:key        "AAAA"
    :new-offset 92
    :old-offset 72
    :op-type    42}])

(deftest log-cleanup
  (testing "merging of 2 log sequences"
    (is (= '({:key        "A"
              :new-offset 33
              :old-offset 0
              :op-type    41}
              {:key        "AAAA"
               :new-offset 92
               :old-offset 72
               :op-type    42})
           (cleanup-log log1)))))


(def clean-log-old
  [{:key        "B"
    :new-offset 33
    :old-offset 0
    :op-type    41}
   {:key        "AAAA"
    :new-offset 92
    :old-offset 72
    :op-type    42}])

(def clean-log-new
  [{:key        "A"
    :new-offset 33
    :old-offset 0
    :op-type    41}
   {:key        "AAAA"
    :new-offset 92
    :old-offset 72
    :op-type    42}])



(deftest merge-op
  (testing "basic merge"
    (is (= '({:from       :old
              :key        "B"
              :new-offset 33
              :old-offset 0
              :op-type    41}
              {:from       :old
               :key        "AAAA"
               :new-offset 92
               :old-offset 72
               :op-type    42}
              {:from       :new
               :key        "A"
               :new-offset 33
               :old-offset 0
               :op-type    41}
              {:from       :new
               :key        "AAAA"
               :new-offset 92
               :old-offset 72
               :op-type    42})
           (make-merged-op-log clean-log-old
                               clean-log-new)))
    (is (= [{:from       :old
             :key        "B"
             :new-offset 33
             :old-offset 0
             :op-type    41}
            {:from       :new
             :key        "AAAA"
             :new-offset 92
             :old-offset 72
             :op-type    42}
            {:from       :new
             :key        "A"
             :new-offset 33
             :old-offset 0
             :op-type    41}]
           (cleanup-log
             (make-merged-op-log clean-log-old
                                 clean-log-new)))))
  )


;(let [old-segment (get @s/old-segments 0)
;      new-segment (get @s/old-segments 1)
;      oplog (cleanup-log
;              (make-merged-op-log
;                (r/segment->seq (:rc old-segment))
;                (r/segment->seq (:rc new-segment))))]
;  (read-oplog-item
;    (first oplog)
;    old-segment
;    new-segment))


(deftest mering-test
  (testing "creating 2 custom segments an merging them"
    (let [old-seg
          (atom (seg/make-new-segment! 600))
          new-seg
          (atom (seg/make-new-segment! 601))]
      (w/write! "k1" "v1" @old-seg)
      (w/write! "k1" "v2" @old-seg)
      (w/write! "k2" "v1" @old-seg)

      (w/write! "k1" "v3" @new-seg)
      (w/write! "k1" "v4" @new-seg)
      (w/delete! "k2" @new-seg)

      (is (= '({:from       :old
                :key        "k1"
                :new-offset 35
                :old-offset 0
                :op-type    41}
                {:from       :old
                 :key        "k1"
                 :new-offset 70
                 :old-offset 35
                 :op-type    41}
                {:from       :old
                 :key        "k2"
                 :new-offset 105
                 :old-offset 70
                 :op-type    41}
                {:from       :new
                 :key        "k1"
                 :new-offset 35
                 :old-offset 0
                 :op-type    41}
                {:from       :new
                 :key        "k1"
                 :new-offset 70
                 :old-offset 35
                 :op-type    41}
                {:from       :new
                 :key        "k2"
                 :new-offset 88
                 :old-offset 70
                 :op-type    42})
             (make-merged-op-log
               (map #(into {} %) (r/segment->seq (:rc @old-seg)))
               (map #(into {} %) (r/segment->seq (:rc @new-seg)))))
          "verify oplog failed")

      (is (= '({:from       :new
                :key        "k1"
                :new-offset 70
                :old-offset 35
                :op-type    41})
             (map #(into {} %) (make-oplog-for-new-segment @old-seg
                                                           @new-seg)))
          "mergable oplog should only have 1 key (latest) since the other key has been deleted")

      (make-merge! @old-seg
                   @new-seg)

      (seg/close-segment-fully! @old-seg)
      (seg/close-segment-fully! @new-seg)

      )))