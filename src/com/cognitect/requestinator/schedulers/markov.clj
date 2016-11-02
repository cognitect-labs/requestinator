(ns com.cognitect.requestinator.schedulers.markov
  "A request generator that uses a first-order Markov chain to create
  requests."
  (:require [causatum.event-streams :as es]
            [clojure.test.check.generators :as tcgen]
            [clojure.tools.logging :as log]
            [com.cognitect.requestinator.math :as math]
            [com.cognitect.requestinator.request :as request]
            [com.cognitect.requestinator.scheduler :as schedule]
            [com.cognitect.requestinator.serialization :as ser]))

(defn produce-request
  "Takes the intermediate state of the request-producing process and
  the next state, and returns the next intermediate state."
  [{:keys [::request-seqs request-params] :as s} {:keys [state rtime]}]
  (let [[request & more] (get request-seqs state)]
    (-> s
        (assoc ::schedule/request request
               ::schedule/t rtime)
        (merge (when-let [store (get-in request-params [state :store])]
                 {::schedule/store store}))
        (assoc-in [::request-seqs state] more))))

(defn schedule
  "Return a lazy sequence of Requesinator request templates."
  [generator request-params graph]
  ;; Get a lazy sequence of all the various request types, then draw
  ;; from them as we hit each one
  (let [request-seqs (->> request-params
                          (map (fn [[state params]]
                                 [state (request/generate generator params)]))
                          (into {}))]
    (->>  (es/event-stream {:graph graph
                            :delay-ops {'constant (fn [rtime t] t)
                                        'erlang (fn [rtime mean] (math/erlang mean))}}
                           [{:state :start :rtime 0}])
          (reductions produce-request {::schedule/request nil
                                       :request-params request-params
                                       ::request-seqs request-seqs})
          (remove #(-> % ::schedule/request nil?))
          (map #(select-keys % [::schedule/request ::schedule/t ::schedule/store])))))

(defrecord MarkovRequestScheduler [request-params graph]
  schedule/RequestScheduler
  (-schedule [this generator]
    (schedule generator request-params graph)))

(ser/register-handlers!
 {:edn {:read {'requestinator.scheduler/markov map->MarkovRequestScheduler}}})
