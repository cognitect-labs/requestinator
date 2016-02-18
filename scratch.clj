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

(take 3 (gen/sample (request-generator amended-spec)))

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



(let [swagger-url "http://petstore.swagger.io/v2/swagger.json"
      amendments  (->> "petstore.amendments.json"
                       io/resource
                       io/reader
                       slurp
                       json/read-str)
      spec        (-> swagger-url
                      slurp
                      json/read-str
                      (json-helper/amend amendments))
      client      (http/generate-client (http/cookie-store))
      request     (first (generate spec))]
  (client request))

(let [swagger-url "http://petstore.swagger.io/v2/swagger.json"
      amendments  (->> "petstore.amendments.json"
                       io/resource
                       io/reader
                       slurp
                       json/read-str)
      spec        (-> swagger-url
                      slurp
                      json/read-str
                      (json-helper/amend amendments))
      request     (generate-request {:spec   spec
                                     :op     "/pet/{petId}/uploadImage"
                                     :method "post"
                                     :mime-type "multipart/form-data"})
      client      (http/generate-client (http/cookie-store))]
  ;;request
  (client request)
  )


(let [swagger-url "http://petstore.swagger.io/v2/swagger.json"
      amendments  (->> "petstore.amendments.json"
                       io/resource
                       io/reader
                       slurp
                       json/read-str)
      spec        (-> swagger-url
                      slurp
                      json/read-str
                      (json-helper/amend amendments))
      now         (java.util.Date.)
      dir         (io/file "/tmp" (format "%TFT%TT" now now))
      recorder    (file-recorder dir)]
  (generate-activity-streams {:spec             spec
                              :agent-count      1
                              :interarrival-sec 1
                              :duration-sec     10
                              :recorder         recorder})
  dir)


(foo)


(let [in (java.io.FileInputStream. "/tmp/2016-02-11T16:05:24/index.transit")
      reader (transit/reader in :json)]
  (transit/read reader))

(execute {:fetch-f (file-fetcher "/tmp/2016-02-16T15:15:00")
          :record-f (fn [path bytes] (log/debug :path path :bytes (alength bytes)))
          :start (java.util.Date.)
          :recorder-concurrency 1})

(execute {:fetch-f (file-fetcher "/tmp/2016-02-16T15:15:00")
          :record-f (file-recorder "/tmp/2016-02-16T15:15:00/responses")
          :start (java.util.Date. (+ (System/currentTimeMillis) 20000))
          :recorder-concurrency 1})


;;; File test


(let [swagger-url "http://petstore.swagger.io/v2/swagger.json"
      amendments  (->> "petstore.amendments.json"
                       io/resource
                       io/reader
                       slurp
                       json/read-str)
      spec        (-> swagger-url
                      slurp
                      json/read-str
                      (json-helper/amend amendments))
      now         (java.util.Date.)
      dir         (io/file "/tmp" (format "%TFT%TT" now now))
      recorder    (file-recorder dir)]
  (generate-activity-streams {:spec             spec
                              :agent-count      10
                              :interarrival-sec 1
                              :duration-sec     60
                              :recorder         recorder})
  dir)

(let [dir "/tmp/2016-02-16T15:43:39"]
 (execute {:fetch-f (file-fetcher dir)
           :record-f (file-recorder (io/file dir "responses"))
           :start (java.util.Date.)
           :recorder-concurrency 5}))


;;; S3 Test

(let [swagger-url "http://petstore.swagger.io/v2/swagger.json"
      amendments  (->> "petstore.amendments.json"
                       io/resource
                       io/reader
                       slurp
                       json/read-str)
      spec        (-> swagger-url
                      slurp
                      json/read-str
                      (json-helper/amend amendments))
      now         (java.util.Date.)
      recorder    (s3-recorder (s3/client)
                               "com.cognitect.requestinator.test"
                               (format "test/%TFT%TT" now now))]
  (generate-activity-streams {:spec             spec
                              :agent-count      10
                              :interarrival-sec 1
                              :duration-sec     60
                              :recorder         recorder}))

(let [bucket "com.cognitect.requestinator.test"
      prefix "test/2016-02-16T16:28:39"
      now    (java.util.Date.)
      folder (format "responses/%TFT%TT" now now)]
  (execute {:fetch-f (s3-fetcher (s3/client) bucket prefix)
            :record-f (s3-recorder (s3/client) bucket (s3/combine-paths prefix
                                                                        folder))
            :start (java.util.Date.)
            :recorder-concurrency 5}))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(refresh)
(let [now (java.util.Date.)
      now-dir  (str "file:///tmp/" (format "%TFT%TT" now now))]
 (main/main* "generate"
             "-s" "http://petstore.swagger.io/v2/swagger.json"
             "-a" "file:resources/petstore.amendments.json"
             "-d" now-dir
             "-n" 3
             "-i" 1.5
             "-t" 21.2))

(let [now (java.util.Date.)
      now-dir  (str "file:///tmp/" (format "%TFT%TT" now now))]
  (main/main* "generate"
              "--spec-uri" "http://petstore.swagger.io/v2/swagger.json"
              "--destination" now-dir
              "--agent-count" "3"
              "--interarrival-sec" "0.5"
              "--duration-sec" "60"))
