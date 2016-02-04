(ns com.cognitect.requestinator.swagger
  "Library for generating Ring request maps from Swagger specs."
  (:require [clojure.core.match :refer [match]]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [clojure.test.check :as c]
            [clojure.test.check.generators :as gen]))

;; TODO:
;; - [ ] Support for `required` being false
;; - [ ] Support for MIME types
;; - [ ] Support for XML
;; - [ ] Support for file datatype
;; - [ ] References for parameters
;; - [ ] Overriding consumes

(defn get-definition
  [definitions ^String path]
  (let [leader "#/definitions/"]
    (when-not (.startsWith path leader)
      (throw (ex-info "Definition paths not starting with #/defintiions not currently supported."
                      {:reason :not-yet-implemented
                       :path path})))
    (let [selector (subs path (count leader))]
      (get definitions selector))))

(declare param-value-generator)

(defn object-generator
  [definitions param]
  (let [props (get param "properties")]
    (->> props
         vals
         (map #(param-value-generator definitions %))
         (apply gen/tuple)
         (gen/fmap (fn [vals] (zipmap (keys props) vals))))))

(defn param-value-generator
  [definitions param]
  (let [{:strs [$ref type format items in schema enum]} param]
    (cond
      $ref
      (param-value-generator definitions (get-definition definitions $ref))

      enum
      (gen/elements enum)

      (= in "body")
      (param-value-generator definitions schema)

      :else
      (match [type format]
             ["integer" _] gen/int
             ["array" _]   (gen/vector (param-value-generator definitions items))
             ["string" _]  gen/string
             ["object" _]  (object-generator definitions param)
             ["boolean" _] gen/boolean
             ["file" _]    gen/string
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
  [definitions param]
  (let [{:strs [in name]} param]
    (gen/let [v (param-value-generator definitions param)]
      (assoc param
             :value v))))

(defn params-generator
  [definitions params]
  (apply gen/tuple (map #(param-generator definitions %) params)))

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
    "application/x-www-form-encoded"
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
     :params         params
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
              mime-type                   (gen/elements (get method-description "consumes"))
              params                      (params-generator definitions
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
