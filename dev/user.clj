(ns user
  "Namespace for REPL helper functions."
  (:require [clojure.core.async :as async :refer [<!! >!!]]
            [clojure.core.server :as repl-server]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [clojure.repl :refer :all]
            [clojure.test.check.generators :as tcgen]
            [clojure.tools.logging :as log]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [com.cognitect.requestinator.engine :refer :all]
            [com.cognitect.requestinator.generators :as gen]
            [com.cognitect.requestinator.generators.markov :as markov]
            [com.cognitect.requestinator.json :as json-helper]
            [com.cognitect.requestinator.html :as h]
            [com.cognitect.requestinator.main :as main]
            [com.cognitect.requestinator.report :as report]
            [com.cognitect.requestinator.s3 :as s3]
            [com.cognitect.requestinator.serialization :as ser]
            [com.cognitect.requestinator.swagger :as swagger]
            [com.gfredericks.test.chuck.generators :as chuck-gen]
            [simulant.http :as http]))

(def petstore-spec
  (->> "petstore.swagger.json"
       io/resource
       io/reader
       slurp
       json/read-str))

(def amended-spec
  (->> "petstore.amendments.json"
       io/resource
       io/reader
       slurp
       json/read-str
       (json-helper/amend petstore-spec)))

(def param
  (get-in amended-spec
          ["paths" "/pet/{petId}" "get" "parameters" 0]))

(defn run-tests
  []
  (clojure.test/run-tests 'com.cognitect.requestinator.swagger-test
                          'com.cognitect.requestinator.json-test))

(defn generate-params
  [spec op method]
  (->> spec
       (get-in spec ["paths" op method "parameters"])
       (swagger/params-generator spec)
       tcgen/generate))

(defn generate-request
  [{:keys [spec op method mime-type]
    :as opts}]
  (let [{:strs [host basePath schemes]} spec]
    (swagger/request (-> {:mime-type (tcgen/generate (tcgen/elements (get-in spec ["paths" op method "consumes"])))
                  :host      host
                  :base-path basePath
                  :scheme    (tcgen/generate (tcgen/elements schemes))}
                 (merge opts)
                 (assoc :params (generate-params spec op method))))))

(defn repl-server
  [port]
  (repl-server/start-server {:port port
                             :name "requestinator"
                             :accept 'clojure.core.server/repl
                             :daemon false}))
