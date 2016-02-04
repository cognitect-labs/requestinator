(ns com.cognitect.requestinator.swagger-test
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.test :refer :all]
            [clojure.test.check.generators :as gen]
            [com.cognitect.requestinator.swagger :refer :all]))

(def petstore-spec
  (->> "petstore.swagger.json"
       io/resource
       io/reader
       slurp
       json/read-str))

(defn valid-map?
  [schema val]
  (is (= (keys schema) (keys val)))
  (is (every? (fn [[k v]]
                (if (map? v)
                  (valid-map? v (get val k))
                  (v (get val k))))
              schema)))

(deftest object-generator-test
  (let [val (gen/generate  (object-generator
                            {}
                            {"properties"
                             {"foo" {"type" "integer"}
                              "bar" {"type" "string"}
                              "quux" {"type" "object"
                                      "properties" {"baaz" {"type" "integer"}}}}}))]
    (valid-map?
     {"foo" integer?
      "bar" string?
      "quux" {"baaz" integer?}}
     val)))

(defn param-map
  [op method]
  (->> (params-generator (get petstore-spec "definitions")
                         (get-in petstore-spec ["paths" op method "parameters"]))
       gen/generate
       (map (fn [{:keys [name] :as p}] [name p]))
       (into {})))

(defn params-valid?
  [params validators]
  (and (= (keys params) (keys validators))
       (every? (fn [[name validator]]
                 (validator (get-in params [name :value])))
               validators)))

(defn valid-pet?
  [pet]
  ;; TODO
  false)

(deftest param-generator-test
  (are [op method param-validators]
      (is (params-valid? (param-map op method) param-validators))
    ;; Empty parameter list
    "/user/logout" "get" {}
    ;; Single number param
    "/store/order/{orderId}" "get" {"orderId" number?}
    ;; Schema-described parameter
    "/pet" "put" {"body" valid-pet?}
    ))
