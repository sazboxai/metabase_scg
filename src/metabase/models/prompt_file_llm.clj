(ns metabase.models.prompt-file-llm
  "Entity definitions and helper functions for PromptFileLlm."
  (:require
   [metabase.models.interface :as mi]
   [toucan2.core :as t2]
   [honey.sql :as sql]
   [honey.sql.helpers :as sqlh]
   [methodical.core :as methodical]
   [clojure.java.jdbc :as jdbc]
   [metabase.db :as db]))

(def model-type :model/prompt-file-llm)

(derive model-type ::mi/read-policy.full-perms-for-perms-set)

(methodical/defmethod t2/table-name model-type
  [model]
  :prompt_file_llm)

(t2/deftransforms model-type
  {:id                   {:in  identity
                         :out identity}
   :index_database_llm_id {:in  identity
                          :out identity}
   :table_reference      {:in  identity
                         :out identity}
   :generated_text       {:in  identity
                         :out identity}
   :error_message        {:in  identity
                         :out identity}
   :status              {:in  (fn [value] (if (string? value) (keyword value) value))
                        :out (fn [value] (if (keyword? value) (name value) value))}
   :prompt              {:in  str
                        :out identity}})

(t2/define-after-select model-type
  [{:keys [model] :as row}]
  (assoc row :model model-type))

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

(defn update-prompt!
  "Update prompt status and generated text using HoneySQL"
  [id {:keys [status generated_text] :as updates}]
  (let [query (-> (sqlh/update :prompt_file_llm)
                  (sqlh/set (cond-> {}
                             status (assoc :status status)
                             generated_text (assoc :generated_text generated_text)))
                  (sqlh/where [:= :id id])
                  sql/format)]
    (t2/query query)))
