(ns com.cognitect.requestinator.spec
  "A library for reading and parsing specifications, and generating
  random request maps from them.")

(defprotocol Spec
  (-requests [this params]
    "Return a lazy sequence of request maps."))

;; We consolidate calls through this function so that we
;; have a central place to add things like logging.
(defn requests
  "Generate a lazy sequence of request maps. Call this in preference
  to the -generate protocol method."
  [spec params]
  (-requests spec params))

