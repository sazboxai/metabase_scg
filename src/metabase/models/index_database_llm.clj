(ns metabase.models.index-database-llm
  "Entity definitions and helper functions for IndexDatabaseLlm."
  (:require
   [metabase.models.interface :as mi]
   [metabase.util :as u]
   [toucan2.core :as t2]
   [honey.sql :as sql]
   [honey.sql.helpers :as sqlh]
   [cheshire.core :as json]
   [methodical.core :as methodical]))

;; Add this to declare the model type
(def ^:private model-type :model/index-database-llm)

;; Derive from read policy
(derive model-type ::mi/read-policy.full-perms-for-perms-set)

;; Define the model with table name
(methodical/defmethod t2/table-name model-type
  [model]  ;; Remove underscore since clj-kondo expects declared params
  :index_database_llm)

;; Define transforms for automatic type conversion
(t2/deftransforms model-type
  {:selected_tables {:in  mi/transform-json
                    :out mi/transform-json}})  ;; Remove status transform since we're using Honey SQL

;; Define permissions
(defmethod mi/perms-objects-set model-type
  [{:keys [database_id]}]
  #{(str "/db/" database_id "/native/")})

;; Add after-select hook
(t2/define-after-select model-type
  [{:keys [model] :as row}]  ;; Use proper destructuring to make linter happy
  (assoc row :model model-type))

;; Helper functions
(defn create-index-database-llm!
  "Creates a new IndexDatabaseLlm entry."
  [{:keys [database-id description selected-tables created-by pinecone-index-id]}]
  (let [json-tables (json/generate-string selected-tables)
        query (-> (sqlh/insert-into :index_database_llm)
                  (sqlh/columns :database_id 
                              :description 
                              :selected_tables 
                              :created_by 
                              :status
                              :created_at
                              :updated_at
                              :pinecone_index_id)  ;; Add new column
                  (sqlh/values [[database-id 
                               description 
                               (sql/call :cast json-tables :json)
                               created-by 
                               "pending"
                               (sql/call :now)
                               (sql/call :now)
                               pinecone-index-id]])  ;; Add new value
                  (sqlh/returning :*)
                  sql/format)]
    (first (t2/query query))))

(defn update-index-status!
  "Updates the status of an IndexDatabaseLlm entry."
  [id new-status & [error-message]]
  (t2/update! :model/index-database-llm id
              (merge
               {:status new-status}
               (when error-message
                 {:error_message error-message}))))
