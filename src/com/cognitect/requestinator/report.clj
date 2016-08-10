(ns com.cognitect.requestinator.report
  "A library for writing out a report that displays a requestinator
  run."
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [cljs.build.api :as cljs]
            [com.cognitect.requestinator.html :as h]
            [com.cognitect.requestinator.serialization :as ser]))

(defn detail-uri
  "Returns a relative URI (as a string) to the detail report for a
  given item."
  [{:keys [agent-id t] :as item}]
  ;; TODO: Change
  (format "detail/%04d/%010d.html"
          agent-id
          (long (* t 1000))))

(defn write-index
  "Writes the report's index.html via record-f."
  [index record-f]
  ;; Blah. The asset-path that ClojureScript requires means that I
  ;; can't load the same script in the main page as I do in the detail
  ;; pages unless they're all at the same level of the file hierarchy.
  ;; So: arbitrary nesting.
  (with-open [w (ser/recorder-writer record-f "main/html/index.html")]
    (h/html
     w
     [:html
      [:head
       [:title "Requestinator report"]
       [:link {:href "../../css/style.css"
               :rel  "stylesheet"
               :type "text/css"}]
       [:script {:src "../../js/report.js"
                 :type "text/javascript"}]]
      [:body
       (let [data (group-by :agent-id index)
             max-t (->> index
                        (map :t)
                        (reduce max))
             max-agent (->> index
                            (map :agent-id)
                            (reduce max))]
         [:svg {:id "timeline"
                :viewBox (format "0 0 %f %d"
                                 max-t
                                 (inc max-agent))
                :preserveAspectRatio "none"}
          (for [[agent-id items] data
                {:keys [t status path duration] :as item} items]
            (let [color (if (= 200 status) "green" "red")]
              [:g
               {:class "timeline-cell"
                :data-detail-uri (str "../../" (detail-uri item))}
               [:rect
                {:fill color
                 :x t
                 :y (+ agent-id 0.1)
                 :width duration
                 :height 0.8}]
               [:path {:fill "white"
                       :stroke color
                       :stroke-width 0.01
                       :d (format "M%f %f L%f %f L%f %f z"
                                  (+ t duration) (+ agent-id 0.1)
                                  (+ t duration 0.1) (+ agent-id 0.5)
                                  (+ t duration) (+ agent-id 0.9))}]]))])
       [:iframe#detail]
       [:script {:type "text/javascript"}
        "com.cognitect.requestinator.report.start();"]]])))


(defn make-temp-dir
  "Make a temporary directory and return its path"
  [^String prefix]
  (.toFile (java.nio.file.Files/createTempDirectory
            prefix
            (make-array java.nio.file.attribute.FileAttribute 0))))

(defn relative-path
  [base file]
  (let [candidate (subs (-> file io/file .getAbsolutePath)
                        (-> base io/file .getAbsolutePath .length))]
    (if (.startsWith candidate "/")
      (subs candidate 1)
      candidate)))

(defn write-js
  ([record-f js-dirname] (write-js record-f js-dirname {}))
  ([record-f js-dirname cljs-opts]
   (let [src-dir (make-temp-dir "requestinator-cljs")
         src-file (io/file src-dir
                           "com/cognitect/requestinator/report.cljs")
         dest-dir (make-temp-dir "requestinator-cljs-output")
         dir (io/file dest-dir js-dirname)]
     (.mkdirs (.getParentFile src-file))
     (.mkdirs (io/file dir))
     (->> "cljs/com/cognitect/requestinator/report.cljs"
          io/resource
          slurp
          (spit src-file))
     (log/debug "Compiling ClojureScript")
     (cljs/build src-dir
                 (merge {:main       'com.cognitect.requestinator.report
                         :output-to  (.getAbsolutePath (io/file dir "report.js"))
                         :output-dir (.getAbsolutePath (io/file dir "out"))
                         :asset-path (format "../../%s/out"  js-dirname)}
                        cljs-opts))
     (log/debug "ClojureScript build complete")
     (doseq [file (file-seq dest-dir)]
       (log/trace "writing file" :file (.getAbsolutePath file))
       (when-not (.isDirectory file)
         (->> file
              .toPath
              java.nio.file.Files/readAllBytes
              (record-f (relative-path dest-dir file))))))))

(defn write-shared-files
  [record-f]
  (write-js record-f "js" {} #_{:optimizations :whitespace})
  ;;(emit-images shared-dir)
  (doseq [resource ["css/style.css"
                    "ext/js/json-formatter/bundle.js"
                    "ext/js/json-formatter/bundle.js.map"]]
    (->> resource
         (str "assets/")
         io/resource
         slurp
         .getBytes
         (record-f resource))))

(defn request-line
  "Returns HTML data for rendering the first line of a request."
  [request]
  [:div.request-line
   [:span.method (or (some-> request :method name .toUpperCase) "<<none>>")]
   [:span.url (let [uri (-> request :url java.net.URI.)]
                (str (.getPath uri)
                     (when-let [q (.getQuery uri)]
                       (str "?" q))))]
   [:span.version "HTTP/1.1"]])

(defn response-line
  "Returns HTML data for rendering the first line of a request."
  [response]
  [:div.response-line
   [:span.version "HTTP/1.1"]
   [:span.status-code (-> response :status str)]
   [:span.reason-phrase
    ;; TODO: I don't think we capture reason phrase at the moment
    ]])

(defn headers
  "Returns HTML data for rendering the headers of a request or
  response"
  [r]
  [:div.headers
   (for [[name values] (:headers r)]
     [:div.header
      [:span.header-name name]
      [:span.colon ":"]
      [:span.header-values
       (->> values
            (interpose ",")
            (apply str))]])])

(defn iget
  "Case-insensitive get."
  [m ^String k]
  (when-let [k* (->> m
                     keys
                     (filter #(.equalsIgnoreCase k %))
                     first)]
    (get m k*)))

(defn json-content?
  "Returns true if the content-type of the request/response `r` is
  JSON."
  [r]
  (-> r :headers (iget "content-type") first (.startsWith "application/json")))

(defn json-body
  [body]
  [:script
   {:type "application/json"
    :class "json-body"}
   (h/raw body)])

(defn body
  "Returns HTML data for rendering the body of a request or
  response."
  [r]
  [:div.body
   (let [body (-> r :body)]
     (cond
       (empty? body) [:div.nobody "((Ooooohhhh, I ain't got no body...))"]
       (json-content? r) (json-body body)
       :else [:pre body]))])

(defn write-details
  "Write the individual detail reports for each of the requests."
  [index fetch-f record-f]
  (doseq [{:keys [path agent-id t] :as item} index]
    (let [{:keys [response]} (-> path fetch-f ser/decode)
          ;; We use the request data in the response, because it
          ;; reflects the request that was *actually* made, including
          ;; cookies etc., rather than the request we said we were
          ;; going to make in the generated data.
          request (:request response)]
      (with-open [w (ser/recorder-writer record-f (detail-uri item))]
        (h/html
         w
         [:html
          [:head
           [:title (format "Requestinator report - Agent %d, Time %f" agent-id t)]
           [:link {:href "../../css/style.css"
                   :rel  "stylesheet"
                   :type "text/css"}]
           [:script {:src "../../js/report.js"
                     :type "text/javascript"}]
           [:script {:src "../../ext/js/json-formatter/bundle.js"
                     :type "text/javascript"}]]
          [:body
           [:div.details
            (if-not request
              [:div.error
               (format "No request data found. Agent %d, time %f"
                       agent-id t)]
              [:div.request
               (request-line request)
               (headers request)
               (body request)])
            (if-not response
              [:div.error
               (format "No response data found. Agent %d, time %f"
                       agent-id t)]
              [:div.response
               (response-line response)
               (headers response)
               (body response)])]
           [:script {:type "application/javascript"}
            "com.cognitect.requestinator.report.render_json('json-body');"]]])))))

(defn report
  [{:keys [fetch-f record-f]}]
  (let [index (ser/decode (fetch-f "index.transit"))]
    (write-shared-files record-f)
    (write-index index record-f)
    (write-details index fetch-f record-f)))
