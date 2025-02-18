(ns metabase.llm.prompt-generator
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [metabase.models.table :as table]
            [metabase.models.field :as field]
            [metabase.models.field-values :as field-values]
            [metabase.util :as u]
            [metabase.util.date-2 :as u.date]
            [toucan2.core :as t2]))

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
