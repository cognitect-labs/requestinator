(ns com.cognitect.requestinator.s3
  "Library for working with S3."
  (:require [clojure.java.io :as io])
  (:import [com.amazonaws.services.s3 AmazonS3Client]
           [com.amazonaws.services.s3.model ObjectMetadata]
           [java.io ByteArrayInputStream ByteArrayOutputStream]))

(def client
  "Return an S3 client object."
  (memoize #(AmazonS3Client.)))

(def UTF-8 (java.nio.charset.Charset/forName "UTF-8"))

(defn combine-paths
  [a b]
  (str a "/" b))

(defn upload
  [client bucket key ^bytes data]
  (.putObject ^AmazonS3Client client
              bucket
              key
              (ByteArrayInputStream. data)
              (doto (ObjectMetadata.)
                (.setContentLength (alength data)))))

(defn get-object
  [^AmazonS3Client client bucket key]
  (let [obj     (.getObject client bucket key)
        m       (.getObjectMetadata obj)
        len     (.getContentLength m)
        barr    (byte-array len)
        baos    (ByteArrayOutputStream.)
        content (.getObjectContent obj)]
    (io/copy content baos)
    (.close content)
    (.toByteArray baos)))

(defn ls
  "Return a lazy sequence of all the items in `bucket` located under
  `prefix`."
  [client bucket prefix]
  (throw (ex-info "Not yet implemented."
                  {:reason :not-yet-implemented})))
