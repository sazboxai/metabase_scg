(ns metabase.api.llm
  "API endpoints for LLM functionality."
  (:require
   [metabase.api.common :as api]
   [metabase.api.macros :as api.macros]
   [metabase.models.index-database-llm :as index-db]
   [metabase.models.prompt-file-llm :as prompt]
   [metabase.util.malli.schema :as ms]
   [metabase.util.i18n :refer [tru]]
   [metabase.util.log :as log]
   [toucan2.core :as t2]))

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
   {:keys [database_id description selected_tables]}
   :- [:map
       [:database_id ms/PositiveInt]
       [:description ms/NonBlankString]
       [:selected_tables [:sequential [:or ms/PositiveInt ms/NonBlankString]]]]]
  (try 
    (api/check-superuser)
    (let [created-by api/*current-user-id*
          index (index-db/create-index-database-llm!
                 {:database-id database_id
                  :description description
                  :selected-tables selected_tables
                  :created-by created-by})]
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

;; Export routes using ns-handler
(def routes (api.macros/ns-handler))
