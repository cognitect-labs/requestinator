;; Copyright (c) 2016 Cognitect, Inc.
;;
;; This file is part of Requestinator.
;;
;; All rights reserved. This program and the accompanying materials
;; are made available under the terms of the Eclipse Public License v1.0
;; which accompanies this distribution, and is available at
;; http://www.eclipse.org/legal/epl-v10.html
(ns com.cognitect.requestinator.math)

(defn erlang
  "Returns an Erlang-distributed random value with mean `mean`."
  [mean]
  ;; TODO: Get rid of call to (rand)
  (- (* (Math/log (rand)) mean)))

