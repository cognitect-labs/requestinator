;; Copyright (c) 2016 Cognitect, Inc.
;;
;; This file is part of Requestinator.
;;
;; All rights reserved. This program and the accompanying materials
;; are made available under the terms of the Eclipse Public License v1.0
;; which accompanies this distribution, and is available at
;; http://www.eclipse.org/legal/epl-v10.html
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

;; Danger, Will Robinson!!!
(->> "/tmp/requestinator"
     io/file
     file-seq
     (remove #(#{"." ".."} (.getName %)))
     (sort-by #(-> % .getPath count))
     reverse
     (map #(.delete %))
     dorun)

(main/generate {:destination "file:///tmp/requestinator/"
                :params-uri "resources/petstore-mixed.edn"}
               [])

(let [fetcher (ser/create-fetcher
               "file://tmp/requestinator")]
  (->> "index.transit"
       fetcher
       ser/decode
       pprint))

(pprint (main/read-params  "resources/petstore-mixed.edn"))

(let [t "11:12"
      mf (future
           (main/execute {:source "file:///tmp/requestinator/"
                          :destination "file:///tmp/requestinator/results/markov"
                          :recorder-concurrency 3
                          :start (main/parse-time t)
                          :groups #{"markov"}}
                         []))
      uf  (future
            (main/execute {:source "file:///tmp/requestinator/"
                           :destination "file:///tmp/requestinator/results/uniform"
                           :recorder-concurrency 3
                           :start (main/parse-time t)
                           :groups #{"uniform"}}
                          []))]
  {:markov  @mf
   :uniform @uf})

(let [fetcher (ser/create-fetcher
               "file://tmp/requestinator/results")]
  (->> "index.transit"
       fetcher
       ser/decode
       ;; (group-by (juxt :agent-group :agent-num))
       pprint))

(main/report {:sources #{"file:///tmp/requestinator/results/markov"
                         "file:///tmp/requestinator/results/uniform"}
              :destination "file:///tmp/requestinator/reports"}
             [])

(clojure.java.shell/sh "open" "/tmp/requestinator/reports/main/html/index.html")

(let [fetcher (ser/create-fetcher
               "file://tmp/requestinator"
               #_"file://tmp/requestinator/results")]
  (->> #_"index.transit"
       "markov-0000/0000003947.transit"
       fetcher
       ser/decode
       #_(map :agent-id)
       #_distinct
       #_sort
       #_:store
       #_first
       #_val
       #_seq?
       :com.cognitect.requestinator.scheduler/request
       pprint))


(report/write-js (ser/create-recorder "file:///tmp/requestinator-test/")
                 "js")

(clojure.java.shell/sh "open" "/tmp/requestinator/reports/main/html/index.html")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def s (graphql/read-spec {:url "https://graphql-swapi.parseapp.com/?"}))


(->> 'com.cognitect.requestinator.main
     find-ns
     #_ns-aliases
     ns-refers
     vals
     (map #(.-ns %))
     (into #{}))


(swagger/map->AbstractRequest )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(as-> (swagger/read-spec "" {:base "http://petstore.swagger.io/v2/swagger.json"}) ?
  (:spec ?)
  (swagger/request-generator ? {:path "/pet/findByStatus"
                                :param-overrides {"status" ["available"]}})
  (tcgen/sample-seq ?)
  (drop 10 ?)
  (take 10 ?)
  (pprint ?))

(get-in (swagger/read-spec "" {:base "http://petstore.swagger.io/v2/swagger.json"})
        ["paths" "/pet/findByStatus"])

(pprint (swagger/read-spec "" {:base "http://petstore.swagger.io/v2/swagger.json"}))

(ser/)

(def v (let [dir "/tmp/requestinator/markov-0000/"
             fetch-f (ser/file-fetcher dir)]
         (->  "0000001000.transit"
              fetch-f
              ser/decode)))

(let [fetcher (ser/create-fetcher
               "file://tmp/requestinator")]
  (-> "markov-0000/0000002000.transit"
      fetcher
      (ser/decode {:handlers (->> main/spec-types
                                  (map ser/transit-read-handlers)
                                  (reduce merge))})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol Evaluates
  (-eval [this context]))

(extend-protocol Evaluates
  Object
  (-eval [this context] this))

(defrecord Recall [nm default]
  (-eval [this context] (get context nm default)))

(defn -fill-in [template context]
  (let [params (map #(-eval % context) (:params template))])
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

comment
{::http/method :post
 ::http/protocol :http
 ::http/host "www.petstore.io"
 ::http/path "/pet"
 ::http/headers [{::http.header/name "foo"
                  ::http.header/value "bar"}]
 ;; TODO: Should this be broken out or just part of the headers?
 ::http/content-type "application/json"
 ;; TODO: What data types should we permit here? InputStream?
 ;; String? The set of things that Pedestal allows?
 ::http/body "this is the body"}
#_{:method :post
   :url "http://whatever/pet"
   :headers ...
   :content-type ...
   :form-params ...
   :body ...
   :insecure? ...}

(s/def :ApiResponse/code int32?)
(s/def :ApiResponse/message string?)
(s/def :ApiResponse/type string?)

(defn json-type
  "Returns a spec..."
  [{:keys [req #_opt]}]
  (s/and map? ))

(defn has-required-keys?
  [m req]
  (let [req-set (set req)]
    (-> m keys set (set/intersection req-set) (= req-set))))

(defn entry-valid?
  [k v-spec]
  (fn [m]
    (if (contains? m k)
      (s/valid? v-spec (get m k))
      true)))

(defn keys-conformance
  [key-specs]
  (->> key-specs
       (map (fn [[k v-spec]] (entry-valid? k v-spec)))
       (reduce (fn [s1 s2] (s/and s1 s2)))))

(def kc (keys-conformance {"foo" int?
                           "bar" string?}))

(s/form kc)

(s/valid? kc {"foo" :broken})
(s/explain kc {"foo" :broken})

(s/valid? kc {"foo" 3})

(def x (s/with-gen
         (s/and #(has-required-keys? % #{"foo" "bar"})
                (keys-conformance {"foo" int?
                                   "bar" string?
                                   "quux" ratio?
                                   "baaz" ratio?}))
         (fn []
           (gen/let [foo-val gen/int
                     bar-val gen/string
                     opt-keys (gen/set (gen/elements #{"quux" "baaz"}))]
             (merge {"foo" foo-val
                     "bar" bar-val}
                    (zipmap opt-keys (repeat 11/3)))))))

(s/form x)

(s/valid? x {"foo" 1 "bar" "foo"})

(s/valid? x {"foo" 1})
(s/explain x {"foo" 1})

(s/valid? x {"foo" 1 "bar" "foo" "quux" 11/3})

(s/valid? x {"foo" 1 "bar" "foo" "quux" 11/3 "blarg" 12/3})
(s/explain x {"foo" 1 "bar" "foo" "quux" 11/3 "blarg" 12/3})

(sgen/sample (s/gen x))

{"ApiResponse" (json-type {:req {"code" int32?
                                 "message" string?
                                 "type" string?}})}

(def ApiResponse (s/keys :req-un [:ApiResponse/code
                                  :ApiResponse/message
                                  :ApiResponse/type]))

(def definitions
  {"ApiResponse" (s/keys :req-un )})

(defn dispatch-mspec
  [val]
  (println "dispatch-mspec" val)
  :test)

(defmulti mspec dispatch-mspec)

(defmethod mspec :test
  [_]
  int?)

(def x (s/multi-spec mspec println))

(s/valid? x 3)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; What do spec forms look like for JSON data?

(defn has-key?
  [k]
  (fn [m] (contains? m k)))

(defn valid-value?
  [k v-spec]
  (fn [m]
    (if (contains? m k)
      (s/valid? v-spec (get m k))
      true)))

(def x (s/and map?
              (has-key? "foo")
              (valid-value? "foo" int?)
              (has-key? "bar")
              (valid-value? "bar" string?)
              (valid-value? "quux" ratio?)))

(defn map-spec-form
  [keyspecs]
  (->> keyspecs
       (mapcat (fn [[k v-spec]]
                 (list `(has-key? ~k)
                       `(valid-value? ~k ~(eval v-spec)))))
       (concat `(s/and map?))))

(defmacro map-spec
  [keyspecs]
  (-> keyspecs map-spec-form eval))

(def y (map-spec {"foo" int?
                  "bar" string?}))

y
(pprint y)
(class y)

(s/valid? y {})
(s/explain y {})
(pprint (s/form y))

(pprint (s/form x))

(s/valid? x {"foo" :broken})
(s/explain x {"foo" :broken})

(s/valid? x {"foo" 3})
(s/explain x {"foo" 3})

(s/valid? x {"foo" 3
             "bar" "b"
             "quux" 3})
(s/explain x {"foo" 3
              "bar" "b"
              "quux" 3})

(sgen/sample (s/gen x))

(def x' (s/with-gen x
          #(clojure.test.check.generators/return {"foo" 3 "bar" "b"})))

(s/valid? x' {"foo" 3 "bar" "b" "quux" 3})
(s/explain x' {"foo" 3 "bar" "b" "quux" 3})
(s/explain-data x' {"foo" 3 "bar" "b" "quux" 3})


(sgen/sample (s/gen x'))

(s/explain-data
 (s/keys :req-un [:user/foo :user/bar] :opt-un [:user/quux])
 {"foo" 3
  "bar" "b"
  "quux" 3})

(def address (map-spec {"street" `string?
                        "number" `integer?
                        "zip" `integer?}))

(let [keyspecs {"first-name" `string?
                "address" `address}]
  (-> keyspecs map-spec-form pprint))

(def person (map-spec {"first-name" `string?
                       "address" `address}))

(pprint (s/form person))
(s/valid? person {"first-name" "Craig"})
(s/explain person {"first-name" "Craig"})

(s/valid? person {"first-name" "Craig"
                  "address" nil})
(s/explain person {"first-name" "Craig"
                  "address" nil})

(s/valid? person {"first-name" "Craig"
                  "address" nil})
(s/explain person {"first-name" "Craig"
                   "address" {"street" "Kilmarnock"
                              "number" 9216}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Conformer experiments

(defn dash?
  [^String c]
  (clojure.string/starts-with? c "-"))

(defn double-dash?
  [^String c]
  (clojure.string/starts-with? c "--"))

(defn flag? [c]
  (s/conformer
   #(if (and (string? %)
             (dash? %)
             (not (double-dash? %))
             (some #{c} (rest %)))
      c
      ::s/invalid)
   (fn [_] (str "-" c))))

(s/valid? (flag? \a) "-a")
(s/explain (flag? \a) "-b")
(s/conform (flag? \a) "-a")
(s/unform (flag? \a) \a)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; What if we view maps as seqs?

(defn required-keys
  [ks]
  (let [key-set (set ks)]
    (fn [m]
      (-> m keys set (clojure.set/intersection key-set) (= key-set)))))

(def names
  (s/and (required-keys ["first-name" "last-name"])
         (s/* (s/alt :first-name (s/spec (s/cat :key #{"first-name"} :val string?))
                     :last-name (s/spec (s/cat :key #{"last-name"} :val string?))
                     :else (s/spec (s/cat :key (complement #{"first-name" "last-name"})
                                          :val (constantly true)))))))

(s/valid? names {"first-name" "Craig"})
(s/explain names {"first-name" "Craig"})

(s/valid? names {"first-name" "Craig"
                 "last-name" "Andera"})
(s/explain names {"first-name" "Craig"
                  "last-name" "Andera"})

(s/valid? names {"first-name" "Craig"
                 "last-name" "Andera"
                 "personality" "sarcastic"})
(s/explain names {"first-name" "Craig"
                 "last-name" "Andera"
                  "personality" "sarcastic"})

(def address
  (s/and (required-keys ["street" "zip"])
         (s/* (s/alt :street (s/spec (s/cat :key #{"street"} :val string?))
                     :zip (s/spec (s/cat :key #{"zip"} :val integer?))
                     :else (s/spec (s/cat :key (complement #{"street" "zip"})
                                          :val (constantly true)))))))

(def person
  (s/and (required-keys ["first-name" "address"])
         (s/* (s/alt :first-name (s/spec (s/cat :key #{"first-name"} :val string?))
                     :address (s/spec (s/cat :key #{"address"} :val address))
                     :else (s/spec (s/cat :key (complement #{"first-name" "address"})
                                          :val (constantly true)))))))

(s/valid? person {"first-name" "Craig"
                  "address" {"street" "Kilmarnock"}})
(s/explain person {"first-name" "Craig"
                   "address" {"street" "Kilmarnock"}})

(s/valid? person {"first-name" "Craig"
                  "address" {"street" "Kilmarnock"
                             "zip" 22031}})
(s/explain person {"first-name" "Craig"
                  "address" {"street" "Kilmarnock"
                             "zip" 22031}})
