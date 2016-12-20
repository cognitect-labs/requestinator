;; Copyright (c) 2016 Cognitect, Inc.
;;
;; This file is part of Requestinator.
;;
;; All rights reserved. This program and the accompanying materials
;; are made available under the terms of the Eclipse Public License v1.0
;; which accompanies this distribution, and is available at
;; http://www.eclipse.org/legal/epl-v10.html
(ns com.cognitect.requestinator.json-test
  (:require [clojure.test :refer :all]
            [com.cognitect.requestinator.json :refer :all]))

(deftest amend-test
  (is (= {"foo" "bar"}
         (amend {} [{"op" "add"
                     "path" "#/foo"
                     "value" "bar"}]))
      "simple add")
  (is (= {"foo" "bar"
          "quux" {"baaz" 3}}
         (amend {"foo" "bar"}
                [{"op" "add"
                  "path" "#/quux/baaz"
                  "value" 3}]))
      "Intermediate keys are created")
  (is (= {}
         (amend {"foo" "bar"}
                [{"op" "remove"
                  "path" "#/foo"}]))
      "Simple remove")
  (is (= {"foo" {"bar" "quux"}}
         (amend {"foo" {"bar" "quux"
                        "baaz" 3}}
                [{"op" "remove"
                  "path" "#/foo/baaz"}]))
      "Nested remove")
  (is (= {"foo" "bar"}
         (amend {"foo" "bar"}
                [{"op" "remove"
                  "path" "#/no/such/path"}]))
      "Removal of nonexistent node does nothing")
  (is (= {"foo" {"bar" 3
                 "quux" {"bling" true}}}
         (amend {}
                [{"op" "add"
                  "path" "#/foo/quux/bling"
                  "value" true}
                 {"op" "add"
                  "path" "#/foo/bar"
                  "value" 3}
                 {"op" "add"
                  "path" "#/foo/popsicle"
                  "value" "whatever"}
                 {"op" "remove"
                  "path" "#/foo/popsicle"}]))
      "Multiple ops"))
