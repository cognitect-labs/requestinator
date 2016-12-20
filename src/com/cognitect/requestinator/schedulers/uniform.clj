;; Copyright (c) 2016 Cognitect, Inc.
;;
;; This file is part of Requestinator.
;;
;; All rights reserved. This program and the accompanying materials
;; are made available under the terms of the Eclipse Public License v1.0
;; which accompanies this distribution, and is available at
;; http://www.eclipse.org/legal/epl-v10.html
(ns com.cognitect.requestinator.schedulers.uniform
  (:require [com.cognitect.requestinator.math :as math]
            [com.cognitect.requestinator.request :as request]
            [com.cognitect.requestinator.scheduler :as schedule]
            [com.cognitect.requestinator.serialization :as ser]))

;; Generates independent requests for all endpoints in the spec, with
;; uniform probability and Erlang-distributed interarrival times.
(defrecord UniformRequestScheduler [interarrival]
  schedule/RequestScheduler
  (-schedule [this generator]
    (map (fn [t request]
           {::schedule/t t
            ::schedule/request request})
         (reductions + (repeatedly #(math/erlang interarrival)))
         ;; TODO: Support parameters at some point
         (request/generate generator {}))))

(ser/register-handlers!
 {:edn {:read {'requestinator.scheduler/uniform map->UniformRequestScheduler}}})
