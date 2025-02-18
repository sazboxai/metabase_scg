(ns metabase.models.index-database-llm
  "Entity definitions and helper functions for IndexDatabaseLlm."
  (:require [metabase.models.interface :as mi]
            [metabase.util :as u]
            [toucan2.core :as t2]
            [methodical.core :as methodical]
            [metabase.permissions.util :as perms-util]))

(derive :model/index-database-llm ::mi/base-model)

;; Define the model with table name
(methodical/defmethod t2/table-name :model/index-database-llm
  [_model]
  :index_database_llm)

;; Define transforms for automatic type conversion
(t2/deftransforms :model/index-database-llm
  {:status           {:in name :out keyword}
   :selected_tables  :json})

;; Define permissions
(defmethod mi/perms-objects-set :model/index-database-llm
  [instance _read-or-write]
  (let [database-id (:database_id instance)]
    #{(str "/db/" database-id "/native/")}))

;; Add after-select hook for any additional processing
(t2/define-after-select :model/index-database-llm
  [instance]
  (assoc instance
         :model :model/index-database-llm))

(defn create-index-database-llm!
  "Creates a new IndexDatabaseLlm entry."
  [{:keys [database-id description selected-tables created-by] :as index-data}]
  (t2/insert! :model/index-database-llm
              (merge
               {:status :pending
                :database_id database-id
                :description description
                :selected_tables selected-tables
                :created_by created-by}
               (dissoc index-data :database-id :description :selected-tables :created-by))))

(defn update-index-status!
  "Updates the status of an IndexDatabaseLlm entry."
  [id new-status & [error-message]]
  (t2/update! :model/index-database-llm id
              (merge
               {:status new-status}
               (when error-message
                 {:error_message error-message}))))
