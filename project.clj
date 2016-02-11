(defproject requestinator "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.374"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/test.check "0.9.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [com.amazonaws/aws-java-sdk "1.10.50"]
                 [com.datomic/simulant "0.1.9-SNAPSHOT"
                  :exclusions [org.slf4j/slf4j-nop]]
                 [com.gfredericks/test.chuck "0.2.5"]
                 [com.stuartsierra/component "0.3.1"]
                 [com.cognitect/transit-clj "0.8.285"]

                 ;; Logging - oy

                 ;; Use Logback as the main logging implementation:
                 [ch.qos.logback/logback-classic "1.1.2"]

                 ;; Logback implements the SLF4J API:
                 [org.slf4j/slf4j-api "1.7.7"]

                 ;; Redirect Apache Commons Logging to Logback via the SLF4J API:
                 [org.slf4j/jcl-over-slf4j "1.7.7"]

                 ;; Redirect Log4j 1.x to Logback via the SLF4J API:
                 [org.slf4j/log4j-over-slf4j "1.7.7"]

                 ;; Redirect Log4J 2.x to Logback via the SLF4J API:
                 [org.apache.logging.log4j/log4j-to-slf4j "2.0.2"]

                 ;; Redirect java.util.logging to Logback via the SLF4J API.
                 ;; Requires installing the bridge handler, see README:
                 [org.slf4j/jul-to-slf4j "1.7.7"]]
  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]]
                   :source-paths ["dev"]}})
