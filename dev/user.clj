(ns user
  "Namespace for REPL helper functions."
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.repl :refer :all]
            [clojure.test.check.generators :as gen]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [com.cognitect.requestinator.swagger :refer :all]
            [com.cognitect.requestinator.json :as json-helper]
            [com.gfredericks.test.chuck.generators :as chuck-gen]))

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
       (params-generator spec)
       gen/generate))

(defn generate-request
  [{:keys [spec op method mime-type]
    :as opts}]
  (request (-> {:mime-type "application/x-www-form-urlencoded"}
               (merge opts)
               (assoc :params (generate-params spec op method)))))
