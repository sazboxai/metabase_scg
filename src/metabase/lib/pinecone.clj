(ns metabase.lib.pinecone
  (:require [clj-http.client :as client]
            [cheshire.core :as json]
            [metabase.models.ai-settings :as ai-settings]
            [metabase.util.i18n :refer [tru]]
            [metabase.util.log :as log]))

(def ^:private pinecone-api-base-url "https://api.pinecone.io")

(defn- get-pinecone-headers []
  (let [api-key (ai-settings/pinecone-api-key)]
    (log/info "Using Pinecone API key:" (if api-key "Found" "Not found"))
    {"Api-Key" api-key
     "Content-Type" "application/json"
     "Accept" "application/json"}))

(defn create-pinecone-index!
  "Creates a new Pinecone index with the given database ID.
   Returns the index name if successful, throws exception if failed."
  [database-id]
  (try
    (let [index-name (str "metabase-index-" database-id)
          request-body (json/generate-string
                        {:name index-name
                         :dimension 1536  ;; OpenAI embedding dimension
                         :metric "cosine"
                         :spec {:serverless {:cloud "aws"
                                           :region "us-east-1"}}})
          _ (log/info "Creating Pinecone index with request:" request-body)
          response (client/post (str pinecone-api-base-url "/indexes")
                              {:headers (get-pinecone-headers)
                               :body request-body
                               :throw-exceptions false})]
      (log/info "Pinecone API response:" {:status (:status response)
                                         :body (:body response)})
      (if (= 201 (:status response))
        index-name
        (throw (ex-info (tru "Failed to create Pinecone index")
                       {:status (:status response)
                        :body (json/parse-string (:body response) true)}))))
    (catch Exception e
      (log/error e (tru "Error creating Pinecone index"))
      (throw (ex-info (tru "Error creating Pinecone index")
                     {:cause (.getMessage e)})))))

(defn check-index-status
  "Checks if a Pinecone index exists and is ready."
  [index-name]
  (try
    (let [response (client/get (str pinecone-api-base-url "/indexes/" index-name)
                              {:headers (get-pinecone-headers)
                               :throw-exceptions false})]
      (= 200 (:status response)))
    (catch Exception _
      false)))

(defn delete-pinecone-index!
  "Deletes a Pinecone index."
  [index-name]
  (try
    (let [response (client/delete (str pinecone-api-base-url "/databases/" index-name)
                                {:headers (get-pinecone-headers)
                                 :throw-exceptions false})]
      (= 202 (:status response)))
    (catch Exception e
      (log/error e (tru "Error deleting Pinecone index"))
      false)))

(defn- ensure-index-exists!
  "Make sure the index exists before trying to use it"
  [index-name]
  (when-not (check-index-status index-name)
    (throw (ex-info (tru "Pinecone index {0} does not exist" index-name)
                   {:status 404
                    :index-name index-name}))))

(defn- get-pinecone-api-url
  "Get the API URL for a specific index"
  [index-name]
  (try
    (let [response (client/get (str pinecone-api-base-url "/indexes/" index-name)
                              {:headers (get-pinecone-headers)
                               :throw-exceptions false})]
      (if (= 200 (:status response))
        (-> response :body json/parse-string (get "host"))
        (throw (ex-info "Failed to get index host" 
                       {:status (:status response)
                        :body (json/parse-string (:body response) true)}))))
    (catch Exception e
      (log/error e "Error getting index host")
      (throw e))))

(defn upsert-vector!
  "Upsert a vector into Pinecone"
  [index-name vector-id embeddings metadata]
  (try
    (log/info "Attempting to upsert vector to index:" index-name)
    (ensure-index-exists! index-name)
    (let [host (get-pinecone-api-url index-name)
          response (client/post (str "https://" host "/vectors/upsert")
                              {:headers (get-pinecone-headers)
                               :body (json/generate-string
                                     {:vectors [{:id vector-id
                                               :values embeddings
                                               :metadata metadata}]})
                               :throw-exceptions false})]
      (if (= 200 (:status response))
        true
        (throw (ex-info (tru "Failed to upsert vector")
                       {:status (:status response)
                        :body (json/parse-string (:body response) true)}))))
    (catch Exception e
      (log/error e (tru "Error upserting vector"))
      (throw (ex-info (tru "Error upserting vector") {:cause (.getMessage e)}))))) 