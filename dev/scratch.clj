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
