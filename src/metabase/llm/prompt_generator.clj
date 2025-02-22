(ns metabase.llm.prompt-generator
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [metabase.models.table :as table]
            [metabase.models.field :as field]
            [metabase.models.field-values :as field-values]
            [metabase.util :as u]
            [metabase.util.date-2 :as u.date]
            [toucan2.core :as t2]
            [metabase.lib.openai :as openai]
            [metabase.lib.pinecone :as pinecone]
            [metabase.util.i18n :refer [tru]]
            [metabase.util.log :as log]))

(def ^:private prompt-template
  "# Table: %s

## Description
%s

## Schema Details

| Column Name | Data Type | Description |
|------------|-----------|-------------|
%s

## Relationships
%s

## Example Query
%s")

(def ^:private sql-expert-prompt
  "You are an expert in SQL and PostgreSQL database management. Based on the provided context, generate an optimized and syntactically correct PostgreSQL query. Ensure the query follows best practices, including indexing considerations, performance optimization, and security measures (such as avoiding SQL injection vulnerabilities). If necessary, include joins, aggregations, filtering conditions, and ordering to achieve the desired outcome.

### Expected Output:
- A PostgreSQL query without any explanation—just pure SQL.
- If you need to include explanations, provide them as inline SQL comments using `--`.

Use the provided database structure for reference to answer the following user question:
%s

Answer with plain text dont add the ```sql or ``` at the beginning or end of your response.

If additional details are required to refine the query, ask clarifying questions in commented form using `--`.")

(defn- get-field-metadata
  "Get metadata for a field including name, type, and description."
  [field-id]
  (let [field (t2/select-one :model/Field :id field-id)
        values (when (field-values/field-should-have-field-values? field)
                (field-values/distinct-values field))]
    {:name (u/qualified-name (:name field))
     :type (u/qualified-name (:base_type field))
     :description (or (:description field) "No description available")
     :values values}))

(defn- format-field-row
  "Format a field's metadata as a markdown table row."
  [{:keys [name type description]}]
  (format "| %s | %s | %s |" name type description))

(defn- get-relationships
  "Get foreign key relationships for a table."
  [table-id]
  (let [fks (t2/select :model/Field
                       :table_id table-id
                       :semantic_type :type/FK)]
    (str/join "\n" 
              (for [fk fks
                    :let [target (t2/select-one :model/Field :id (:fk_target_field_id fk))
                          target-table (when target 
                                        (t2/select-one :model/Table :id (:table_id target)))]]
                (format "- %s references %s.%s"
                        (:name fk)
                        (:name target-table)
                        (:name target))))))

(defn- generate-example-query
  "Generate a simple example query for the table."
  [table-id]
  (let [table (t2/select-one :model/Table :id table-id)
        fields (t2/select :model/Field :table_id table-id :limit 3)
        field-names (map :name fields)]
    (format "```sql\nSELECT %s\nFROM %s\nLIMIT 5;\n```"
            (str/join ", " field-names)
            (:name table))))

(defn generate-table-prompt
  "Generate a prompt for a given table."
  [table-id]
  (let [table (t2/select-one :model/Table :id table-id)
        fields (t2/select :model/Field :table_id table-id)
        field-metadata (map get-field-metadata (map :id fields))
        field-rows (str/join "\n" (map format-field-row field-metadata))
        relationships (get-relationships table-id)
        example-query (generate-example-query table-id)]
    (format prompt-template
            (:name table)
            (or (:description table) "No description available")
            field-rows
            (if (str/blank? relationships) "No relationships found." relationships)
            example-query)))

(defn save-prompt-file!
  "Save the prompt content to a file and return the file path."
  [table-id prompt-content]
  (let [temp-file (doto (java.io.File/createTempFile
                         (format "prompt_%d_" table-id)
                         ".md"
                         (io/file (System/getProperty "java.io.tmpdir")))
                    (.deleteOnExit))
        file-path (.getAbsolutePath temp-file)]
    (spit temp-file prompt-content)
    {:file-path file-path}))

(defn generate-query!
  "Generate a SQL query based on a natural language question and database context"
  [question index-name]
  (try
    (let [embeddings (openai/create-embeddings question)
          context (pinecone/query index-name
                                embeddings
                                {:top-k 3
                                 :include-values false
                                 :include-metadata true})
          
          ;; Parse the response and extract texts
          matches (get context "matches")
          context-texts (map #(get-in % ["metadata" "text"]) matches)
          combined-context (str/join "\n\n---\n\n" context-texts)
          
          ;; Format prompt with context AND question
          prompt (format sql-expert-prompt 
                        (str "\nContext:\n" combined-context 
                             "\n\nQuestion:\n" question))
          
          ;; Generate SQL query
          generated-query (openai/generate-text prompt)]
      {:success true
       :query generated-query})
    (catch Exception e
      (log/error e "Error generating query")
      {:success false
       :error (tru "Error generating query")
       :details (.getMessage e)})))
