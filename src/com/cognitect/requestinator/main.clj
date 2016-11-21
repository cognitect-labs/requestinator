(ns com.cognitect.requestinator.main
  "An interface for external (command-line) invocation of
  requestinator functionality."
  (:require [clojure.core.async :refer [<!!] :as async]
            [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :as log]
            [com.cognitect.requestinator.engine :as engine]
            [com.cognitect.requestinator.report :as report]
            [com.cognitect.requestinator.serialization :as ser]
            ;; We need these to be loaded for their read/write support
            [com.cognitect.requestinator.swagger]
            [com.cognitect.requestinator.graphql]
            [com.cognitect.requestinator.schedulers.markov]
            [com.cognitect.requestinator.schedulers.uniform]))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn usage [options-summary]
  (->> ["Usage: requestinator action [options]"
        ""
        "Options:"
        options-summary
        ""
        "Actions:"
        "  generate Generate web service requests from a Swagger spec."
        "  execute  Execute requests, recording the responses."]
       (str/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (str/join \newline errors)))

(defn parse-time
  "Parses a java.util.Date from the provided string. Date formats
  supported are as per
  `org.joda.time.format.ISODateTimeFormat/dateTimeParser`, but
  additionally supports a bare time (with no leading 'T'). If date is
  not provided, today is assumed. Assumes the default time zone if one
  is not provided."
  [s]
  (let [fmt    (org.joda.time.format.ISODateTimeFormat/dateTimeParser)
        parse #(try
                 (.parseDateTime fmt %)
                 (catch IllegalArgumentException _ nil))]
    (when-let [d (or (parse s)
                     (parse (str "T" s)))]
      (.toDate (if (= [1970 1] [(.getYear d) (.getDayOfYear d)])
                 (let [now (org.joda.time.DateTime/now)]
                   (.withDate d (.getYear now) (.getMonthOfYear now) (.getDayOfMonth now)))
                 d)))))

(defn before?
  "Returns true if Date `a` is before `b`."
  [^java.util.Date a ^java.util.Date b]
  (< (.getTime a) (.getTime b)))

(defn valid-start?
  "Returns true if `d` is a valid start time."
  [d]
  (and (some? d)
       (before? (java.util.Date.) d)))

(def transit-serialization-types
  [:swagger
   :graphql
   :engine])

(def edn-serialization-types
  [:swagger
   :graphql
   :engine
   :uniform
   :markov])

(ser/register-handlers!
 {:edn {:read {'seconds identity
               'minutes #(* % 60)
               'hours   #(* % 60 60)
               'url     identity}}})

(defn read-params
  [params-uri]
  (let [fetcher (ser/create-fetcher params-uri)]
    (->> (fetcher "")
         String.
         ser/edn-read-string)))

(defn generate
  [{:keys [destination
           params-uri]
    :as options}
   arguments]
  (log/debug "Generate" :options options)
  (let [recorder (ser/create-recorder destination)
        params (read-params params-uri)]
    (engine/generate-activity-streams
     (assoc params
            :recorder recorder)))
  {:code    0
   :message "Success"})

(defn execute
  [{:keys [source destination start groups recorder-concurrency] :as options} arguments]
  (log/debug "Execute" :options)
  (let [fetcher  (ser/create-fetcher source)
        recorder (ser/create-recorder destination)
        {:keys [status]} (engine/execute {:fetch-f              fetcher
                                          :record-f             recorder
                                          :start                (or start
                                                                    (java.util.Date. (+ (System/currentTimeMillis)
                                                                                        10000)))
                                          :groups               groups
                                          :recorder-concurrency recorder-concurrency})]
    (loop []
      (when-let [msg (<!! status)]
        (println msg)
        (recur)))
    (log/info "Execution complete")
    {:code    0
     :message "Success"}))

(defn report
  [{:keys [sources destination] :as options} arguments]
  (println "Building report" :sources sources :destination destination)
  (let [fetchers (map ser/create-fetcher sources)
        recorder (ser/create-recorder destination)]
    (report/report {:fetchers fetchers
                    :recorder recorder}))
  {:code 0
   :message "Success"})

(def commands
  {"generate" {:cli-spec [["-d"
                           "--destination DESTINATION"
                           "Path to destination for generated requests"
                           :id :destination
                           :validate [some? "Required"]]
                          ["-p"
                           "--params PARAMS_LOCATION"
                           "Location of parameters file"
                           :id :params-uri
                           :validate [some? "Required"]]]
               :impl generate}
   "execute" {:cli-spec [["-s"
                          "--source REQUEST_SOURCE"
                          "Path to location of requests"
                          :id :source]
                         ["-d"
                          "--destination DESTINATION"
                          "Path to destination for results"
                          :id :destination]
                         ["-t"
                          "--start-time START_TIME"
                          "Start time for the test run. Defaults to an immediate start."
                          :id :start-time
                          :parse-fn parse-time
                          :validate [valid-start? "Must be a valid time in the future."]]
                         ["-n"
                          "--recorder-concurrency RECORDER_CONCURRENCY"
                          "Number of threads to use to record results"
                          :id :recorder-concurrency
                          :parse-fn #(Long. %)
                          :validate [integer? "Must be an integer"]]
                         ["-g"
                          "--group GROUP"
                          "Agent group to execute. Can be specified multiple times to execute more than one group concurrently. Optional - defaults to executing all groups."
                          :id :groups
                          :assoc-fn (fn [m k v]
                                      (assoc m k (conj (get m k #{}) v)))]]
              :impl execute}
   "report" {:cli-spec [["-s"
                         "--source RESULTS_SOURCE"
                         "Path to the directory containing the results index. Can be specified multiple times to merge multiple runs."
                         :id :sources
                         :assoc-fn (fn [m k v]
                                     (assoc m k (conj (get m k #{}) v)))]
                        ["-d"
                         "--destination DESTINATION"
                         "Path where report files will be writen."
                         :id :destination]]
             :impl report}})

;; REPL helper so we don't actually exit the process
(defn main*
  [command & args]
  (let [{:keys [cli-spec impl]} (get commands command)
        {:keys [options arguments errors summary]} (parse-opts args cli-spec)]
    (cond
      (:help options)
      {:code    0
       :message (usage summary)}

      errors
      {:code   1
       :message (error-msg errors)}

      (nil? impl)
      {:code   1
       :message (usage summary)}

      :else
      (impl options arguments))))

(defn -main
  [command & args]
  (let [{:keys [code message]} (apply main* command args)]
    (exit code message)))
