;; Copyright (c) 2016 Cognitect, Inc.
;;
;; This file is part of Requestinator.
;;
;; All rights reserved. This program and the accompanying materials
;; are made available under the terms of the Eclipse Public License v1.0
;; which accompanies this distribution, and is available at
;; http://www.eclipse.org/legal/epl-v10.html
(defproject requestinator "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.9.0-alpha13"]
                 [org.clojure/clojurescript "1.9.229"]
                 ;;[org.clojure/clojurescript "1.8.51"]
                 [org.clojure/core.async "0.2.391"]
                 [com.amazonaws/aws-java-sdk "1.11.41"]
                 [org.apache.httpcomponents/httpclient "4.5.2"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/test.check "0.9.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [com.gfredericks/test.chuck "0.2.7"]
                 [com.stuartsierra/component "0.3.1"]
                 [com.cognitect/transit-clj "0.8.290"
                  :exclusions [com.fasterxml.jackson.core/jackson-core]]
                 [com.stuartsierra/log.dev "0.1.0"]
                 [org.clojure/tools.cli "0.3.5"]
                 [org.apache.commons/commons-lang3 "3.4"]
                 [org.craigandera/causatum "0.3.0"]
                 [data.graphql "0.1.0-SNAPSHOT"]
                 [joda-time "2.9.6"]]
  :main ^:skip-aot com.cognitect.requestinator.main
  :repl-options {:init-ns user}
  :source-paths ["src" "spec"]
  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]]
                   :source-paths ["dev"]
                   :jvm-opts ["-Xloggc:/tmp/gc.log"]}
             :profile {:jvm-opts ["-agentpath:/Users/candera/bin/libyjpagent.jnilib"]}})
