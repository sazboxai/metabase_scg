(ns metabase.models.prompt-file-llm
  "Entity definitions and helper functions for PromptFileLlm."
  (:require
   [metabase.models.interface :as mi]
   [metabase.util :as u]
   [toucan2.core :as t2]
   [methodical.core :as methodical]
   [metabase.db.connection :as mdb]
   [metabase.permissions.util :as perms-util]))

;; Add this to declare the model type
(def ^:private model-type :model/prompt-file-llm)

;; Use doto for model properties
(doto model-type
  (derive :metabase/model)
  (derive ::mi/read-policy.full-perms-for-perms-set))

;; Define transforms for automatic type conversion
(t2/deftransforms model-type
  {:status {:in keyword :out name}})

;; Define permissions
(defmethod mi/perms-objects-set model-type
  [prompt]
  (let [index-id (:index_database_llm_id prompt)
        database-id (t2/select-one-fn :database_id :model/index-database-llm :id index-id)]
    #{(str "/db/" database-id "/native/")}))

;; Define the model with table name
(methodical/defmethod t2/table-name model-type
  [_]  ;; Just use _ for unused param
  :prompt_file_llm)

;; Add after-select hook
(t2/define-after-select model-type
  [instance]  ;; Use instance consistently
  (assoc instance :model model-type))

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
