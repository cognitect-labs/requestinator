;; Copyright (c) 2016 Cognitect, Inc.
;;
;; This file is part of Requestinator.
;;
;; All rights reserved. This program and the accompanying materials
;; are made available under the terms of the Eclipse Public License v1.0
;; which accompanies this distribution, and is available at
;; http://www.eclipse.org/legal/epl-v10.html
(ns com.cognitect.requestinator.thread-pool
  "Implements a generic thread pool of fixed size that continually
  runs a given task on those threads. The thread pool is implemented
  as a component which can be started and stopped (per
  com.stuartsierra.component).

  Tasks to be run in the thread pool are specified as no-arg functions
  whose return value is ignored. They are wrapped in error handling
  and an infinite loop."
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component])
  (:import [java.util.concurrent TimeUnit]))

;;; Functions

(defn- wrap-task
  "Wraps the given task with a try/catch block, logging, and looping
  to turn it into an infinitely-running job."
  [task name]
  (fn []
    (when
        (try
          (task)
          :continue
          (catch InterruptedException t
            (log/warn t "InterruptedException thrown - shutting down thread."
                      :thread-pool name)
            nil)
          (catch Throwable t
            (log/error t "Exception thrown during task execution"
                       :thread-pool name)
            :continue))
      (recur))))

(defrecord ThreadPool
  [size task name]
  component/Lifecycle
  (start [this]
         (let [executor (java.util.concurrent.Executors/newFixedThreadPool size)]
           (dotimes [_ size]
             (.submit executor (wrap-task task name)))
           (assoc this :executor executor)))
  (stop [{:keys [executor] :as this}]
        (.shutdownNow executor)
        (when-not (.awaitTermination executor 30 TimeUnit/SECONDS)
          (log/warn "Timed out waiting for thread pool to shut down."
                    :thread-pool name))
        (assoc this :executor nil)))
