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
                 [com.stuartsierra/log.dev "0.1.0"]]
  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]]
                   :source-paths ["dev"]}})
