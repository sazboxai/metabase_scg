(ns metabase.lib.openai
  (:require [clj-http.client :as client]
            [cheshire.core :as json]
            [metabase.models.ai-settings :as ai-settings]
            [metabase.util.i18n :refer [tru]]
            [metabase.util.log :as log]))

(def ^:private openai-api-base-url "https://api.openai.com/v1")

(defn- get-openai-headers []
  (let [api-key (ai-settings/openai-api-key)]
    (when-not api-key
      (throw (ex-info (tru "OpenAI API key not configured")
                     {:status 400})))
    (log/info "Using OpenAI API key:" (if api-key "Found" "Not found"))
    {"Authorization" (str "Bearer " api-key)
     "Content-Type" "application/json"}))

(defn generate-text
  "Generate text using GPT-4 model"
  [prompt]
  (try
    (let [response (client/post (str openai-api-base-url "/chat/completions")
                               {:headers (get-openai-headers)
                                :body (json/generate-string
                                      {:model "gpt-4"
                                       :messages [{:role "user"
                                                 :content prompt}]
                                       :temperature 0.7})
                                :throw-exceptions false})]
      (if (= 200 (:status response))
        (-> response
            :body
            json/parse-string
            (get-in ["choices" 0 "message" "content"]))
        (throw (ex-info (tru "Failed to generate text")
                       {:status (:status response)
                        :body (json/parse-string (:body response) true)}))))
    (catch Exception e
      (log/error e (tru "Error generating text"))
      (throw (ex-info (tru "Error generating text") {:cause (.getMessage e)})))))

(defn create-embeddings
  "Create embeddings using text-embedding-3-small model"
  [text]
  (try
    (let [response (client/post (str openai-api-base-url "/embeddings")
                               {:headers (get-openai-headers)
                                :body (json/generate-string
                                      {:model "text-embedding-3-small"
                                       :input text})
                                :throw-exceptions false})]
      (if (= 200 (:status response))
        (-> response
            :body
            json/parse-string
            (get-in ["data" 0 "embedding"]))
        (throw (ex-info (tru "Failed to create embeddings")
                       {:status (:status response)
                        :body (json/parse-string (:body response) true)}))))
    (catch Exception e
      (log/error e (tru "Error creating embeddings"))
      (throw (ex-info (tru "Error creating embeddings") {:cause (.getMessage e)}))))) 