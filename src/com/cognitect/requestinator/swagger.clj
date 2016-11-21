(ns com.cognitect.requestinator.swagger
  "Library for generating Ring request maps from Swagger specs."
  (:require [clojure.core.match :refer [match]]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [clojure.test.check :as c]
            [clojure.test.check.generators :as gen]
            [clojure.tools.logging :as log]
            [cognitect.transit :as transit]
            [com.cognitect.requestinator.engine :as engine]
            [com.cognitect.requestinator.json :as json-helper]
            [com.cognitect.requestinator.math :as math]
            [com.cognitect.requestinator.serialization :as ser]
            [com.cognitect.requestinator.request :as request]
            [com.gfredericks.test.chuck.generators :as chuck-gen])
  (:import [java.util Base64]
           [java.net URLEncoder]))

(declare param-value-generator)

(defn object-generator
  [spec param]
  (let [props (get param "properties")]
    (->> props
         vals
         (map #(param-value-generator spec %))
         (apply gen/tuple)
         (gen/fmap (fn [vals] (zipmap (keys props) vals))))))

(defn date-generator
  [format]
  (gen/let [d (gen/choose 0 10000000000000)]
    (.format (java.text.SimpleDateFormat. format)
             (java.util.Date. d))))

(defn string-generator
  [{:strs [pattern minLength maxLength]}]
  (if pattern
    (chuck-gen/string-from-regex (re-pattern pattern))
    (if (or minLength maxLength)
      (gen/vector gen/char (or minLength 0) (or maxLength 30))
      gen/string)))

(defn param-value-generator
  [spec param]
  (let [{:strs [$ref type format items in schema enum maximum minimum]} param]
    (cond
      $ref
      (param-value-generator spec (json-helper/select spec $ref))

      enum
      (gen/elements enum)

      (= in "body")
      (param-value-generator spec schema)

      :else
      (match [type       format    ]
             ["integer"  "int32"   ] (gen/choose
                                      (int (or minimum Integer/MIN_VALUE))
                                      (int (or maximum Integer/MAX_VALUE)))
             ["integer"  _         ] (gen/choose
                                      (long (or minimum Long/MIN_VALUE))
                                      (long (or maximum Long/MAX_VALUE)))
             ["number"  "float"    ] (gen/double* {:min       (or minimum Float/MIN_VALUE)
                                                   :max       (or maximum Float/MAX_VALUE)
                                                   :infinite? false
                                                   :NaN?      false})
             ["number"  _          ] (gen/double* {:min       (or minimum Double/MIN_VALUE)
                                                   :max       (or maximum Double/MAX_VALUE)
                                                   :infinite? false
                                                   :NaN?      false})
             ["string"  "byte"     ] (gen/fmap #(.encodeToString (Base64/getEncoder) %)
                                               gen/bytes)
             ["string"  "date"     ] (date-generator "YYYY-MM-dd")
             ["string"  "date-time"] (date-generator "YYYY-MM-dd'T'HH:mm:SSZ")
             ["string"  _          ] (string-generator param)
             ["boolean" _          ] gen/boolean
             ["array"   _          ] (gen/vector (param-value-generator spec items))
             ["object"  _          ] (object-generator spec param)
             ["file"    _          ] gen/string
             :else (throw (ex-info (clojure.core/format "Parameter generation for %s/%s not yet implmented. Parameter definition: %s"
                                                        type
                                                        format
                                                        (pr-str param))
                                   {:reason :not-yet-implemented
                                    ;;:definitions definitions
                                    :param param
                                    :type type
                                    :format format}))))))

(defn param-generator
  [spec param]
  (gen/let [v (param-value-generator spec param)]
    (assoc param
           :value v)))

(defn params-generator
  [spec params]
  (apply gen/tuple (map #(param-generator spec %) params)))

(defn substitute-path-params
  [uri path-params]
  (->> path-params
       (reduce (fn [s {:strs [name] :keys [value]}]
                 (str/replace s (str "{" name "}") (URLEncoder/encode (str value)
                                                                      "UTF-8")))
               uri)))

(defn format-array
  [format name values]
  (case format
    "multi"
    (->> values
         (map (fn [v] (str name "=" v)))
         (str/join "&"))

    "csv"
    (str/join "," values)

    "ssv"
    (str/join " " values)

    "tsv"
    (str/join "\t" values)

    "pipes"
    (str/join "|" values)))

(defn build-query-string
  [query-params]
  (->> query-params
       (map (fn [{:strs [name collectionFormat] :keys [value]}]
              (if collectionFormat
                (format-array collectionFormat name value)
                (str (URLEncoder/encode name "UTF-8")
                     "="
                     (URLEncoder/encode value "UTF-8")))))
       (str/join "&")))

(defn build-headers
  [header-params]
  (->> header-params
       (map (fn [{:strs [name] :keys [value]}]
              [(str/lower-case name) (str value)]))
       (into {})))

(def multipart-boundary (str (java.util.UUID/randomUUID)))

(defn format-form-data
  [mime-type form-data]
  (case mime-type
    "application/x-www-form-urlencoded"
    (->> form-data
         (map (fn [{:strs [name] :keys [value]}]
                (str (URLEncoder/encode name "UTF-8")
                     "="
                     (URLEncoder/encode value "UTF-8"))))
         (str/join "&"))

    "multipart/form-data"
    (str (->> form-data
              (map (fn [{:strs [name] :keys [value]}]
                     (format "--%s\nContent-Disposition: form-data; name=\"%s\"\n\n%s\n"
                             multipart-boundary
                             name
                             value)))
              (str/join "\n"))
         "--"
         multipart-boundary
         "--")))

(defn evaluate-params
  [params context]
  (map (fn [param]
         (update param :value #(engine/evaluate % context)))
       params))

(defn request
  [{:keys [host scheme base-path op method params mime-type]} context]
  (let [params (evaluate-params params context)
        _ (log/debug "request" :params params)
        {:strs [query header path formData body]} (group-by #(get % "in") params)]
    {:url          (format "%s://%s%s%s"
                           scheme
                           host
                           base-path
                           (substitute-path-params op path))
     :query-string (build-query-string query)
     :method       (keyword method)
     ::params      params
     :headers      (merge {"content-type" (cond

                                            (and formData
                                                 (= mime-type "multipart/form-data"))
                                            (str "multipart/form-data; boundary="
                                                 multipart-boundary)

                                            mime-type
                                            mime-type

                                            :else
                                            "application/json")}
                          (build-headers header))
     :body         (cond
                     body (->> body
                               first
                               :value
                               json/write-str)
                     formData (format-form-data mime-type formData))}))

(defrecord Template [host scheme base-path op method mime-type params]
  request/Template
  (-fill-in [this context]
    (request this context)))

(defn override-param-values
  "Updates `base`, a sequence of parameter maps, with values
  overridden per `overrides`."
  [base overrides]
  (log/debug "override-params" :base base :overrides overrides)
  (mapv (fn [param]
          (let [n (get param "name")]
            (if-let [o (get overrides n)]
              (assoc param :value o)
              param)))
        base))

(defn request-generator
  "Given a Swagger spec, return a generator that will create a random
  request map against one of the operations in it."
  ([spec] (request-generator spec {}))
  ([spec params]
   (let [{:keys [path method param-overrides]} params
         {:strs [host basePath schemes definitions paths]} spec]
     (gen/let [[op op-description]         (if path
                                             (gen/return [path (get paths path)])
                                             (gen/elements paths))
               [method method-description] (if method
                                             (gen/return [method (get op-description method)])
                                             (gen/elements op-description))
               mime-type                   (gen/elements (get method-description "consumes" [nil]))
               scheme                      (gen/elements schemes)
               params                      (params-generator spec
                                                             (get method-description "parameters"))]
       (map->Template
        {:host      host
         :scheme    scheme
         :base-path basePath
         :op        op
         :method    method
         :mime-type mime-type
         :params    (override-param-values params param-overrides)})))))

(defn generate
  "Given a Swagger spec, return a lazy sequence of Ring request maps
  representing valid requests described by it."
  ;; Using a sufficiently large max-size on a complex spec uses O(MG)
  ;; memory - and the default in test.check is 100. For the small
  ;; number of Swagger specs I tried, 30 seems to do a decent job of
  ;; generating "interesting" requests often enough. But we probably
  ;; need to expose `max-size` from the command line eventually.
  ([spec params] (generate spec params 30))
  ([spec params max-size]
   (gen/sample-seq (request-generator spec params) max-size)))

(defrecord Generator [spec]
  request/Generator
  (-generate [this params] (generate spec params)))

;; TODO: Update this code so that the spec and amendments can be
;; embedded literally in the spec file rather than us assuming they're
;; URLs.
(defn read-spec
  [base-uri {:keys [base amendments]}]
  (log/debug "read-spec" :base-uri base-uri :base base :amendments amendments)
  (let [spec       (->> base
                        (ser/resolve-relative base-uri)
                        slurp
                        json/read-str)
        amendments (some->> amendments
                            (ser/resolve-relative base-uri)
                            slurp
                            json/read-str)]
    (->Generator (json-helper/amend spec amendments))))

(ser/register-handlers!
 {:transit {:read  {(.getName Template) (transit/record-read-handler Template)}
            :write {Template (transit/record-write-handler Template)}}
  ;; TODO: support relative file resolution. We used to pass through
  ;; the base-uri parameters, but with the switch to consolidate
  ;; handlers in the serialization namespace, that ability was lost.
  ;; Still need to add it back in.
  :edn     {:read {'requestinator.spec/swagger #(read-spec nil %)}}})
