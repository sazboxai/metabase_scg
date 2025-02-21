(ns metabase.api.llm
  "API endpoints for LLM functionality."
  (:require
   [metabase.api.common :as api]
   [metabase.api.macros :as api.macros]
   [metabase.models.index-database-llm :as index-db]
   [metabase.models.prompt-file-llm :as prompt]
   [metabase.lib.pinecone :as pinecone]
   [metabase.lib.openai :as openai]
   [metabase.util.malli.schema :as ms]
   [metabase.util.i18n :refer [tru]]
   [metabase.util.log :as log]
   [toucan2.core :as t2]
   [metabase.llm.prompt-generator :as prompt-gen]))

(log/info "Loading LLM API namespace")

(api.macros/defendpoint :get "/"
  "Get all LLM indexes."
  []
  (log/info "List endpoint called")
  (try 
    (api/check-superuser)
    {:data (t2/select :model/index-database-llm)}
    (catch Exception e
      (log/error e "Error fetching LLM indexes")
      {:error (tru "Error fetching LLM indexes")
       :details (.getMessage e)})))

(api.macros/defendpoint :post "/"
  "Create a new LLM index."
  [_route-params
   _query-params
   {:keys [database_id description selected_tables pinecone_index_id]}
   :- [:map
       [:database_id ms/PositiveInt]
       [:description ms/NonBlankString]
       [:selected_tables [:sequential [:or ms/PositiveInt ms/NonBlankString]]]
       [:pinecone_index_id {:optional true} ms/NonBlankString]]]
  (try 
    (api/check-superuser)
    (let [created-by api/*current-user-id*
          index (index-db/create-index-database-llm!
                 {:database-id database_id
                  :description description
                  :selected-tables selected_tables
                  :created-by created-by
                  :pinecone-index-id pinecone_index_id})]
      {:data index})
    (catch Exception e
      (log/error e "Error creating LLM index")
      {:error (tru "Error creating LLM index")
       :details (.getMessage e)})))

(api.macros/defendpoint :post "/prompt"
  "Creates a new prompt."
  [_route-params
   _query-params
   {:keys [name description prompt index_database_llm_id table_reference]}
   :- [:map
       [:name           ms/NonBlankString]
       [:description    ms/NonBlankString]
       [:prompt         ms/NonBlankString]
       [:index_database_llm_id ms/PositiveInt]
       [:table_reference ms/PositiveInt]]]
  (try 
    (api/check-superuser)
    (let [created-prompt (prompt/create-prompt! 
                         {:name name
                          :description description
                          :prompt prompt
                          :index_database_llm_id index_database_llm_id
                          :table_reference table_reference})]
      {:data created-prompt})
    (catch Exception e
      (log/error e "Error creating prompt")
      {:error (tru "Error creating prompt")
       :details (.getMessage e)})))

(api.macros/defendpoint :get "/prompt"
  "Get all prompts."
  []
  (log/info "List prompts endpoint called")
  (try 
    (api/check-superuser)
    {:data (t2/select :model/prompt-file-llm)}
    (catch Exception e
      (log/error e "Error fetching prompts")
      {:error (tru "Error fetching prompts")
       :details (.getMessage e)})))

(api.macros/defendpoint :post "/pinecone/index"
  "Create a new Pinecone index for the given database."
  [_route-params
   _query-params
   {:keys [database_id]}
   :- [:map
       [:database_id ms/PositiveInt]]]
  (try
    (api/check-superuser)
    (let [index-name (pinecone/create-pinecone-index! database_id)]
      {:success true
       :data {:index_name index-name}})
    (catch Exception e
      (log/error e "Error creating Pinecone index")
      {:success false
       :error (tru "Error creating Pinecone index")
       :details (.getMessage e)})))

(api.macros/defendpoint :get "/pinecone/index/:index_name/status"
  "Check the status of a Pinecone index."
  [index_name]
  (try
    (api/check-superuser)
    {:success true
     :data {:exists (pinecone/check-index-status index_name)}}
    (catch Exception e
      (log/error e "Error checking Pinecone index status")
      {:success false
       :error (tru "Error checking Pinecone index status")
       :details (.getMessage e)})))

(defn- process-prompt!
  "Process a prompt through OpenAI and index in Pinecone"
  [prompt-id]
  (try
    (let [id (cond
              (string? prompt-id) (Integer/parseInt prompt-id)
              (number? prompt-id) prompt-id
              (map? prompt-id) (Integer/parseInt (str (:id prompt-id)))
              :else (throw (ex-info "Invalid ID type" {:id prompt-id})))
          prompt-record (t2/select-one [:model/prompt-file-llm :*]
                                     :id id)
          index-record (t2/select-one [:model/index-database-llm :*]
                                     :id (:index_database_llm_id prompt-record))
          generated-text (openai/generate-text (:prompt prompt-record))
          embeddings (openai/create-embeddings generated-text)
          vector-id (str "prompt-" id)
          metadata {:prompt_id id
                   :text generated-text
                   :table_reference (:table_reference prompt-record)}]
      
      (pinecone/upsert-vector! (:pinecone_index_id index-record)
                              vector-id
                              embeddings
                              metadata)
      
      (prompt/update-prompt! id 
                           {:status "completed"
                            :generated_text generated-text})
      {:success true})
    (catch Exception e
      (log/error e (tru "Error processing prompt"))
      (let [id (cond
                (string? prompt-id) (Integer/parseInt prompt-id)
                (number? prompt-id) prompt-id
                (map? prompt-id) (Integer/parseInt (str (:id prompt-id)))
                :else (throw (ex-info "Invalid ID type" {:id prompt-id})))]
        (prompt/update-prompt! id 
                             {:status "failed"})
        {:success false
         :error (tru "Error processing prompt")
         :details (.getMessage e)}))))

(api.macros/defendpoint :post "/prompt/:id/process"
  "Process a prompt through OpenAI and index in Pinecone"
  [id]
  {id ms/PositiveInt}
  (try
    (api/check-superuser)
    (process-prompt! id)
    (catch Exception e
      (log/error e "Error processing prompt")
      {:success false
       :error (tru "Error processing prompt")
       :details (.getMessage e)})))

(api.macros/defendpoint :post "/generate-query"
  "Generate a SQL query from natural language"
  [_route-params
   _query-params
   {:keys [question database_id]}]
  {question    ms/NonBlankString
   database_id ms/PositiveInt}
  (try
    (api/check-superuser)
    (let [index-record (t2/select-one [:model/index-database-llm :*]
                                     :database_id database_id)
          result (when index-record
                  (prompt-gen/generate-query! 
                   question 
                   (:pinecone_index_id index-record)))]
      (if result
        result
        {:success false
         :error (tru "No index found for database")}))
    (catch Exception e
      (log/error e "Error generating query")
      {:success false
       :error (tru "Error generating query")
       :details (.getMessage e)})))

;; Export routes using ns-handler
(def routes (api.macros/ns-handler))
