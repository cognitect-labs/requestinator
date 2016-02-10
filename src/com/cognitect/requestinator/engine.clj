(ns com.cognitect.requestinator.engine
  "A library to pull together the various pieces of the Requestinator
  into a working system."
  (:require [com.cognitect.requestinator.swagger :as swagger]
            [com.cognitect.requestinator.json :as json-helper]
            [com.cognitect.requestinator.thread-pool :as thread-pool]
            [com.stuartsierra.component :as component]
            [clojure.core.async :as async :refer [>!! <!!]]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [simulant.http :as http]))

(defn file-recorder
  "Returns a new recorder function that records to files in a directory."
  [dir]
  (io/make-parents (io/file dir "probe.txt"))
  (let [counter (atom 0)]
    (fn [val]
      (log/debug "Recording a value" :counter @counter)
      (->> val
           pr-str
           (spit (io/file dir (format "%s-%010d.edn"
                                      (:agent val)
                                      (swap! counter inc))))))))

(defn s3-recorder
  "Returns a new recorder task that records to files in an S3 bucket"
  [bucket prefix signal]
  (throw (ex-info "Not yet implemented"
                  {:reason :not-yet-implemented})))

(defn erlang
  [mean]
  (- (* (Math/log (rand)) mean)))

(defn create-agent-task
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
        tasks           (map #(create-agent-task opts %1 %2 (format "%04d" %3)) channels request-streams (range))
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

(defn run
  [{:keys [spec agent-count interarrival-sec duration-sec recorder]
    :as opts}]
  (let [{:keys [channels threads]} (start-agents opts)]
    (consume-results channels recorder)
    {:channels channels
     :threads threads}))

