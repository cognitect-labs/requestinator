(ns com.cognitect.requestinator.math)

(defn erlang
  "Returns an Erlang-distributed random value with mean `mean`."
  [mean]
  ;; TODO: Get rid of call to (rand)
  (- (* (Math/log (rand)) mean)))

