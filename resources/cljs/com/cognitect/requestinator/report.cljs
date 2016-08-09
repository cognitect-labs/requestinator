(ns com.cognitect.requestinator.report
  (:require [goog.dom :as gdom]
            [goog.events :as gevents]))

(defn replace-detail
  [url]
  (let [existing (gdom/getElement "detail")
        replacement (gdom/createElement "iframe")]
    (.setAttribute replacement "id" "detail")
    (set! (.-src replacement) url)
    (gdom/replaceNode replacement existing)))

(defn enter-timeline-cell
  [evt]
  (-> evt
      .-target
      (.getAttribute "data-detail-uri")
      replace-detail))

(defn ^:export render-json
  "Transmutes all elements with the given class into rendered JSON."
  [klass]
  (doseq [e (array-seq (gdom/getElementsByClass klass))]
    (let [json (-> e
                   .-innerText
                   js/JSON.parse
                   (js/JSONFormatter. 0 #js {"hoverPreviewEnabled" true})
                   .render)]
      (gdom/replaceNode json e))))

(defn ^:export start
  []
  (doseq [cell (array-seq (gdom/getElementsByClass "timeline-cell"))]
    (gevents/listen cell
                    "mouseenter"
                    enter-timeline-cell)))

