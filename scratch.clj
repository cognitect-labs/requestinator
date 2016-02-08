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

(last (gen/sample (request-generator amended-spec) 100))

(gen/generate
 (object-generator
  {}
  {"properties"
   {"foo" {"type" "integer"}
    "bar" {"type" "string"}
    "quux" {"type" "object"
            "properties" {"baaz" {"type" "integer"}}}}}))

(first (generate amended-spec))

(generate-params amended-spec "/pet/findByStatus" "get")

(generate-params amended-spec "/store/order" "post")

(generate-request {:spec amended-spec :op "/pet" :method "post"})
(generate-request {:spec amended-spec :op "/pet" :method "put"})

(generate-request {:spec amended-spec :op "/pet/findByStatus" :method "get"})

(generate-request {:spec amended-spec :op "/pet/findByTags" :method "get"})

(generate-request {:spec amended-spec :op "/pet/{petId}" :method "delete"})

(generate-request {:spec amended-spec :op "/pet/{petId}/uploadImage" :method "post"})

(generate-request {:spec amended-spec :op "/store/order" :method "post"})

(generate-request {:spec amended-spec :op "/store/order/{orderId}" :method "delete"})
(generate-request {:spec amended-spec :op "/store/order/{orderId}" :method "get"})



