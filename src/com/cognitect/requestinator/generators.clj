(ns com.cognitect.requestinator.generators
  "Request generators library. Don't call the `-generate` method of
  `RequestGenerator` directly. Call through `generate` instead."
  (:require [clojure.edn :as edn]
            [clojure.tools.logging :as log]
            [com.cognitect.requestinator.generators.markov :as markov]
            [com.cognitect.requestinator.math :as math]
            [com.cognitect.requestinator.spec :as spec]))

(defprotocol RequestGenerator
  (-generate [this spec] "Returns a lazy sequence of request descriptions,
  a map with keys ::t and ::request."))

;; Generates independent requests for all endpoints in the spec, with
;; uniform probability and Erlang-distributed interarrival times.
(defrecord UniformRequestGenerator [interarrival]
  RequestGenerator
  (-generate [this spec]
    (map (fn [t request]
           {::t t
            ::request request})
         (reductions + (repeatedly #(math/erlang interarrival)))
         ;; TODO: Support parameters at some point
         (spec/requests spec {}))))

(defrecord MarkovRequestGenerator [requests graph]
  RequestGenerator
  (-generate [this spec]
    (markov/generate spec requests graph)))

;; We consolidate calls to generators through this function so that we
;; have a central place to add things like logging.
(defn generate
  "Generate a lazy sequence of request descriptions. Call this in
  preference to the -generate protocol method. Takes `base-uri` as a
  context to resolve any relative URIs."
  [generator spec]
  (-generate generator spec))


