(ns com.cognitect.requestinator.main
  "An interface for external (command-line) invocation of
  requestinator functionality."
  (:require [clojure.core.async :refer [<!!] :as async]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :as log]
            [com.cognitect.requestinator.engine :as engine]
            [com.cognitect.requestinator.generators :as gen]
            [com.cognitect.requestinator.report :as report]
            [com.cognitect.requestinator.serialization :as ser]
            [com.cognitect.requestinator.swagger :as swagger]))

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

(defn read-params
  [params-uri]
  (let [fetcher (ser/create-fetcher params-uri)]
    (->> (fetcher "")
         String.
         (edn/read-string
          {:readers
           (merge gen/readers
                  {'requestinator.spec/swagger #(swagger/read-spec params-uri %)
                   'seconds                    identity
                   'minutes                    #(/ % 60)
                   'hours                      #(/ % 60 60)
                   ;; For now, just read URLs as strings
                   'url                        identity})}))))

(defn generate
  [{:keys [destination
           params-uri]
    :as options}
   arguments]
  (log/debug "Generate" :options options)
  (let [recorder (ser/create-recorder destination)
        params (read-params params-uri)]
    (engine/generate-activity-streams (assoc params :recorder recorder)))
  {:code    0
   :message "Success"})

(defn execute
  [{:keys [source destination start recorder-concurrency] :as options} arguments]
  (log/debug "Execute" :options)
  (let [fetcher  (ser/create-fetcher source)
        recorder (ser/create-recorder destination)
        {:keys [status]} (engine/execute {:fetch-f              fetcher
                                          :record-f             recorder
                                          ;; There is support in the API for delaying the
                                          ;; start, but I haven't figured out how best to
                                          ;; pass it in via the CLI, so for the moment we
                                          ;; just go with "10 seconds from now"
                                          :start                (java.util.Date. (+ (System/currentTimeMillis)
                                                                                    10000))
                                          :recorder-concurrency recorder-concurrency})]
    (loop []
      (when-let [msg (<!! status)]
        (println msg)
        (recur)))
    (log/info "Execution complete")
    {:code    0
     :message "Success"}))

(defn report
  [{:keys [source destination] :as options} arguments]
  (println "Building report" :source source :destination destination)
  (let [fetcher (ser/create-fetcher source)
        recorder (ser/create-recorder destination)]
    (report/report {:fetch-f fetcher
                    :record-f recorder}))
  {:code 0
   :message "Success"})

(def commands
  {"generate" {:cli-spec [["-d" "--destination DESTINATION" "Path to destination for generated requests"
                           :id :destination
                           :validate [some? "Required"]]
                          ["-p" "--params PARAMS_LOCATION"
                           "Location of parameters file"
                           :id :params-uri
                           :validate [some? "Required"]]]
               :impl generate}
   "execute" {:cli-spec [["-s" "--source REQUEST_SOURCE" "Path to location of requests"
                          :id :source]
                         ["-d" "--destination DESTINATION" "Path to destination for results"
                          :id :destination]
                         ["-n" "--recorder-concurrency RECORDER_CONCURRENCY" "Number of threads to use to record results"
                          :id :recorder-concurrency
                          :parse-fn #(Long. %)
                          :validate [integer? "Must be an integer"]]]
              :impl execute}
   "report" {:cli-spec [["-s" "--source RESULTS_SOURCE" "Path to the directory containing the results index."
                         :id :source]
                        ["-d" "--destination DESTINATION" "Path where report files will be writen."
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
