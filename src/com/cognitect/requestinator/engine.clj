(ns com.cognitect.requestinator.engine
  "A library to pull together the various pieces of the Requestinator
  into a working system."
  (:require [com.cognitect.requestinator.s3 :as s3]
            [com.cognitect.requestinator.swagger :as swagger]
            [com.cognitect.requestinator.json :as json-helper]
            [com.cognitect.requestinator.thread-pool :as thread-pool]
            [cognitect.transit :as transit]
            [com.stuartsierra.component :as component]
            [clojure.core.async :as async :refer [>!! <!!]]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [simulant.http :as http])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream FileOutputStream InputStream]
           [java.nio.file CopyOption Files]))

(defn file-recorder
  "Returns a new recorder function that records to files in a directory."
  [dir]
  (let [counter (atom 0)]
    (fn [relative-path ^bytes data]
      (log/debug "Recording a value" :counter @counter)
      (let [path (io/file dir relative-path)]
        (io/make-parents path)
        (with-open [stream (FileOutputStream. path)]
          (.write stream data))))))

(defn s3-recorder
  "Returns a new recorder task that records to files in an S3 bucket"
  [creds bucket prefix]
  (let [client (s3/client creds)
        counter (atom 0)]
    (fn [val]
     (s3/upload client
                bucket
                (format "%s/%s/%10d.edn"
                        prefix
                        (:agent val)
                        (swap! counter inc))
                (pr-str val)))))

(defn erlang
  [mean]
  (- (* (Math/log (rand)) mean)))

(defn create-agent
  [{:keys [interarrival-sec duration-sec]} ch requests id]
  (fn []
    (try
      (log/debug "Starting a thread")
      (let [start  (System/currentTimeMillis)
            client (http/generate-client (http/cookie-store))]
        (loop [[request & more] requests]
          (when (> duration-sec (/ (- (System/currentTimeMillis) start) 1000.0))
            (let [response (client request)]
              (>!! ch (assoc response :agent id))
              (let [sleep (long (erlang (* interarrival-sec 1000)))]
                (log/debug "Agent sleeping for" sleep)
                (Thread/sleep sleep))
              (recur more)))))
      (catch Throwable t
        (log/error t "Exception in agent"))
      (finally
        (log/debug "Closing the channel")
        (async/close! ch)))))

(defn start-agents
  [{:keys [spec agent-count interarrival-sec duration-sec]
    :as opts}]
  (let [channels        (repeatedly agent-count #(async/chan 1))
        request-streams (repeatedly agent-count #(swagger/generate spec))
        tasks           (map #(create-agent opts %1 %2 (format "%04d" %3)) channels request-streams (range))
        threads         (map #(Thread. %) tasks)]
    (doseq [thread threads]
      (.start thread))
    {:threads         threads
     :channels        channels
     ;;:request-streams request-streams
     ;;:tasks           tasks
     }))

(defn consume-results
  [chans recorder]
  (try
    (loop [chans (set chans)]
      (when-not (empty? chans)
        (let [[val port] (async/alts!! (seq chans))]
          (log/debug "Got a value from a channel")
          (if (nil? val)
            (do
              (log/debug "Removing a completed channel." :reminaing (count chans))
              (recur (disj chans port)))
            (do
              (recorder val)
              (recur chans))))))
    (catch Throwable t
      (log/error t "Error while consuming results"))))

(defn- encode
  "Serializes `val` to a byte array."
  [val]
  (let [out    (ByteArrayOutputStream.)
        writer (transit/writer out :json)]
    (transit/write writer val)
    (.toByteArray out)))

(defn generate-activity-streams
  "Generates requests to a web service described by `spec` and records
  them using `recorder`."
  [{:keys [spec agent-count interarrival-sec duration-sec recorder]
    :as opts}]
  (for [agent-id (range agent-count)]
    (let [requests (swagger/generate spec)]
      (loop [[request & more]      requests
             t                     (erlang interarrival-sec)]
        (when (< t duration-sec)
          (recorder (format "%04d/%010d.transit"
                            agent-id
                            (long (* t 1000)))
                    (encode request))
          (recur more (+ t (erlang interarrival-sec))))))))



