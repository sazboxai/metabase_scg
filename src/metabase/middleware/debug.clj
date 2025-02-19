(ns metabase.middleware.debug
  (:require [clojure.pprint :as pprint]
            [metabase.util.log :as log]
            [metabase.api.routes :as routes]))

(defn wrap-debug-logging
  [handler]
  (fn [request]
    (println "\n=== FULL REQUEST DEBUG START ===")
    (println "URI:" (:uri request))
    (println "Request Method:" (:request-method request))
    (println "Route Params:" (pr-str (:route-params request)))
    (println "Path Params:" (pr-str (:path-params request)))
    (println "Query Params:" (pr-str (:query-params request)))
    (println "Form Params:" (pr-str (:form-params request)))
    (println "Body Params:" (pr-str (:body-params request)))
    (println "Headers:" (pr-str (:headers request)))
    (println "Body:" (pr-str (:body request)))
    (println "=== FULL REQUEST DEBUG END ===\n")
    
    (let [response (handler request)]
      (println "\n=== RESPONSE DEBUG START ===")
      (println "Status:" (:status response))
      (println "Headers:" (pr-str (:headers response)))
      (println "Body:" (pr-str (:body response)))
      (println "=== RESPONSE DEBUG END ===\n")
      response)))

(defn log-api-call
  "Middleware to log API calls"
  [handler]
  (fn [request]
    (log/info "API Call:" (:uri request) (:request-method request))
    (handler request)))

(defn wrap-api-debug
  "Middleware to debug API routing"
  [handler]
  (fn [request]
    (log/info "API Request:" (:request-method request) (:uri request))
    (when-let [route-map (try 
                          (requiring-resolve 'metabase.api.routes/route-map)
                          (catch Exception _))]
      (log/info "Available routes:" (keys @route-map)))
    (let [response (handler request)]
      (log/info "API Response:" (:status response))
      response))) 