;; Copyright (c) 2016 Cognitect, Inc.
;;
;; This file is part of Requestinator.
;;
;; All rights reserved. This program and the accompanying materials
;; are made available under the terms of the Eclipse Public License v1.0
;; which accompanies this distribution, and is available at
;; http://www.eclipse.org/legal/epl-v10.html
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
  [{:keys [agent-group agent-num t] :as item}]
  ;; TODO: Change
  (format "detail/%s-%04d/%010d.html"
          agent-group
          agent-num
          (long (* t 1000))))

(defn time-lod
  "Returns the level-of-detail for the specified time."
  [t]
  (->> [900 600 300 60 30 15 5 1]
       (filter #(zero? (mod t %)))
       first))

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
               :type "text/css"
               :id "stylesheet"}]
       [:script {:src "../../js/report.js"
                 :type "text/javascript"}]]
      [:body
       [:div
        [:span.help-icon
         {:onmouseenter "goog.style.showElement(goog.dom.getElement('instructions'), true);"
          :onmouseleave "goog.style.showElement(goog.dom.getElement('instructions'), false);"}
         "?"]
        [:span#instructions
         {:style "display: none;"}
         "Hover over a request in the timeline to display details of the reqeust. Click it to lock. Click again to unlock it, or click another request to lock a new one. Hold down the shift key and scroll with the mouse to zoom the time dimension in and out."]]
       (let [data        (group-by (juxt :agent-group :agent-num) index)
             lines       (->> data keys sort)
             line-number (zipmap lines (range))
             max-t       (->> index
                              (map :actual-t)
                              (reduce max)
                              (+ 0.25))
             line-count  (count lines)]
         [:div#timeline-container
          [:svg {:xmlns "http://www.w3.org/2000/svg"
                 "xmlns:svg" "http://www.w3.org/2000/svg"
                 :id "timeline"
                 ;; :agent-numbers agent-numbers
                 :line-count line-count
                 :max-t max-t
                 ;; TODO: This is sort of an arbitrary heuristic. Can we fix it?
                 :height (format "%dpx" (* line-count 10))
                 :preserveAspectRatio "none"}
           [:g#timeline-content
            ;; Row highlights
            (for [line lines
                  :let [items (get data line)
                        line-num (line-number line)]]
              [[:rect {:class (str "timeline-outline "
                                   (if (odd? line-num) "odd" "even"))
                       :x 0
                       :y line-num
                       :width max-t
                       :height 0.99}]])
            (for [x (range 0 max-t)]
              [[:line
                {:class (str "time-line time" (time-lod x))
                 :x1 x
                 :x2 x
                 :y1 0
                 :y2 line-count}]])
            [:g.time-labels
             (let [x-scale 75
                   y-scale 10]
               (for [x (range 0 max-t)]
                 [:g {:transform (format "translate(%d, %d)"
                                         x
                                         line-count)}
                  [:g.scale-compensation
                   [:text
                    {:class (str "time-label time" (time-lod x))
                     :transform (format "translate(0, %d)"
                                        y-scale)
                     :x 0.2
                     :y 0}
                    (format "%d:%02d" (long (/ x 60)) (mod x 60))]]]))]
            ;; Highlighting of selected item
            [:rect#highlight-x {:x 0 :y 0 :width max-t :height 1}]
            [:rect#highlight-y {:x 0 :y 0 :width 0 :height line-count}]
            ;; The actual data points
            (for [line lines
                  :let [items (get data line)
                        line-num (line-number line)]
                  {:keys [actual-t t status path duration] :as item} (sort-by (comp - :actual-t) items)]
              (let [status-class (format "status%dxx" (-> status (/ 100) long))]
                [:g
                 {:class "timeline-cell"
                  :data-detail-uri (str "../../" (detail-uri item))}
                 [:rect
                  {:class (str "timeline-rect actual " status-class)
                   :x actual-t
                   :y (+ line-num 0.1)
                   :width duration
                   :height 0.8}]
                 #_[:g {:class (str "timeline-rect scheduled " status-class)
                        :transform (format "translate(0, %f)"
                                           (float agent-num))}
                    [:path {:d (format "M%f 0.02 L%f 0.4 L%f 0.6 L%f 0.98"
                                       (float t)
                                       (float actual-t)
                                       (float actual-t)
                                       (float t))}]]
                 [:rect
                  {:class (str "timeline-rect scheduled " status-class)
                   :x t
                   :y (+ line-num 0.95)
                   :width (- actual-t t)
                   :height 0.04}]
                 (let [width (min (- actual-t t) 0.0025)]
                   [:rect
                    {:class (str "timeline-rect scheduled " status-class)
                     :x (- actual-t width)
                     :y (+ line-num 0.92)
                     :width width
                     :height 0.04}])
                 [:path {:class (str "timeline-handle " status-class)
                         :d (format "M%f %f L%f %f L%f %f z"
                                    (float (+ actual-t duration)) (+ line-num 0.1)
                                    (float (+ actual-t duration 0.1)) (+ line-num 0.5)
                                    (float (+ actual-t duration)) (+ line-num 0.9))}]]))]]])
       [:div#detail-container
        [:div#detail-loading
         {:style "display:none"}
         ""]
        [:iframe#detail]]
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
                    "ext/js/json-formatter/bundle.js.map"
                    "images/curl.svg"
                    "images/clipboard.svg"
                    "images/spinner.gif"]]
    (with-open [in (.getResourceAsStream ClassLoader (str "/assets/" resource))]
      (let [baos (java.io.ByteArrayOutputStream.)
            size 1024
            buf (byte-array size)]
        (loop []
          (let [read (.read in buf 0 size)]
            (when (not (neg? read))
              (.write baos buf 0 read)
              (recur))))
        (.flush baos)
        (record-f resource (.toByteArray baos))))))

