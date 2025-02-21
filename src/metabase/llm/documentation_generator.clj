(ns metabase.llm.documentation-generator
  (:require [clojure.string :as str]
            [metabase.lib.openai :as openai]
            [metabase.lib.pinecone :as pinecone]
            [metabase.util.i18n :refer [tru]]
            [metabase.util.log :as log]
            [toucan2.core :as t2]))

(def ^:private doc-generator-prompt
  "You are an expert in databases and information retrieval. Your task is to generate a comprehensive document that helps an LLM model deeply understand how this database is structured and how it operates.

### Database Overview:
- Provide an overall description of the database.
- Explain the purpose and use cases of the database.

### Tables & Relationships:
- List and describe all the tables in the database.
- Detail the relationships between tables (primary keys, foreign keys, and joins).
- Highlight any important constraints or indexing strategies.

Based on the following generated texts from various prompts:

%s

Generate a comprehensive and well-structured document.")

(defn generate-documentation!
  "Generate comprehensive documentation for a database based on existing prompts"
  [database-id]
  (try
    (let [;; First get the index record for this database
          index-record (t2/select-one [:model/index-database-llm :*]
                                    :database_id database-id)
          
          ;; Check if we have an index
          _ (when-not index-record
              (throw (ex-info "No index found for this database"
                            {:database-id database-id})))
          
          ;; Get the index name from the record
          index-name (:pinecone_index_id index-record)
          
          ;; Then get all prompts for this index
          prompts (t2/select [:model/prompt-file-llm :*]
                            :index_database_llm_id (:id index-record)
                            :status "completed")
          
          ;; Check if we have prompts
          _ (when (empty? prompts)
              (throw (ex-info "No completed prompts found for this database"
                            {:database-id database-id})))
          
          ;; Combine all generated texts
          generated-texts (str/join "\n\n---\n\n" 
                                  (map :generated_text prompts))
          
          ;; Generate comprehensive doc
          prompt (format doc-generator-prompt generated-texts)
          documentation (openai/generate-text prompt)
          
          ;; Create embeddings
          embeddings (openai/create-embeddings documentation)
          
          ;; Store in Pinecone
          vector-id (str "doc-" database-id)
          metadata {:type "documentation"
                   :database_id database-id
                   :text documentation}
          _ (pinecone/upsert-vector! index-name
                                    vector-id
                                    embeddings
                                    metadata)]
      {:success true
       :documentation documentation})
    (catch Exception e
      (log/error e "Error generating documentation")
      {:success false
       :error (tru "Error generating documentation")
       :details (.getMessage e)}))) 