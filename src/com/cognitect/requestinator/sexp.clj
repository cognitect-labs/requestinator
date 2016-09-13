(ns com.cognitect.requestinator.sexp
  "A library for evaluating s-expressions."
  (:refer-clojure :exclude [eval]))

(defn eval
  "Evaluates `expr` calling `symbol-resolver` and
  `invocation-resolver` to resolve symbols and lists.
  `symbol-resolver` is function of one argument, the symbol.
  `function-resolver` is passed two arguments: the operation and the
  a seq of the operands.

  Note that as a result, eval implements either a Lisp-1 or a Lisp-2
  depending on how the caller chooses to perform resolution."
  [expr symbol-resolver invocation-resolver]
  ;; TODO: This is a first pass. Not sure I'm thrilled with it.
  (cond
    (symbol? expr) (symbol-resolver expr)

    (and (seq? expr)
         (-> expr first symbol?))
    (invocation-resolver
     (first expr)
     (->> expr rest (map #(eval % symbol-resolver invocation-resolver))))

    :else expr))

