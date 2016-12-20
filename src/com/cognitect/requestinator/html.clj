;; Copyright (c) 2016 Cognitect, Inc.
;;
;; This file is part of Requestinator.
;;
;; All rights reserved. This program and the accompanying materials
;; are made available under the terms of the Eclipse Public License v1.0
;; which accompanies this distribution, and is available at
;; http://www.eclipse.org/legal/epl-v10.html
(ns com.cognitect.requestinator.html
  "A library for generating HTML. Inspired by Hiccup, but faster and
  less of a pain to debug."
  (:require [clojure.string :as str]))

(declare html)

;; For unescaped content
(defrecord Raw [s])

(defn raw
  "Returns a node that will not be HTML escaped when rendered."
  [s]
  (->Raw s))

(defn ^String tag-token
  "Parse part of a tag name, returning the next token. Tokens are
  separated by . or #."
  [^String s ^long start]
  (let [dot-pos (.indexOf s "." start)
        hash-pos (.indexOf s "#" start)]
    (.substring s start (cond
                          (and (neg? dot-pos) (neg? hash-pos)) (.length s)
                          (neg? hash-pos) dot-pos
                          :else hash-pos))))

(defn tag-info
  "Parse a tag name, returning a map with keys :name, :classes,
  and :id."
  [^String tag]
  (let [tag-name (tag-token tag 0)]
    (loop [remainder (.substring tag (.length ^String tag-name))
           classes []
           id nil]
      (cond
        (.isEmpty remainder)
        {:name    tag-name
         :classes classes
         :id      id}

        (.startsWith remainder ".")
        (let [class-name (tag-token remainder 1)]
          (recur (.substring remainder (inc (.length ^String class-name)))
                 (conj classes class-name)
                 id))

        (.startsWith remainder "#")
        (let [id-name ^String (tag-token remainder 1)]
          (recur (.substring remainder (inc (.length ^String id-name)))
                 classes
                 id-name))

        :else
        (throw (ex-info "Unparseable tag" {:reason ::unparseable-tag
                                           :tag tag}))))))

(defn- add-classes
  "Add `classes` (vector of strings) to the classes present in
  `attribs` map. Returns the updated attribs map."
  [attribs classes]
  (if (and (empty? classes)
           (empty? (:class attribs)))
    attribs
    (update attribs :class #(str/join " " (conj classes %)))))

(def self-closing?
  "Returns true if the argument is the name of a tag that can self-close.
  Because HTML is dumb."
  (complement #{"script" "iframe" "a"}))

(defn write-element
  "Emit a Hiccup-style element (vector with keyword first element) to
  a Writer."
  [^java.io.Writer w [tag & contents]]
  (try
    (let [{:keys [^String name classes id]} (tag-info (name tag))
          attribs? (map? (first contents))
          attribs (if attribs?
                    (first contents)
                    {})
          contents (if attribs?
                     (rest contents)
                     contents)
          attribs (add-classes attribs classes)
          attribs (if id
                    (assoc attribs :id id)
                    attribs)]
      (.write w "<")
      (.write w name)
      (doseq [[a v] attribs]
        (.write w " ")
        (.write w ^String (clojure.core/name a))
        (.write w "='")
        (when v (.write w (-> v .toString (.replace "'" "&apos;"))))
        (.write w "'"))
      (if (and (empty? contents)
               (self-closing? name))
        (.write w "/>")
        (do
          (.write w ">")
          (doseq [item contents]
            (html w item))
          (.write w "</")
          (.write w name)
          (.write w ">"))))
    (catch Throwable t
      (throw (ex-info "Error while writing element"
                      {:reason ::exception-in-write-element
                       :tag tag
                       :contents contents}
                      t)))))

(defn escape
  "Returns HTML-escaped version of s"
  [s]
  (org.apache.commons.lang3.StringEscapeUtils/escapeHtml4 s))

(defn html
  "Emit a Hiccup-style data structure to Writer `w`."
  [^java.io.Writer w v]
  (cond
    (instance? Raw v)
    (.write w (:s v))

    (coll? v)
    (if (keyword? (first v))
      (write-element w v)
      (doseq [x v]
        (html w x)))

    (string? v)
    (.write w ^String (escape v))

    (number? v)
    (.write w (.toString v))

    (nil? v)
    nil

    :else
    (throw (ex-info "Unknown node type" {:reason ::unknown-element-type
                                         :v v}))))

(defn html-str
  "Render a Hiccup-style data structure to HTML, returning it as a string"
  [v]
  (with-out-str
    (html *out* v)))

(comment
  (html-str [:div])
  (html-str [:div.foo])
  (html-str [:div.d {:class "a b c"}])
  (html-str [:div#quux.foo])
  (html-str [:div.foo "blah"])
  (html-str [:div.foo {:ketchup "mustard"}])
  (html-str [:div.foo.bar [:ul [:li "item1"] [:li "item2"]]])
  (html-str [:div [:ul [[:li.odd "item1"] [:li.even "item2"] [:li]]]])
  (html-str [:a])
  (html-str [:a {:onclick "do_thing('X')"}])
)
