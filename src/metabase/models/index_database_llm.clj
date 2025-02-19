(ns metabase.models.index-database-llm
  "Entity definitions and helper functions for IndexDatabaseLlm."
  (:require
   [metabase.models.interface :as mi]
   [metabase.util :as u]
   [toucan2.core :as t2]
   [methodical.core :as methodical]))

;; Add this to declare the model type
(def ^:private model-type :model/index-database-llm)

;; Derive from read policy
(derive model-type ::mi/read-policy.full-perms-for-perms-set)

;; Define the model with table name
(methodical/defmethod t2/table-name model-type
  [_]  ;; Just use _ for unused param
  :index_database_llm)

;; Define transforms for automatic type conversion
(t2/deftransforms model-type
  {:selected_tables {:in mi/transform-json :out mi/transform-json}
   :status         {:in keyword :out name}})

;; Define permissions
(defmethod mi/perms-objects-set model-type
  [{:keys [database_id]}]
  #{(str "/db/" database_id "/native/")})

;; Add after-select hook
(t2/define-after-select model-type
  [instance]  ;; Use instance consistently
  (assoc instance :model model-type))

;; Helper functions
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
