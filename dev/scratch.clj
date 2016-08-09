(do
  (refresh)
  (let [dir "/tmp/requestinator/results/2016-08-08T15:21:16"
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
