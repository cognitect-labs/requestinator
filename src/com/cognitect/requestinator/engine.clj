(ns com.cognitect.requestinator.engine
  "A library to pull together the various pieces of the Requestinator
  into a working system."
  (:require [cognitect.transit :as transit]
            [com.cognitect.requestinator.json :as json-helper]
            [com.cognitect.requestinator.http :as http]
            [com.cognitect.requestinator.report :as report]
            [com.cognitect.requestinator.request :as request]
            [com.cognitect.requestinator.scheduler :as schedule]
            [com.cognitect.requestinator.serialization :as ser]
            [com.cognitect.requestinator.sexp :as sexp]
            [com.cognitect.requestinator.thread-pool :as thread-pool]
            [com.stuartsierra.component :as component]
            [requestinator :as r]
            [requestinator.agent :as agent]
            [clojure.core.async :as async :refer [>!! <!!]]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log])
  (:import [java.io
            ByteArrayInputStream
            ByteArrayOutputStream
            FileOutputStream
            InputStream]
           [java.nio.file CopyOption Files]))

;;; Parameter evaluation

(defprotocol Evaluable
  (-evaluate [this context]
    "Evaluates something in a given context"))

(defrecord Recall [val default]
  Evaluable
  (-evaluate [this context]
    (log/debug "recalling" :val val :default default)
    (get context val default)))

(defn make-recall
  "Creates a new recall, an object that can recall a previously-stored
  value from agent memory."
  [[val default]]
  (->Recall val default))

;; We consolidate calls to the protocol through this function so we have
;; a common place to put logging or other cross-cutting concerns.
(defn evaluate
  "Evaluate a value in a given context. Prefer this function to the
  -evaluate protocol function."
  [val context]
  ;; I could extend the protocol to Object, but I find this approach
  ;; simpler. We already have this function for good reasons - why not
  ;; make things explicit?
  (log/debug "evaluate" :val val :context context)
  (if (satisfies? Evaluable val)
    (-evaluate val context)
    val))

;;; Serialization

