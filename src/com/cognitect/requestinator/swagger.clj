(ns com.cognitect.requestinator.swagger
  "Library for generating Ring request maps from Swagger specs."
  (:require [clojure.core.match :refer [match]]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [clojure.test.check :as c]
            [clojure.test.check.generators :as gen]
            [com.cognitect.requestinator.json :as json-helper])
  (:import [java.util Base64]))

;; TODO:
;; - [ ] Support for `required` being false
;; - [ ] Support for MIME types
;; - [ ] Support for XML
;; - [ ] Support for file datatype
;; - [ ] References for parameters
;; - [ ] Overriding consumes

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
             ["string"  _          ] gen/string
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
  (let [{:strs [in name]} param]
    (gen/let [v (param-value-generator spec param)]
      (assoc param
             :value v))))

(defn params-generator
  [spec params]
  (apply gen/tuple (map #(param-generator spec %) params)))

(defn substitute-path-params
  [uri path-params]
  (->> path-params
       (reduce (fn [s {:strs [name] :keys [value]}]
                 (str/replace s (str "{" name "}") (str value)))
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
                (str name "=" value))))
       (str/join "&")))

(defn build-headers
  [header-params]
  (->> header-params
       (map (fn [{:strs [name] :keys [value]}]
              [(str/lower-case name) (str value)]))
       (into {})))

(def multipart-boundary (str "--------" (java.util.UUID/randomUUID) "--------"))

(defn format-form-data
  [mime-type form-data]
  (case mime-type
    "application/x-www-form-urlencoded"
    (->> form-data
         (map (fn [{:strs [name] :keys [value]}]
                (str name "=" value)))
         (str/join "&"))

    "multipart/form-data"
    (->> form-data
         (map (fn [{:strs [name] :keys [value]}]
                (format "%s\nContent-Disposition: form-data; name=\"%s\"\n%s\n"
                        multipart-boundary
                        name
                        value)))
         (str/join "\n"))))

(defn request
  [{:keys [op method params mime-type]}]
  (let [{:strs [query header path formData body]} (group-by #(get % "in") params)]
    {:uri            (substitute-path-params op path)
     :query-string   (build-query-string query)
     :request-method (keyword method)
     ::params         params
     :headers        (merge {"content-type" (if (and formData
                                                     (= mime-type "multipart/form-data"))
                                              (str "multipart/form-data; boundary="
                                                   multipart-boundary)
                                              mime-type)}
                            (build-headers header))
     :body           (cond
                       body (->> body
                                 first
                                 :value
                                 json/write-str)
                       formData (format-form-data mime-type formData))}))

(defn request-generator
  "Given a Swagger spec, return a generator that will create a random
  Ring request map against one of the operations in it."
  [spec]
  (let [{:strs [definitions paths]} spec]
    (gen/let [[op op-description]         (gen/elements paths)
              [method method-description] (gen/elements op-description)
              mime-type                   (gen/elements (get method-description "consumes" [nil]))
              params                      (params-generator spec
                                                            (get method-description "parameters"))]
      (request {:op        op
                :method    method
                :mime-type mime-type
                :params    params}))))

(defn generate
  "Given a Swagger spec, return a lazy sequence of Ring request maps
  representing valid requests described by it."
  [spec]
  (gen/sample-seq (request-generator spec)))
