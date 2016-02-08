(ns user
  "Namespace for REPL helper functions."
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.repl :refer :all]
            [clojure.test.check.generators :as gen]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [com.cognitect.requestinator.swagger :refer :all]
            [com.cognitect.requestinator.json :as json-helper]))

(def petstore-spec
  (->> "petstore.swagger.json"
       io/resource
       io/reader
       slurp
       json/read-str))

(def param
  (get-in petstore-spec
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
  (request (-> {:mime-type "application/x-www-form-encoded"}
               (merge opts)
               (assoc :params (generate-params spec op method)))))