(ser/register-handlers!
 {:transit {:read  {(.getName Recall) (transit/record-read-handler Recall)}
            :write {Recall (transit/record-write-handler Recall)}}
  :edn     {:read {'requestinator/recall make-recall}}})

;;; Generation

(defn generate-activity-streams
  "Generates timestamped requests to a web service via `generator`, an
  instance of
  `com.cognitect.requestinator.request.RequestGenerator` and
  records them using `recorder`. Does not actually issue the requests
  to produce responses."
  [{:keys [::r/spec ::r/duration ::r/agent-groups recorder]
    :as opts}]
  (->> (for [{:keys [::agent/count ::agent/tag ::agent/scheduler]} agent-groups
             agent-num (range count)
             :let [agent-id (format "%s-%04d" tag agent-num)]]
         (loop [schedule   (schedule/schedule scheduler spec)
                agent-info {}]
           (let [[{:keys [::schedule/t ::schedule/request] :as action} & more] schedule]
             (if (or (nil? request) (< duration t))
               [agent-id agent-info]
               (let [path (format "%s/%010d.transit"
                                  agent-id
                                  (long (* t 1000)))]
                 (recorder path
                           (ser/encode action))
                 (recur more
                        (assoc-in agent-info
                                  [:requests t]
                                  path)))))))
       (into (sorted-map))
       ser/encode
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
  `action-infos` and place them on `output-chan`. `action-infos` is
  a sequence of maps containing key `path`."
  [action-infos output-chan fetch-f]
  (let [worker (Thread.
                (fn []
                  (try
                    (doseq [{:keys [path] :as action-info} action-infos]
                      (log/debug "Fetching" :path path)
                      (async/>!! output-chan
                                 (assoc action-info
                                        :action (ser/decode (fetch-f path)))))
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
                      (when-let [{:keys [t] :as action-info} (<!! input-chan)]
                        (log/debug "Throttler awaiting" :t t :path (:path action-info))
                        (await-t start t)
                        (log/debug "Throttler releasing" :t t :path (:path action-info))
                        (>!! output-chan action-info)
                        (recur)))
                    (catch Throwable t
                      (log/error t "Error in throttler"))
                    (finally
                      (log/debug "Throttler finished")
                      (async/close! output-chan)))))]
    (.start worker)
    {::worker worker}))

(defmulti storage-op
  "A storage operation, like `response-body-json` that can be used to
  retrieve values from a response for storage in agent memory. If the
  operation returns nil, nothing is stored."
  (fn [op op-context & args] op))

(defmethod storage-op 'response-body-json
  [op {:keys [response]} path]
  (log/debug "storing response-body-json" :path path)
  (-> response
      :body
      json/read-str
      (json-helper/select path)))

(defn things-to-store
  "Returns a map of values to be stored in the agent context."
  [{:keys [agent-memory store request response] :as context}]
  (into {}
        (for [[k v] store
              :let [e (sexp/eval v
                                 #(get context %)
                                 (fn [op args]
                                   (apply storage-op op context args)))]
              :when (some? e)]
          [k e])))

(defn create-agent
  "Returns a running agent that will consume request info maps from
  `input-chan` and execute them, placing the result on `output-chan`.
  Stops when `input-chan` closes, at which point it closes
  `output-chan`."
  [start input-chan output-chan]
  (let [client (http/generate-client (http/cookie-store))
        worker (Thread.
                (fn []
                  (try
                    (loop [agent-memory {}]
                      (log/debug "agent-memory" agent-memory)
                      (when-let [{:keys [action] :as action-info} (<!! input-chan)]
                        (log/debug "Agent requesting" :path (:path action-info))
                        (let [request-template (::schedule/request action)
                              request (request/fill-in request-template agent-memory)
                              actual-t (elapsed start)
                              begin (System/currentTimeMillis)
                              response (client request)
                              end (System/currentTimeMillis)
                              duration (/ (- end begin) 1000.0)]
                          (>!! output-chan
                               (assoc action-info
                                      :actual-t actual-t
                                      :request request
                                      :request-template request-template
                                      :response response
                                      :duration duration))
                          (recur (merge agent-memory
                                        (things-to-store {:agent-memory agent-memory
                                                          :store (::schedule/store action)
                                                          :request request
                                                          :response response}))))))
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

  Sends the summary index to channel `->consolidator` and closes it
  when `chans` have all closed."
  [chans record-f ->consolidator]
  (let [worker (Thread.
                (fn []
                  (try
                    (loop [chans (set chans)
                           index []]
                      (if (empty? chans)
                        (do
                          (>!! ->consolidator index)
                          (async/close! ->consolidator))
                        (let [[val port] (async/alts!! (seq chans))]
                          (if (nil? val)
                            (do
                              (log/debug "Removing a completed channel."
                                         :remaining (dec (count chans)))
                              (recur (disj chans port) index))
                            (do
                              (log/debug "Recording a response" :path (:path val))
                              (record-f (:path val) (ser/encode val))
                              (recur chans
                                     (conj index
                                           (-> val
                                               (select-keys [:t
                                                             :actual-t
                                                             :path
                                                             :agent-id
                                                             :duration])
                                               (assoc :status
                                                      (get-in val [:response :status]))))))))))
                    (catch Throwable t
                      (log/error t "Error in recorder.")))))]
    (.start worker)
    {::worker worker}))

(defn create-consolidator
  "Returns a running consolidator whose job it is to collate the
  indexes received from `chans` and write them to index.transit once
  they have all closed. Closes channel `status` when done."
  [chans record-f status]
  (let [worker (Thread.
                (fn []
                  (try
                    (loop [chans (set chans)
                           index []]
                      (if (empty? chans)
                        (record-f "index.transit" (ser/encode index))
                        (let [[val port] (async/alts!! (seq chans))]
                          (if (nil? val)
                            (do
                              (log/debug "Consolidator removing a completed channel."
                                         :remaining (dec (count chans)))
                              (recur (disj chans port) index))
                            (recur chans (into index val))))))
                    (async/close! status)
                    (catch Throwable t
                      (log/error t "Error in consolidator.")
                      (>!! status [:exception t])
                      (async/close! status)))))]
    (.start worker)
    {::worker worker}))

(defn execute
  [{:keys [fetch-f record-f start recorder-concurrency]}]
  (let [index       (ser/decode (fetch-f "index.transit"))
        agent-count (count index)
        processes   (for [[agent-id agent-info] index]
                      (let [action-infos (sort-by :t
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
                         :fetcher     (create-fetcher action-infos
                                                      ->throttler
                                                      fetch-f)
                         :throttler   (create-throttler start ->throttler ->agent)
                         :agent       (create-agent start ->agent ->recorder)}))
        ->recorders  (map :->recorder processes)
        ->consolidators (repeatedly recorder-concurrency #(async/chan 10))
        status       (async/chan)]
    {:processes (into [] processes)
     :consolidator (create-consolidator ->consolidators record-f status)
     :recorder  (->> ->consolidators
                     (map #(create-recorder ->recorders record-f %))
                     (into []))
     :status     status}))

;;; Reporting

(defn report
  [{:keys [fetch-f record-f] :as opts}]
  (report/report opts))
