(ns com.cognitect.requestinator.generators
  "Request generators library. Don't call the `-generate` method of
  `RequestGenerator` directly. Call through `generate` instead."
  (:require [clojure.edn :as edn]
            [clojure.tools.logging :as log]
            [com.cognitect.requestinator.generators.markov :as markov]
            [com.cognitect.requestinator.serialization :as ser]
            [com.cognitect.requestinator.swagger :as swagger]))

(defprotocol RequestGenerator
  (-generate [this spec] "Returns a lazy sequence of request descriptions,
  a map with keys ::t and ::request."))

;; Generates independent requests for all endpoints in the spec, with
;; uniform probability and Erlang-distributed interarrival times.
(defrecord UniformRequestGenerator [interarrival]
  RequestGenerator
  (-generate [this spec]
    (swagger/generate spec interarrival)))

(defrecord MarkovRequestGenerator [requests graph]
  RequestGenerator
  (-generate [this spec]
    (markov/generate spec requests graph)))

(def readers
  {'requestinator.generators/markov map->MarkovRequestGenerator
   'requestinator.generators/uniform map->UniformRequestGenerator})

;; We consolidate calls to generators through this function so that we
;; have a central place to add things like logging.
(defn generate
  "Generate a lazy sequence of request descriptions. Call this in
  preference to the -generate protocol method. Takes `base-uri` as a
  context to resolve any relative URIs."
  [generator spec]
  (-generate generator spec))

(defn read-generator
  "Return a generator instance given a URI."
  [uri additional-readers]
  (log/debug "read-generator" :uri uri :additional-readers additional-readers)
  (let [fetcher (ser/create-fetcher uri)]
    (->> (fetcher "")
         String.
         (edn/read-string
          {:readers (merge additional-readers readers)}))))
