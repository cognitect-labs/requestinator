(ns com.cognitect.requestinator.report
  (:require [cljs.core.async :as async
             :refer [<! >! timeout]]
            [goog.dom :as gdom]
            [goog.dom.classlist :as gclasses]
            [goog.events :as gevents]
            [goog.events.MouseWheelHandler]
            [goog.fx.dom :as fx]
            [goog.fx.dom.Fade]
            [goog.History :as history]
            [goog.string :as gstring]
            [goog.string.format]
            [goog.style :as gstyle])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]))

(def svg-ns "http://www.w3.org/2000/svg")

(def history (goog.History.))

(defn log
  [& messages]
  (.debug js/console (->> messages
                          (interpose " ")
                          (apply str))))

#_(defn log
  [& messages])

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

(def t-scroll-factor 1.001)
(def t-scale-default 15)
(def t-scroll (atom 0))
(def t-scroll-max 1000)

(defn set-timeline-view
  [t-scale]
  (let [timeline (gdom/getElement "timeline")
        timeline-content (gdom/getElement "timeline-content")
        max-agent (-> timeline (.getAttribute "max-agent") js/Number.)
        max-t (-> timeline (.getAttribute "max-t") js/Number.)]
    (.setAttributeNS
     timeline-content
     nil
     "transform"
     (gstring/format "scale(%f 1)" t-scale))
    (doseq [[n v] [["t-scale" t-scale]
                   ["t-scroll" @t-scroll]
                   ["max-t" max-t]
                   ["max-agent" max-agent]
                   ["viewBox" (gstring/format "0 0 %f %d"
                                              (double (* t-scale max-t))
                                              (inc max-agent))]]]
      (.setAttributeNS timeline nil n v))))

(defn mousewheel
  [e]
  #_(log "mousewheel"
         :offsetX (.-offsetX e)
         :offsetY (.-offsetY e)
         :clientX (.-clientX e)
         :clientY (.-clientY e)
         :screenX (.-screenX e)
         :screenY (.-screenY e)
         :deltaX (.-deltaX e)
         :deltaY (.-deltaY e)
         :keys (js/Object.keys e)
         :shift (.-shiftKey e))
  ;; Only zoom for vertical scroll when shift is held
  ;; TODO: Figure out how to make this reasonable on a touchscreen
  (when (and (.-shiftKey e)
             (< (Math/abs (.-deltaX e)) (Math/abs (.-deltaY e))))
    (let [timeline (gdom/getElement "timeline")
          old-bounds (-> timeline .getBoundingClientRect)
          old-left (.-left old-bounds)
          old-width (.-width old-bounds)
          client-x (.-clientX e)
          old-origin (/ (- client-x old-left)
                        old-width)
          old-body-scroll (-> js/document .-body .-scrollLeft)
          new-scroll (swap! t-scroll (fn [s]
                                       (let [d (.-deltaY e)
                                             n (+ d s)]
                                         (if (< t-scroll-max n)
                                           s
                                           n))))
          new-scale (* t-scale-default (Math/pow t-scroll-factor new-scroll))
          _ (set-timeline-view new-scale)
          new-bounds (-> timeline .getBoundingClientRect)
          new-width (.-width new-bounds)
          width-delta (- new-width old-width)
          body-scroll-delta (* width-delta old-origin)
          new-body-scroll (+ body-scroll-delta old-body-scroll)]
      (log "mousewheel" :scroll new-scroll)
      (-> js/document .-body .-scrollLeft (set! new-body-scroll))
      (.preventDefault e)
      (.stopPropagation e))))

(defn ^:export start
  []
  (unhighlight nil)
  (set-timeline-view t-scale-default)
  (gevents/listen (gevents/MouseWheelHandler. (gdom/getElement "timeline"))
                  gevents/MouseWheelHandler.EventType.MOUSEWHEEL
                  mousewheel)
  (add-tracking "timeline-cell"))

(defn show-lightbox
  [id _]
  (-> id
      gdom/getElement
      (gstyle/setPosition 0 0)))

(defn hide-lightbox
  [e evt]
  (if (= e (.-target evt))
    (gstyle/setPosition e -10000 0)
    false))

(defn copy-contents
  "Copies the contents of the element to the clipboard"
  [id tip]
  (log "copy-contents" :id id)
  (let [r (.createRange js/document)
        t (gdom/getElement tip)]
    (.selectNodeContents r (gdom/getElement id))
    (-> js/window .getSelection .removeAllRanges)
    (-> js/window .getSelection (.addRange r))
    (.execCommand js/document "copy")
    (-> js/window .getSelection .removeAllRanges)
    (.play (fx/FadeInAndShow. t 250))
    (go
      (<! (timeout 5000))
      (.play (fx/FadeOutAndHide. t 500)))))

(defn ^:export start-detail
  []
  (doseq [e (array-seq (gdom/getElementsByClass "lightbox-trigger"))]
    (let [id (.getAttribute e "data-lightbox-id")]
      (gevents/listen e
                      gevents/EventType.CLICK
                      (fn [evt]
                        (show-lightbox id evt)))))
  (doseq [e (array-seq (gdom/getElementsByClass "lightbox"))]
    (gevents/listen e
                    gevents/EventType.CLICK
                    (fn [evt]
                      (hide-lightbox e evt))))
  (doseq [e (array-seq (gdom/getElementsByClass "copy-trigger"))]
    (let [id (.getAttribute e "data-copy-target")
          tip (.getAttribute e "data-copy-tip")]
      (gevents/listen e
                      gevents/EventType.CLICK
                      (fn [evt]
                        (copy-contents id tip))))))
