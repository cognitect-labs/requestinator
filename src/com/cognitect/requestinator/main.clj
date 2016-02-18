(ns com.cognitect.requestinator.main
  "An interface for external (command-line) invocation of
  requestinator functionality."
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :as log]
            [com.cognitect.requestinator.engine :as engine]
            [com.cognitect.requestinator.json :as json-helper]
            [com.cognitect.requestinator.s3 :as s3]))

(comment
 (def cli-options
   [["-e" "--env ENV_FILE" "Environment file"
     :id :env-file]
    ["-r" "--report-dir DIRECTORY" "Directory to write the report to"
     :id :report-dir]
    ["-s" "--show-report" "Opens the generated report in the default browser when the test ends"]
    ["-t" "--test-cases TEST_CASE_FILE" "Optional test cases file; a CSV. See README for format, and test-cases-example.csv"
     :id :cases-file]
    ["-c" "--data-consistency" "Runs the data consistency validation rules."
     :id :data-consistency?]
    [nil "--db-uri DB_URI" "The Datomic database URI to use. Defaults to an in-memory database."
     :id :db-uri]
    [nil "--script-types SCRIPT_TYPES" "The type of scripts to run. Specify multiple values with distinct flag/value pairs. Accepts \"web\", \"android\". Defaults to \"web\"."
     :id :script-types
     :assoc-fn (fn [m k v] (update-in m [k] conj (keyword v)))
     :validate [(partial contains? #{"web" "android"})
                "Accepts \"web\", \"android\"."]]
    [nil "--bash-properties PROPS_FILE" "Optional. Path to a file where properties of the run will be written as Bash-compatible properties."
     :id :bash-properties]
    [nil "--git-sha GIT_SHA" "Optional. The git SHA of the running code. Used to cache previously-generated reports."]
    ["-h" "--help"]]))

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

(defn read-spec
  [spec-uri amendments-uri]
  (let [spec       (->> spec-uri
                        slurp
                        json/read-str)
        amendments (some->> amendments-uri
                            slurp
                            json/read-str)]
    (json-helper/amend spec amendments)))

(defn create-recorder
  [uri]
  (cond
    (.startsWith uri "file:///")
    (engine/file-recorder (subs uri (count "file://")))

    (.startsWith uri "file:")
    (engine/file-recorder (subs uri (count "file:")))

    (.startsWith uri "s3://")
    (let [without-proto (subs uri (count "s3://"))
          [bucket & paths] (str/split without-proto #"/")]
      (engine/s3-recorder (s3/client)
                          bucket
                          (str/join paths "/")))

    :else
    (throw (ex-info (str "Unsupported destination: " uri)
                    {:reason ::unsupported-destination
                     :uri    uri}))))

(defn generate
  [{:keys [spec-uri amendments-uri destination agent-count interarrival-sec duration-sec]
    :as options}
   arguments]
  (log/debug "Generate" :options options)
  (let [spec     (read-spec spec-uri amendments-uri)
        recorder (create-recorder destination)]
   (engine/generate-activity-streams {:spec             spec
                                      :agent-count      agent-count
                                      :interarrival-sec interarrival-sec
                                      :duration-sec     duration-sec
                                      :recorder         recorder}))
  {:code    0
   :message "Success"})

(defn execute
  [options arguments]
  (println "TODO: Execute" :options options :arguments arguments))

(def commands
  {"generate" {:cli-spec [["-s" "--spec-uri SWAGGERURI" "Path to Swagger JSON spec"
                           :id :spec-uri
                           :validate [some? "Required"]]
                          ["-a" "--amendments-uri AMENDMENTSURI" "Path to spec amendments"
                           :id :amendments-uri]
                          ["-d" "--destination DESTINATION" "Path to destination for generated requests"
                           :id :destination]
                          ["-n" "--agent-count AGENT_COUNT" "Number of agents to use"
                           :id :agent-count
                           :parse-fn #(Long. %)
                           :validate [integer? "Must be an integer"]]
                          ["-i" "--interarrival-sec INTERARRIVAL_SEC"
                           "Mean of interval between requests, per agent, in seconds."
                           :id :interarrival-sec
                           :parse-fn #(Double. %)
                           :validate [number? "Must a number."]]
                          ["-t" "--duration-sec DURATION_SEC"
                           "Duration of activity stream, in seconds."
                           :id :duration-sec
                           :parse-fn #(Double. %)
                           :validate [number? "Must a number."]]]
              :impl generate}
   "execute" {}})

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
