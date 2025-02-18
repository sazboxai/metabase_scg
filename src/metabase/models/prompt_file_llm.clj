(ns metabase.models.prompt-file-llm
  "Entity definitions and helper functions for PromptFileLlm."
  (:require [metabase.models.interface :as mi]
            [metabase.util :as u]
            [toucan2.core :as t2]
            [methodical.core :as methodical]
            [metabase.permissions.util :as perms-util]
            [metabase.db.connection :as mdb]))

(derive :model/prompt-file-llm ::mi/base-model)

;; Define the model with table name
(methodical/defmethod t2/table-name :model/prompt-file-llm
  [_model]
  :prompt_file_llm)

;; Define transforms for automatic type conversion
(t2/deftransforms :model/prompt-file-llm
  {:status      {:in name :out keyword}})

;; Define permissions
(defmethod mi/perms-objects-set :model/prompt-file-llm
  [_model index-database-llm-id]
  #{(str "/db/" index-database-llm-id "/native/")})

;; Add after-select hook for any additional processing
(t2/define-after-select :model/prompt-file-llm
  [instance]
  (assoc instance
         :model :model/prompt-file-llm))

(defn create-prompt-file!
  "Creates a new PromptFileLlm entry."
  [{:keys [index-database-llm-id table-reference] :as prompt-data}]
  (t2/insert! :model/prompt-file-llm
              (merge
               {:status :pending
                :index_database_llm_id index-database-llm-id
                :table_reference table-reference}
               (dissoc prompt-data :index-database-llm-id :table-reference))))

(defn update-prompt-status!
  "Updates the status of a PromptFileLlm entry."
  [id new-status]
  (t2/update! :model/prompt-file-llm id
              {:status new-status
               :updated_at (java.time.ZonedDateTime/now)}))
