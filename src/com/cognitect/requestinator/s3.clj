(ns com.cognitect.requestinator.s3
  "Library for working with S3."
  (:import [com.amazonaws.services.s3 AmazonS3Client]
           [com.amazonaws.services.s3.model ObjectMetadata]
           [java.io InputStream]))

(defn client
  "Return a new S3 client object."
  []
  (AmazonS3Client.))

(def UTF-8 (java.nio.charset.Charset/forName "UTF-8"))

(defn upload
  [client bucket key ^InputStream data length]
  (.putObject ^AmazonS3Client client
              bucket
              key
              data
              (doto (ObjectMetadata.)
                (.setContentLength length))))

(defn ls
  "Return a lazy sequence of all the items in `bucket` located under
  `prefix`."
  [client bucket prefix]
  (throw (ex-info "Not yet implemented."
                  {:reason :not-yet-implemented})))
