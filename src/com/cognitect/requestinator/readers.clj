(ns com.cognitect.requestinator.readers
  (:require [clojure.edn :as edn]
            [clojure.tools.logging :as log]
            [com.cognitect.requestinator.generators :as gen]
            [com.cognitect.requestinator.serialization :as ser]
            [com.cognitect.requestinator.swagger :as swagger]))

(defn spec-readers
  [base-uri]
  {'requestinator.spec/swagger #(swagger/read-spec base-uri %)
   ;; For now, read URLs as strings, since we're not set up to include
   ;; specs as literals.
   'url identity})

(def generator-readers
  {'requestinator.generators/markov gen/map->MarkovRequestGenerator
   'requestinator.generators/uniform gen/map->UniformRequestGenerator})

(defn read-generator
  "Return a generator instance given a URI."
  [uri additional-readers]
  (log/debug "read-generator" :uri uri :additional-readers additional-readers)
  (let [fetcher (ser/create-fetcher uri)]
    (->> (fetcher "")
         String.
         (edn/read-string
          {:readers (merge additional-readers generator-readers)}))))


