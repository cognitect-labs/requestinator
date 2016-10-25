(ns com.cognitect.requestinator.spec
  "A library that defines a set of operations for mapping specs to
  requests. This happens in two steps. First, a spec is used to
  generate a sequence of `request template`, an abstract description
  of a request to be executed. Abstract, because it may contain
  placeholders that need to be filled in at runtime, for instance
  based on the results of previous requests. At runtime, request
  templates are built into actual requests.")

(defprotocol RequestTemplateBuilder
  (-request-templates [this params]
    "Return a lazy sequence of request templates."))

(defprotocol RequestBuilder
  (-build [template params]
    "Fills in the 'holes' in a request template to produce an
    actionable request map."))

;; We consolidate calls through these functions so that we
;; have a central place to add things like logging.
(defn request-templates
  "Generate a lazy sequence of request maps. Call this in preference
  to the protocol method."
  [spec params]
  (-request-templates spec params))

(defn build
  "Convert a request template into a request based on the information
  in the `state` map. Call this in preference to the protocol method"
  [template state]
  (-build template state))

(defmulti dynamic-param-op (fn [op context & args] op))



