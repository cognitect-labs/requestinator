(ns com.cognitect.requestinator.graphql
  "A library for generating request maps from GraphQL schema."
  (:require [clojure.data.json :as json]
            [clojure.test.check.generators :as gen]
            [com.cognitect.requestinator.http :as http]
            [com.cognitect.requestinator.request :as request]
            [com.cognitect.requestinator.serialization :as ser]))

;; (defn request-generator
;;   "Returns a generator for request maps based on spec and params."
;;   [spec params]
;;   (let [host "http://todo"
;;         ]
;;     (gen/let [op (gen/elements [:query :mutation])]
;;       ;; TODO: This should be an instance of Request, right?
;;       {:op op
;;        ;; :host host
;;        ;; :scheme scheme
;;        ;; :base-path base-path
;;        })))

;; (defn generate
;;   "Given a GraphQL schema, return a lazy sequence of request maps
;;   representing valid requests described by it."
;;   ([spec params] (generate spec params 30))
;;   ([spec params max-size]
;;    (gen/sample-seq (request-generator spec params) max-size)))

;; (defrecord Spec [spec]
;;   request/RequestGenerator
;;   (-generate [this params] (generate spec params)))

;; ;; TODO: Move toward this:
;; ;; https://github.com/graphql/graphql-js/blob/master/src/utilities/introspectionQuery.js
;; (def schema-query
;;   "The GraphQL query that returns all the information we need to
;;   create a spec."
;; "{
;;   __schema {
;;     queryType {
;;       name
;;     }
;;     mutationType {
;;       name
;;     }
;;     types {
;;       name
;;       fields {
;;         name
;;         args {
;;           name
;;           type {
;;             kind
;;             name
;;           }
;;           defaultValue
;;         }
;;         type {
;;           kind
;;           name
;;         }
;;       }
;;     }
;;   }
;; }
;; ")

;; (defn read-spec
;;   "Query the specified GraphQL URL and return an instance of Spec."
;;   [{:keys [url]}]
;;   (let [client (http/generate-client (http/cookie-store))
;;         req {:method :post
;;              :url url
;;              :headers {"Content-Type" "application/json"}
;;              :body (-> {:operationName nil
;;                         :query schema-query
;;                         :variables nil}
;;                        json/write-str)}]
;;     (-> req
;;         client
;;         :body
;;         json/read-str
;;         (get "data")
;;         (get "__schema"))))

;; TODO
#_(ser/register-handlers
 {:transit {:read {}
            :write {}}
  :edn {:read {}}})
