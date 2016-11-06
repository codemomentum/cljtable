(ns ednstore.store.merge.controller
  (:require [clojure.tools.logging :as log]
            [ednstore.store.segment :as s]
            [ednstore.store.loader :as lo]
            [ednstore.store.merger :as m]
            [ednstore.common :as c]
            [clojure.java.io :as io]
            [ednstore.env :as e])
  (:import (java.util.concurrent Executors TimeUnit)
           (clojure.lang Atom)))

(defn delete-segment-from-disk!
  "given the segment id, load it as a read only segment"
  [id]
  (let [segment-file (c/get-segment-file! id)]
    (log/infof "Deleting segment %s from disk" id)
    (io/delete-file segment-file)))

(defn merge!
  "merge 2 segments by
  active new segment and dropping old segments"
  [^Atom segments-map-atom
   ^Number older-segment-id
   ^Number newer-segment-id]
  (log/infof "Start merge! old-segment: %s and new-segment: %s with status: %s ..."
             older-segment-id
             newer-segment-id
             @segments-map-atom)
  (let [segment1 (get @segments-map-atom older-segment-id)
        segment2 (get @segments-map-atom newer-segment-id)]
    ;TODO background (c/do-sequential merger-exec)
    (let [merged-segment-id
          (m/make-merge! segment1 segment2)]
      (log/infof "Segment merge complete for %s - %s . Created new segment: %s ",
                 segment1
                 segment2
                 merged-segment-id)
      (log/infof "Activating merged segment: %s  ", merged-segment-id)
      (let [loaded-segment (lo/load-read-only-segment merged-segment-id)]
        (log/debugf "Loaded new segment: %s" loaded-segment)
        ;;TODO make atomic
        (swap! segments-map-atom dissoc (:id segment1) (:id segment2))
        (log/infof "Removed old segments: %s" @segments-map-atom)
        (s/close-segment! segment1)
        (s/close-segment! segment2)
        (swap! segments-map-atom assoc merged-segment-id loaded-segment)
        (log/infof "Activated merged segment: %s  ", merged-segment-id)
        (log/infof "Deleting closed segments....")
        (delete-segment-from-disk! (:id segment1))
        (delete-segment-from-disk! (:id segment2))
        ))))

(defn make-merge-func [old-segments]
  (fn []
    (try
      (do
        (log/infof "Checking for merge: %s" (keys @old-segments))
        (let [mergeables
              (m/get-mergeable-segments
                @old-segments
                (:merge-strategy e/props))]
          (if mergeables
            (do
              (log/infof "Found mergeable segments: %s" mergeables)
              (merge! old-segments
                      (:id (first mergeables))
                      (:id (second mergeables))))
            (log/infof "No candidates for segment merge ..."))))
      (catch Exception e
        (log/errorf "Error in merger!: %s" (.getMessage e))
        (.printStackTrace e)))))

(defn make-merger-pool! [interval-sec segments-map]
  (let [pool
        (Executors/newScheduledThreadPool 1)]
    (log/infof "Starting merger thread...")
    (.scheduleAtFixedRate pool
                          (make-merge-func segments-map)
                          0 interval-sec TimeUnit/SECONDS)
    pool))
