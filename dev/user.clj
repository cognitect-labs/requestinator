(ns user
  "Namespace for REPL helper functions."
  (:require [clojure.core.async :as async :refer [<!! >!!]]
            [clojure.core.server :as repl-server]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [clojure.repl :refer :all]
            [clojure.spec :as s]
            [clojure.spec.gen :as sgen]
            [clojure.test.check.generators :as tcgen]
            [clojure.tools.logging :as log]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [cognitect.transit :as transit]
            [com.cognitect.requestinator.engine :refer :all]
            [com.cognitect.requestinator.scheduler :as schedule]
            [com.cognitect.requestinator.schedulers.markov :as markov]
            [com.cognitect.requestinator.schedulers.uniform :as uniform]
            [com.cognitect.requestinator.graphql :as graphql]
            [com.cognitect.requestinator.json :as json-helper]
            [com.cognitect.requestinator.html :as h]
            [com.cognitect.requestinator.http :as http]
            [com.cognitect.requestinator.main :as main]
            [com.cognitect.requestinator.report :as report]
            [com.cognitect.requestinator.s3 :as s3]
            [com.cognitect.requestinator.serialization :as ser]
            [com.cognitect.requestinator.swagger :as swagger]
            [com.gfredericks.test.chuck.generators :as chuck-gen]))

(defn run-tests
  []
  (clojure.test/run-tests 'com.cognitect.requestinator.swagger-test
                          'com.cognitect.requestinator.json-test))

(defn repl-server
  ([] (repl-server 5560))
  ([port]
   (repl-server/start-server {:port port
                              :name "requestinator"
                              :accept 'clojure.core.server/repl
                              :daemon false})))
