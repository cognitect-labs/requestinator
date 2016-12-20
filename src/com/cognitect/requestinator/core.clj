;; Copyright (c) 2016 Cognitect, Inc.
;;
;; This file is part of Requestinator.
;;
;; All rights reserved. This program and the accompanying materials
;; are made available under the terms of the Eclipse Public License v1.0
;; which accompanies this distribution, and is available at
;; http://www.eclipse.org/legal/epl-v10.html
(ns com.cognitect.requestinator.core
  "Core library for the Requestinator")

(defn self-destruct!
  "Every good inator needs one"
  []
  (println "Curse you, Perry the Platypus!")
  (System/exit 1))
