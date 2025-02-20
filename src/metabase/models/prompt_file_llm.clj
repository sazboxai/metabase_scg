(ns metabase.models.prompt-file-llm
  "Entity definitions and helper functions for PromptFileLlm."
  (:require
   [metabase.models.interface :as mi]
   [toucan2.core :as t2]
   [honey.sql :as sql]
   [honey.sql.helpers :as sqlh]
   [methodical.core :as methodical]))

(def model-type :model/prompt-file-llm)

(derive model-type ::mi/read-policy.full-perms-for-perms-set)

(methodical/defmethod t2/table-name model-type
  [_model]
  :prompt_file_llm)

(t2/deftransforms model-type
  {:id                   {:in  identity
                         :out identity}
   :index_database_llm_id {:in  identity
                          :out identity}
   :table_reference      {:in  identity
                         :out identity}})

(t2/define-after-select model-type
  [instance]
  (assoc instance :model model-type))

(doto model-type
  (derive :metabase/model))

(defmethod mi/can-read? model-type
  [_]
  true)

(defmethod mi/can-write? model-type
  [_]
  (mi/superuser?))

(defn get-all-prompts
  "Get all prompts from the database."
  []
  (t2/select model-type))

(defn create-prompt!
  "Creates a new prompt entry using Honey SQL."
  [{:keys [prompt index_database_llm_id table_reference]}]
  (let [query (-> (sqlh/insert-into :prompt_file_llm)
                  (sqlh/columns :prompt
                              :index_database_llm_id
                              :table_reference
                              :status
                              :created_at
                              :updated_at)
                  (sqlh/values [[prompt
                               index_database_llm_id
                               table_reference
                               "pending"
                               (sql/call :now)
                               (sql/call :now)]])
                  (sqlh/returning :*)
                  sql/format)]
    (first (t2/query query))))
