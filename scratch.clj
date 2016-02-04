(require '[clojure.test.check :as c]
         '[clojure.test.check.generators :as gen]
         '[clojure.data.json :as json])

;; Can we generate an infinite sequence of values from a generator?

(->> (gen/sample-seq gen/int)
     (drop 100000)
     (take 10))

(->> "http://petstore.swagger.io/v2/swagger.json"
    slurp
    (spit "petstore.swagger.json"))

(def petstore-spec
  (->> "petstore.swagger.json"
      slurp
      json/read-str))

(gen/sample (request-generator petstore-spec) 3)

(gen/generate
 (object-generator
  {}
  {"properties"
   {"foo" {"type" "integer"}
    "bar" {"type" "string"}
    "quux" {"type" "object"
            "properties" {"baaz" {"type" "integer"}}}}}))

(first (generate petstore-spec))

(generate-params petstore-spec "/pet/findByStatus" "get")

(generate-params petstore-spec "/store/order" "post")

(generate-request {:spec petstore-spec :op "/pet" :method "post"})
(generate-request {:spec petstore-spec :op "/pet" :method "put"})

(generate-request {:spec petstore-spec :op "/pet/findByStatus" :method "get"})

(generate-request {:spec petstore-spec :op "/pet/findByTags" :method "get"})

(generate-request {:spec petstore-spec :op "/pet/{petId}" :method "delete"})

(generate-request {:spec petstore-spec :op "/pet/{petId}/uploadImage" :method "post"})


(generate-request {:spec petstore-spec :op "/store/order" :method "post"})