(defn request-line
  "Returns HTML data for rendering the first line of a request."
  [request]
  [:div.request-line
   [:span.method (or (some-> request :method name .toUpperCase) "<<none>>")]
   [:span.url (let [uri (-> request :url java.net.URI.)]
                (str (.getPath uri)
                     (let [q (:query-string request)]
                       (when-not (empty? q)
                         (str "?" q)))))]
   [:span.version "HTTP/1.1"]
   [:button.curl-button.lightbox-trigger
    {:data-lightbox-id "curl-lightbox"
     :title "Display curl command for this request"}]])

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
       (empty? body) [:div.nobody "Body was empty"]
       (json-content? r) (json-body body)
       :else [:pre body]))])

(defn curl-command
  "Returns an invocation of 'curl' that will replicate 'request'."
  [request]
  (let [cmd (->> [ ;; Base command
                  "curl --include"
                  ;; Method
                  (when-let [method (some-> request :method name .toUpperCase)]
                    (str "--request " method))
                  ;; Headers
                  (->> request
                       :headers
                       (remove (comp #{"Content-Length" "Host" "Connection"} key))
                       (mapv (fn [[name values]]
                               (format "--header \"%s: %s\""
                                       name
                                       (->> values (interpose ",") (apply str))))))
                  (when (:body request)
                    "--data @-")
                  ;; URL
                  (:url request)]
                 (remove nil?)
                 (mapv (fn [item]
                         (if (string? item)
                           [item]
                           item)))
                 (reduce into [])
                 (interpose " \\\n  ")
                 (apply str))]
    (if-let [body (:body request)]
      (str cmd " <<EOF\n" body "\nEOF")
      cmd)))

(defn write-details
  "Write the individual detail reports for each of the requests."
  [index record-f]
  (doseq [{:keys [path agent-num agent-group t fetcher] :as item} index]
    (let [{:keys [response]} (-> path fetcher ser/decode)
          agent-id (format "%s/%04d" agent-group agent-num)
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
           [:title (format "Requestinator report - Agent %s, Time %f" agent-id (float t))]
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
               (format "No request data found. Agent %s, time %f"
                       agent-id (float t))]
              [:div.request
               (request-line request)
               (headers request)
               (body request)])
            (if-not response
              [:div.error
               (format "No response data found. Agent %s, time %f"
                       agent-id (float t))]
              [:div.response
               (response-line response)
               (headers response)
               (body response)])]
           [:div#curl-lightbox.lightbox
            [:div#curl-command.lightbox-content
             [:div.button-bar
              [:a#curl-copy-trigger.copy-trigger
               {:title "Copy command to clipboard"
                :data-copy-target "curl-command-text"
                :data-copy-tip "curl-copy-tip"}]
              [:span#curl-copy-tip.copy-tip "Command was copied to the clipboard"]]
             [:pre#curl-command-text (curl-command request)]]]
           [:script {:type "application/javascript"}
            "com.cognitect.requestinator.report.start_detail();
com.cognitect.requestinator.report.render_json('json-body');"]]])))))

(defn report
  [{:keys [fetchers recorder]}]
  (let [index (->> fetchers
                   (mapcat (fn [fetcher]
                             (->> (fetcher "index.transit")
                                  ser/decode
                                  (map #(assoc % :fetcher fetcher)))))) ]
    (write-shared-files recorder)
    (write-index index recorder)
    (write-details index recorder)))
