(ns ednstore.store.writer
  (:require
    [ednstore.store.segment]
    [ednstore.io.read :refer :all]
    [ednstore.io.write :as w]
    [ednstore.store.metadata :as md]))

(defn write!
  "write to the active segment only, should not write to an inactive segment
  active segment looks like this:
  index last-offset read-chan write-chan

  1.update index
  2.update increment last offset
  3.write
  "
  ;TODO should be atomic
  [^String current-namespace k v]
  (let [segment (md/get-active-segment-for-namespace current-namespace)
        append-offset-length (w/write-pair!! (:wc segment) k v)]
    (swap! (:index segment) assoc k @(:last-offset segment))
    (swap! (:last-offset segment) + append-offset-length)))

(defn write-to-segment!
  "used to write to a non-active segment
  used by the merge process"
  [k v segment]
  (let [append-offset-length (w/write-pair!! (:wc segment) k v)]
    (swap! (:index segment) assoc k @(:last-offset segment))
    (swap! (:last-offset segment) + append-offset-length)))

(defn delete!
  "write to log with the delete marker
  1.append to file
  2.update index
  3.append segment offset counter"
  ;TODO should be atomic
  [^String current-namespace k]
  (let [segment (md/get-active-segment-for-namespace current-namespace)
        append-offset-length (w/delete-key!! (:wc segment) k)]
    (swap! (:index segment) assoc k @(:last-offset segment))
    (swap! (:last-offset segment) + append-offset-length)))

