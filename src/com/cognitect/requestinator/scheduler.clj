;; Copyright (c) 2016 Cognitect, Inc.
;;
;; This file is part of Requestinator.
;;
;; All rights reserved. This program and the accompanying materials
;; are made available under the terms of the Eclipse Public License v1.0
;; which accompanies this distribution, and is available at
;; http://www.eclipse.org/legal/epl-v10.html
(ns com.cognitect.requestinator.scheduler
  "Request scheduler library. Don't call the `-schedule` protocol method
  of `RequestScheduler` directly. Call through `schedule` instead."
  (:require [clojure.edn :as edn]
            [clojure.tools.logging :as log]
            [com.cognitect.requestinator.math :as math]
            [com.cognitect.requestinator.spec :as spec]))

(defprotocol RequestScheduler
  (-schedule [this spec] "Returns an agenda, a lazy sequence of maps
  with keys ::t and ::request."))

;; We consolidate calls to -schedule through this function so that we
;; have a central place to add things like logging.
(defn schedule
  "Generate an agenda, a lazy sequence of maps with
  keys ::t, ::request, and (optionally) ::store. Call this in
  preference to the -schedule protocol method."
  [scheduler spec]
  (-schedule scheduler spec))


