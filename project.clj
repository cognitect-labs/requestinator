(defproject requestinator "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.9.0-alpha11"]
                 [org.clojure/clojurescript "1.9.198"]
                 ;;[org.clojure/clojurescript "1.8.51"]
                 [org.clojure/core.async "0.2.374"]
                 [com.amazonaws/aws-java-sdk "1.10.52"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/test.check "0.9.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [com.datomic/simulant "0.1.9-SNAPSHOT"
                  :exclusions [org.slf4j/slf4j-nop]]
                 [com.gfredericks/test.chuck "0.2.6"]
                 [com.stuartsierra/component "0.3.1"]
                 [com.cognitect/transit-clj "0.8.285"
                  :exclusions [com.fasterxml.jackson.core/jackson-core]]
                 [com.stuartsierra/log.dev "0.1.0"]
                 [org.clojure/tools.cli "0.3.3"]
                 [org.apache.commons/commons-lang3 "3.4"]]
  :main ^:skip-aot com.cognitect.requestinator.main
  :repl-options {:init-ns user}
  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]]
                   :source-paths ["dev"]
                   :jvm-opts ["-Xloggc:/tmp/gc.log"]}
             :profile {:jvm-opts ["-agentpath:/Users/candera/bin/libyjpagent.jnilib"]}})
