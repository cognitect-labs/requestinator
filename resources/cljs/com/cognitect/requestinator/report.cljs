(ns com.cognitect.requestinator.report
  (:require [goog.dom :as gdom]
            [goog.dom.classlist :as gclasses]
            [goog.events :as gevents]
            [goog.History :as history]
            [goog.string :as gstring]
            [goog.string.format]
            [goog.style :as gstyle]))

(def history (goog.History.))

(defn log
  [& messages]
  (.debug js/console (->> messages
                          (interpose " ")
                          (apply str))))

(defn get-by-id
  [id]
  (.getElementById js/document id))

(defn computed-style
  [e]
  (if-not (-> e .-currentStyle undefined?)
    (.-currentStyle e)
    (-> js/document .-defaultView (.getComputedStyle e nil))))

(defn set-display-style
  [e style]
  (set! (-> e .-style .-display) style))

(defn class-regex
  [klass]
  (re-pattern (str "(?:^|\\s)" klass "(?!\\S)")))

(defn has-class?
  [e klass]
  (gclasses/contains e klass))

(defn add-class
  [e klass]
  (gclasses/add e klass))

(defn remove-class
  [e klass]
  (gclasses/remove e klass))

(def highlight-x (delay (gdom/getElement "highlight-x")))
(def highlight-y (delay (gdom/getElement "highlight-y")))

(defn highlight
  [source]
  (let [bbox (.getBBox source)]
    (.setAttribute @highlight-x
                   "transform"
                   (gstring/format "translate(0 %f)" (.-y bbox)))
    (.setAttribute @highlight-y
                   "transform"
                   (gstring/format "translate(%f 0)" (.-x bbox)))
    (.setAttribute @highlight-y
                   "width"
                   (.-width bbox))
    (gstyle/showElement @highlight-x true)
    (gstyle/showElement @highlight-y true)))

(defn unhighlight
  [source]
  (gstyle/showElement @highlight-x false)
  (gstyle/showElement @highlight-y false))

(defn replace-detail
  [url]
  (let [existing (gdom/getElement "detail")
        replacement (gdom/createElement "iframe")]
    (.setAttribute replacement "id" "detail")
    (set! (.-src replacement) url)
    (gdom/replaceNode replacement existing)))

(defn show-detail
  [target]
  (log "show")
  (replace-detail target))

(defn hide-detail
  [target]
  (log "hide")
  (replace-detail ""))

(defn show
  [target]
  (gstyle/showElement target true))

(defn hide
  [target]
  (log "hide")
  (gstyle/showElement target false))

(defn lock-highlight
  "Changes the color of the highlight to match the lock state."
  [lock?]
  (doseq [h [@highlight-x @highlight-y]]
    (if lock?
      (add-class h "locked")
      (remove-class h "locked"))))


(defn tracker
  []
  (atom {:locked?          false
         :current-source   nil
         :currently-target nil}))

(defn track-enter
  [tracker event]
  (let [{:keys [locked? current-target current-source]} @tracker]
    (log "track-enter" :locked? locked?)
    (when-not locked?
      (log "track-enter - not locked")
      (when current-target
        (unhighlight current-source)
        (hide-detail current-target))
      (let [new-source (-> event .-target)
            new-target (-> new-source (.getAttribute "data-detail-uri"))]
        (highlight new-source)
        (show-detail new-target)
        (swap! tracker assoc
               :current-target new-target
               :current-source new-source)))))

(defn track-leave
  [tracker event]
  (let [{:keys [locked? current-source current-target]} @tracker]
    (log "track-leave" :locked? locked?)
    (when-not locked?
      (when current-target
        (unhighlight current-source)
        (hide-detail current-target))
      (swap! tracker assoc :current-target nil))))

(defn update-tracker-state
  [tracker source target]
  (let [{:keys [locked? current-target current-source]} @tracker
        lock-state (if locked? :locked :unlocked)
        target-change (if (= target current-target)
                        :same
                        :different)]
    (log "update-tracker-state"
         :lock-state lock-state
         :target-change target-change)
    (condp = [lock-state target-change]
      [:locked :different] (do
                             (when current-target (hide-detail current-target))
                             (when current-source (unhighlight current-source))
                             (show-detail target)
                             (highlight source)
                             (.setToken history (str "result/" target))
                             (lock-highlight true)
                             (swap! tracker assoc
                                    :locked? true
                                    :current-target target
                                    :current-source source))
      [:locked :same] (do
                        (unhighlight source)
                        (show-detail target)
                        (.setToken history "")
                        (lock-highlight false)
                        (swap! tracker assoc :locked? false))
      [:unlocked :different] (do
                               (when current-target (hide-detail current-target))
                               (when current-source (unhighlight current-source))
                               (highlight source)
                               (show-detail target)
                               (lock-highlight true)
                               (.setToken history (str "result/" target))
                               (swap! tracker assoc
                                      :locked? true
                                      :current-target target
                                      :current-source source))
      [:unlocked :same] (do
                          (highlight source)
                          (.setToken history (str "result/" target))
                          (lock-highlight true)
                          (swap! tracker assoc
                                 :locked? true
                                 :current-target target
                                 :current-source source)))))

(defn track-click
  [tracker event el]
  (let [source el
        target (-> source (.getAttribute "data-detail-uri"))]
    (update-tracker-state tracker source target)))

(def trackers (atom {}))

(defn add-tracking
  [klass]
  (let [tracker (tracker)]
    (doseq [e (array-seq (gdom/getElementsByClass klass))]
      (gevents/listen e "mouseenter" #(track-enter tracker %))
      (gevents/listen e "mouseleave" #(track-leave tracker %))
      (gevents/listen e "click" #(track-click tracker % e)))
    (swap! trackers assoc klass tracker)))

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
  (unhighlight nil)
  (add-tracking "timeline-cell"))
