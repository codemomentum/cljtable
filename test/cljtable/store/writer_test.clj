(ns cljtable.store.writer-test
  (:require [clojure.test :refer :all]
            [cljtable.store.writer :refer :all]
            [cljtable.store.segment :as seg]))

(def segment (atom nil))

(defn segment-fixture [f]
  (reset! segment (seg/roll-new-segment! 33))
  (f)
  (seg/close-segment-fully! @segment)
  (reset! seg/active-segment nil))

(use-fixtures :each segment-fixture)


(deftest wire-format-test
  (testing "convert tuple to wire format"
    (let [k (.getBytes "A")
          v (.getBytes "B")
          wire (get-log-to-write k v)]
      (println (seq wire))
      (is (= 11 (alength wire)))
      (is (= '(0 0 0 1 65 41 0 0 0 1 66) (seq wire)))
      )
    (let [k (.getBytes "AA")
          v (.getBytes "BB")
          wire (get-log-to-write k v)]
      (println (seq wire))
      (is (= 13 (alength wire)))
      (is (= '(0 0 0 2 65 65 41 0 0 0 2 66 66) (seq wire)))
      )
    (let [k (.getBytes "A")
          v (.getBytes "ABCD")
          wire (get-log-to-write k v)]
      (println (seq wire))
      (is (= 14 (alength wire)))
      (is (= '(0 0 0 1 65 41 0 0 0 4 65 66 67 68) (seq wire)))
      )
    ))

;some serialization overhead with nippy
(deftest put-to-active-segment
  (testing "segment, index and offset management when writing"
    ;index last-offset read-chan write-chan
    (write! "A" "B" @segment)
    (write! "AAAA" "BBBB" @segment)
    ;validate index
    (is (= 2 (count (keys @(:index @segment)))))
    (is (= 72 @(:last-offset @segment)))
    (println @(:index @segment))
    (is (= 0 (get @(:index @segment) "A")))
    (is (= 33 (get @(:index @segment) "AAAA")))
    ))

(deftest delete-from-active-segment
  (testing "segment, index and offset management when writing"
    ;index last-offset read-chan write-chan
    (write! "A" "B" @segment)
    (write! "AAAA" "BBBB" @segment)
    ;validate index
    (is (= 2 (count (keys @(:index @segment)))))
    (delete! "A" @segment)
    (is (= 2 (count (keys @(:index @segment)))))
    ;TODO side-effect of deleting something that does not exist
    (delete! "NON_EXISTING_KEY" @segment)
    (is (= 3 (count (keys @(:index @segment)))))
    )
  )
