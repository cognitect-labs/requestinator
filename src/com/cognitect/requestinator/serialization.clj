(ns com.cognitect.requestinator.serialization
  "A library that abstracts how files are read and written."
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [cognitect.transit :as transit]
            [com.cognitect.requestinator.json :as json-helper]
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
  "Serializes `val` to a byte array. Optional `opts` are Transit
  writer options."
  ([val] (encode val {}))
  ([val opts]
   (let [out    (ByteArrayOutputStream.)
         writer (transit/writer out :json opts)]
     (transit/write writer val)
     (.toByteArray out))))

(defn decode
  "Deserializes `val` from a byte array. If provided, `opts` specifies
  Transit reader options."
  ([^bytes in] (decode in {}))
  ([^bytes in opts]
   (let [reader (transit/reader (ByteArrayInputStream. in) :json opts)]
     (transit/read reader))))

(defn parse-uri
  [uri]
  (cond
    (.startsWith uri "file:///")
    {:type :file
     :dir (subs uri (count "file://"))}

    (.startsWith uri "file:")
    {:type :file
     :dir (subs uri (count "file:"))}

    (.startsWith uri "s3://")
    (let [without-proto (subs uri (count "s3://"))
          [bucket & paths] (str/split without-proto #"/")]
      {:type   :s3
       :bucket bucket
       :prefix (str/join "/" paths)})

    (re-matches #"^\w+\:\/\/.*" uri)
    (throw (ex-info (str "Unsupported destination: " uri)
                    {:reason ::unsupported-destination
                     :uri    uri}))

    :else
    {:type :file
     :dir uri}))

(defn resolve-relative
  [base uri]
  uri)

(defn create-recorder
  [uri]
  (let [{:keys [type dir bucket prefix]} (parse-uri uri)]
    (case type
      :file (file-recorder dir)
      :s3 (s3-recorder (s3/client) bucket prefix))))

(defn create-fetcher
  [uri]
  (let [{:keys [type dir bucket prefix]} (parse-uri uri)]
    (case type
      :file (file-fetcher dir)
      :s3 (s3-fetcher (s3/client) bucket prefix))))

(defmulti transit-read-handlers
  "Returns a map of types to Transit read handlers that support the given format"
  (fn [format] format))

(defmulti transit-write-handlers
  "Returns a map of types to Transit write handlers that support the given format"
  (fn [format] format))

(defmulti edn-readers
  "Returns a map of symbols to functions to support reading custom
  tags in an EDN file. `format` indicates the particular read
  context (Swagger, GraphQL, etc.) and `relative-to` is the path from
  which the data is being read, to assist in resolving references."
  ;; TODO: this approach for relative-to sort of sucks because it
  ;; doesn't nest arbitrarily deep.
  (fn [format relative-to] format))
