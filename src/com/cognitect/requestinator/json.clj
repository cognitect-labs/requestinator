;; Copyright (c) 2016 Cognitect, Inc.
;;
;; This file is part of Requestinator.
;;
;; All rights reserved. This program and the accompanying materials
;; are made available under the terms of the Eclipse Public License v1.0
;; which accompanies this distribution, and is available at
;; http://www.eclipse.org/legal/epl-v10.html
(ns com.cognitect.requestinator.json
  "A library for navigating and manipulating JSON via path selectors."
  (:require [clojure.string :as str]))

(defn- path-segments
  [path]
  (when-not (.startsWith path "#/")
    (throw (ex-info "Non-relative paths not currently supported"
                    {:reason ::non-fragment-pathh
                     :path path})))
  (str/split (subs path 2) #"/"))

(defn- select-relative
  "ALPHA: NOT FULLY IMPLEMENTED. Returns the value of JSON document
  `doc` at location indicated by `pointer`, a relative reference as
  specified by http://tools.ietf.org/html/rfc6901."
  [doc ^String pointer]
  ;; TODO: Implement spec more completely.
  (reduce (fn [context segment]
            (cond
              (= "" segment) context
              (re-matches #"\d+" segment) (get context (Long. segment))
              :else (get context segment)))
          doc
          (path-segments pointer)))

(defn select
  "ALPHA: NOT FULLY IMPLEMENTED. Returns the value of JSON document
  `doc` at location indicated by `pointer`, a reference as specified
  by http://tools.ietf.org/html/rfc6901."
  [doc ^String pointer]
  ;; TODO: Implement the rest of the spec
  (select-relative doc pointer))

(defmulti amend*
  "Implementation of amend operations"
  (fn [doc amendment] (get amendment "op")))

(defmethod amend* "add"
  [doc amendment]
  (let [{:strs [path value]} amendment]
    (assoc-in doc (path-segments path) value)))

(defn- exists?
  "Returns true if `segments` is a path-like sequence corresponding to
  an existing node in `doc`."
  [doc segments]
  (loop [[head & more] segments
         context doc]
    (cond
      (not head) true

      (not (map? context)) false

      (contains? context head) (recur more (get context head))

      :else false)))

(defmethod amend* "remove"
  [doc amendment]
  (let [path (get amendment "path")
        segments (path-segments path)
        intermediate (butlast segments)
        final (last segments)]
    (if (exists? doc segments)
      (if (empty? intermediate)
        (dissoc doc final)
        (update-in doc intermediate dissoc final))
      doc)))

(defn amend
  "Amends JSON document `doc` per `amendments`, a sequence of maps
  each with string keys:

  op    - The string 'add' or 'remove'
  path  - a JSON pointer per `select` indicating the element to modify.
  value - required when op is add

  If op is 'add', adds value at path, overwriting any existing value,
  and creating intermediate keys as necessary.

  If op is 'delete', removes the value at path, if it exists. If it
  does not exist, do nothing. Never creates any new nodes, even
  intermediate ones."
  [doc amendments]
  (reduce amend* doc amendments))
