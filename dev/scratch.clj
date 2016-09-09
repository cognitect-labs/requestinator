(do
  (refresh)
  (let [dir "/tmp/requestinator/petstore-full/results/2016-08-29T10:37:06"
        now (java.util.Date.)
        dest (str dir
                  "/reports/"
                  (format "%TFT%TT" now now))]
    (report/report {:fetch-f (ser/file-fetcher dir)
                    :record-f (ser/file-recorder dest)})
    (clojure.java.shell/sh "open" (str dest "/main/html/index.html"))))

(with-open [rw (recorder-writer (file-recorder "/tmp/requestinator-test/reports/2016-07-25T13:38:18")
                                "test.txt")]
  (.write rw "Hi there"))

(+ 1 2)

(let [dir "/tmp/requestinator/results/2016-08-08T15:21:16"
      fetch-f (ser/file-fetcher dir)]
  (->  "index.transit"
       fetch-f
       ser/decode
       rand-nth
       :path
       fetch-f
       ser/decode
       :response
       :body
       pprint))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Why is generation using up all my memory?
;;

;; Answer: because test.check was generating with a max-length of 100,
;; which is too big for complicated specs.

(as-> "http://localhost:8888/v1/petstore-full.json" ?
  (slurp ?)
  (json/read-str ?)
  (generate ? 30)
  (drop 100 ?)
  ;;(take 100 ?)
  ;;(dorun ?)
  (first ?)
  (pprint ?)
  ;;(time ?)
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; What would a causatum spec look like?


(main/generate
 {:generator-uri "file:resources/petstore-uniform.edn"
  :destination  "file:///tmp/requestinator/petstore-uniform/requests"
  :agent-count  3
  :duration-sec 60}
 nil)

(gen/read-generator "file:resources/petstore-uniform.edn"
                    (swagger/readers "file:resources/petstore-uniform.edn"))

(let [fetcher (ser/create-fetcher "file:resources/petstore-uniform.edn")]
  (->> (fetcher "")
       String.
       (clojure.edn/read-string {:readers (swagger/readers "file:resources/petstore-uniform.edn")})))


(let [reader (get (swagger/readers "file:resources/petstore-uniform.edn") 'com.cognitect.requestinator.spec)]
  (reader {:spec "http://petstore.swagger.io/v2/swagger.json"
           :amendments nil}))

(main/generate
 {:generator-uri
  "file:resources/petstore-markov.edn"
  #_"file:resources/petstore-uniform.edn"
  :destination  "file:///tmp/requestinator/petstore-markov-2/requests"
  :agent-count  3
  :duration-sec 60}
 nil)

(defn foo [x]
  (tcgen/sample-seq
   (tcgen/let [b (tcgen/return 3)
               a (if (even? x)
                   (tcgen/elements [1 2])
                   (tcgen/elements [3 4]))]
     [a b])))

(take 5 (foo 2))

(as-> (swagger/read-spec "" {:base "http://petstore.swagger.io/v2/swagger.json"}) ?
  (swagger/request-generator ? {:path "/pet/findByStatus"})
  (tcgen/sample-seq ?)
  (drop 10 ?)
  (take 10 ?)
  (pprint ?))


(->> (markov/generate
      (swagger/read-spec "" {:base "http://petstore.swagger.io/v2/swagger.json"})
      {:query-by-status {:path "/pet/findByStatus"
                         :method "get"}
       :query-by-tags   {:path "/pet/findByTags"
                         :method "get"}
       :pet-by-id       {:path   "/pet/{petId}"
                         :method "get"}}
      {:start           [{:query-by-status {:weight 1
                                            :delay '(constant 1)}
                          :query-by-tags   {:weight 1
                                            :delay '(constant 2)}}]
       :query-by-status [{:pet-by-id     {:weight 1
                                          :delay  '(erlang 10)}
                          :query-by-tags {:weight 1
                                          :delay  '(erlang 10)}}]
       :query-by-tags   [{:pet-by-id {:weight 1
                                      :delay  '(erlang 10)}}]
       :pet-by-id       [{:pet-by-id {:weight 1
                                      :delay  '(erlang 10)}}]})
     (take 10)
     (pprint))

(->> (swagger/generate
      (swagger/read-spec "" {:base "http://petstore.swagger.io/v2/swagger.json"})
      1)
     (take 10)
     pprint)

(main/generate {:destination "file:///tmp/requestinator/"
                :params-uri "resources/petstore-mixed.edn"}
               [])

(pprint (main/read-params  "resources/petstore-mixed.edn"))

(main/execute {:source "file:///tmp/requestinator/"
               :destination "file:///tmp/requestinator/results"
               :recorder-concurrency 3}
              [])

(main/report {:source "file:///tmp/requestinator/results"
              :destination "file:///tmp/requestinator/reports"}
             [])

(let [fetcher (ser/create-fetcher
               #_"file://tmp/requestinator"
               "file://tmp/requestinator/results")]
  (->> "index.transit"
      fetcher
      ser/decode
      (map :agent-id)
      distinct
      sort))
