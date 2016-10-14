(ns com.cognitect.requestinator.generators.markov
  "A request generator that uses a first-order Markov chain to create
  requests."
  (:require [causatum.event-streams :as es]
            [clojure.test.check.generators :as tcgen]
            [clojure.tools.logging :as log]
            [com.cognitect.requestinator.math :as math]
            [com.cognitect.requestinator.spec :as spec]))

(defn produce-request
  "Takes the intermediate state of the request-producing process and
  the next state, and returns the next intermediate state."
  [{:keys [::request-seqs] :as s} {:keys [state rtime]}]
  (let [[request & more] (get request-seqs state)]
    (-> s
        (assoc ::request request
               ::t rtime)
        (assoc-in [::request-seqs state] more))))

(defn generate
  "Return a lazy sequence of Requesinator request maps."
  [spec requests graph]
  ;; Get a lazy sequence of all the various request types, then draw
  ;; from them as we hit each one
  (let [request-seqs (->> requests
                          (map (fn [[state params]]
                                 [state (spec/requests spec params)]))
                          (into {}))]
    (->>  (es/event-stream {:graph graph
                            :delay-ops {'constant (fn [rtime t] t)
                                        'erlang (fn [rtime mean] (math/erlang mean))}}
                           [{:state :start :rtime 0}])
          (reductions produce-request {::request nil
                                       ::request-seqs request-seqs})
          (remove #(-> % ::request nil?))
          (map (fn [{:keys [::request ::t]}]
                 {:com.cognitect.requestinator.generators/t t
                  :com.cognitect.requestinator.generators/request request})))))



