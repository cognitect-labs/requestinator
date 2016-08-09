(ns com.cognitect.requestinator.serialization
  "A library that abstracts how files are read and written."
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [cognitect.transit :as transit]
            [com.cognitect.requestinator.s3 :as s3])
  (:import [java.io
            ByteArrayInputStream
            ByteArrayOutputStream
            FileOutputStream
            InputStream]
           [java.nio.file CopyOption Files]))

(defn file-recorder
  "Returns a new recorder function that records to files in a directory."
  [dir]
  (fn [relative-path ^bytes data]
    (log/debug "Recording a value to filesystem"
               :relative-path relative-path
               :bytes (alength data))
    (let [path (io/file dir relative-path)]
      (io/make-parents path)
      (with-open [stream (FileOutputStream. path)]
        (.write stream data)))))

(defn file-fetcher
  [dir]
  (fn [path]
    (-> (io/file dir path)
        .toPath
        Files/readAllBytes)))

(defn s3-recorder
  "Returns a new recorder task that records to files in an S3 bucket"
  [client bucket prefix]
  (fn [relative-path ^bytes data]
    (log/debug "Recording a value to S3"
               :relative-path relative-path
               :bucket bucket
               :prefix prefix
               :bytes (alength data))
    (s3/upload client
               bucket
               (s3/combine-paths prefix relative-path)
               data)))

(defn s3-fetcher
  [client bucket prefix]
  (fn [relative-path]
    (s3/get-object client
                   bucket
                   (s3/combine-paths prefix relative-path))))

(defn recorder-writer
  "Returns an implementation of java.io.Writer that writes via
  recorder `record-f` to `path` when the returned Writer is closed."
  [record-f path]
  (let [baos (java.io.ByteArrayOutputStream.)
        inner (java.io.OutputStreamWriter. baos)]
    (proxy [java.io.Writer] []
      (append
        ([c] (.append inner c))
        ([c start end] (.append inner start end)))
      (close []
        (.close inner)
        (log/trace "recorder-writer/close" :path path :baos baos)
        (record-f path (.toByteArray baos)))
      (flush []
        (.flush inner))
      (write
        ([thing] (.write inner thing))
        ([thing off len] (.write inner thing off len))))))

(defn encode
  "Serializes `val` to a byte array."
  [val]
  (let [out    (ByteArrayOutputStream.)
        writer (transit/writer out :json)]
    (transit/write writer val)
    (.toByteArray out)))

(defn decode
  "Deserializes `val` from a byte array."
  [^bytes in]
  (let [reader (transit/reader (ByteArrayInputStream. in) :json)]
    (transit/read reader)))

