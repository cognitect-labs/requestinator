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

;;; Helper functionaltiy

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

(defn erlang
  [mean]
  (- (* (Math/log (rand)) mean)))

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

;;; Generation

(defn generate-activity-streams
  "Generates timestamped requests to a web service described by `spec`
  and records them using `recorder`. Does not actually issue the
  requests to produce responses."
  [{:keys [spec agent-count interarrival-sec duration-sec recorder]
    :as opts}]
  (->> (for [agent-id (range agent-count)]
         (let [requests (swagger/generate spec)]
           (loop [[request & more]      requests
                  t                     (erlang interarrival-sec)
                  agent-info            {}]
             (if (< duration-sec t)
               [agent-id agent-info]
               (let [path (format "%04d/%010d.transit"
                                  agent-id
                                  (long (* t 1000)))]
                 (recorder path
                           (encode request))
                 (recur more
                        (+ t (erlang interarrival-sec))
                        (assoc-in agent-info
                                  [:requests t]
                                  path)))))))
       (into (sorted-map))
       encode
       (recorder "index.transit")))

;; Execution

(defn elapsed
  [^java.util.Date moment]
  (/ (- (System/currentTimeMillis) (.getTime moment))
     1000.0))

(defn await-t
  [start t]
  (loop []
    (let [remaining-sec (- t (elapsed start))]
      (when (pos? remaining-sec)
        (Thread/sleep (max 1 (long (* remaining-sec 900))))
        (recur)))))

(defn create-fetcher
  "Returns a running fetcher that will get objects described by
  `request-infos` and place them on `output-chan`. `request-infos` is
  a sequence of maps containing key `path`."
  [request-infos output-chan fetch-f]
  (let [worker (Thread.
                (fn []
                  (try
                    (doseq [{:keys [path] :as request-info} request-infos]
                      (log/debug "Fetching" :path path)
                      (async/>!! output-chan (assoc request-info
                                                    :request
                                                    (decode (fetch-f path)))))
                    (catch Throwable t
                      (log/error t "Error in fetcher"))
                    (finally
                      (log/debug "Fetcher finished")
                      (async/close! output-chan)))))]
    (.start worker)
    {::worker worker}))

(defn create-throttler
  "Returns a running throttler that will consume request info maps
  from `input-chan`, wait until the `:t` in each one arrives (relative
  to `start`) and then places them on `output-chan`. Closes
  `output-chan` once `input-chan` closes."
  [start input-chan output-chan]
  (let [worker (Thread.
                (fn []
                  (try
                    (loop []
                      (when-let [{:keys [t] :as request-info} (<!! input-chan)]
                        (log/debug "Throttler awaiting" :t t :path (:path request-info))
                        (await-t start t)
                        (log/debug "Throttler releasing" :t t :path (:path request-info))
                        (>!! output-chan request-info)
                        (recur)))
                    (catch Throwable t
                      (log/error t "Error in throttler"))
                    (finally
                      (log/debug "Throttler finished")
                      (async/close! output-chan)))))]
    (.start worker)
    {::worker worker}))

(defn create-agent
  "Returns a running agent that will consume request info maps from
  `input-chan` and execute them, placing the result on `output-chan`.
  Stops when `input-chan` closes, at which point it closes
  `output-chan`."
  [input-chan output-chan]
  (let [client (http/generate-client (http/cookie-store))
        worker (Thread.
                (fn []
                  (try
                    (loop []
                      (when-let [{:keys [request] :as request-info} (<!! input-chan)]
                        (log/debug "Agent requesting" :path (:path request-info))
                        (>!! output-chan
                             (assoc request-info
                                    :response
                                    (client request)))
                        (recur)))
                    (catch Throwable t
                      (log/error t "Error in agent"))
                    (finally
                      (log/debug "Agent finished")
                      (async/close! output-chan)))))]
    (.start worker)
    {::worker worker}))


(defn create-recorder
  "Returns a running recorder that will monitor channels `chans`,
  taking available maps and passing them to `record-f` a function of
  two arguments: a relative path and a byte array of data.

  Closes the channel `status` when `chans` have all closed."
  [chans record-f status]
  (let [worker (Thread.
                (fn []
                  (try
                    (loop [chans (set chans)]
                      (if (empty? chans)
                        (async/close! status)
                        (let [[val port] (async/alts!! (seq chans))]
                          (if (nil? val)
                            (do
                              (log/debug "Removing a completed channel."
                                         :remaining (dec (count chans)))
                              (recur (disj chans port)))
                            (do
                              (log/debug "Recording a response" :path (:path val))
                              (record-f (:path val) (encode (:response val)))
                              (recur chans))))))
                    (catch Throwable t
                      (log/error t "Error in recorder.")))))]
    (.start worker)
    {::worker worker}))

(defn execute
  [{:keys [fetch-f record-f start recorder-concurrency]}]
  (let [index       (decode (fetch-f "index.transit"))
        agent-count (count index)
        processes   (for [[agent-id agent-info] index]
                      (let [request-infos (sort-by :t
                                                   (for [[t path] (:requests agent-info)]
                                                     {:t        t
                                                      :path     path
                                                      :agent-id agent-id}))
                            ->throttler   (async/chan 10)
                            ->agent       (async/chan 1)
                            ->recorder    (async/chan 10)]
                        {:agent-id    agent-id
                         :->throttler ->throttler
                         :->agent     ->agent
                         :->recorder  ->recorder
                         :fetcher     (create-fetcher  request-infos ->throttler fetch-f)
                         :throttler   (create-throttler start ->throttler ->agent)
                         :agent       (create-agent ->agent ->recorder)}))
        ->recorders  (map :->recorder processes)
        status       (async/chan)]
    {:processes (into [] processes)
     :recorder  (into [] (repeatedly recorder-concurrency
                                     #(create-recorder ->recorders record-f status)))
     :status     status}))
