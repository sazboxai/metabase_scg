(ns metabase.models.llm
  "Models for LLM-related functionality."
  (:require
   [metabase.models.interface :as mi]
   [metabase.permissions.core :as perms]
   [metabase.util :as u]
   [methodical.core :as methodical]
   [toucan2.core :as t2]))

(def ^:private status-values
  "Valid status values for LLM operations."
  #{:pending :processing :completed :error})

(defn- valid-status?
  "Validate if a status value is valid."
  [status]
  (contains? status-values status))

;; Add this to declare the model type
(def ^:private model-type :model/index-database-llm)

;; Define the model with table name
(methodical/defmethod t2/table-name model-type
  [_]
  :index_database_llm)

;; Set up model properties
(doto model-type
  (derive :metabase/model)
  (derive ::mi/read-policy.full-perms-for-perms-set))

;; Define transforms for automatic type conversion
(t2/deftransforms model-type
  {:selected_tables {:in mi/transform-json :out mi/transform-json}
   :status         {:in keyword :out name}})

;; Add permissions
(defmethod mi/perms-objects-set model-type
  [{:keys [database_id] :as _model}]
  #{(str "/db/" database_id "/native/")})

;; Add after-select hook
(t2/define-after-select model-type
  [instance]
  (assoc instance :model model-type))

;; Define model types
(def model-types
  {:prompt-file-llm {:table :prompt_file_llm
                     :types {:status :keyword}}
   :document-file-llm {:table :document_file_llm
                       :types {:status :keyword}}})

;; Define the model
(def ^:export IndexDatabaseLlm
  "The IndexDatabaseLlm model."
  model-type)

;; Add this instead
(defmethod mi/can-read? model-type
  [_]
  true)

(defmethod mi/can-write? model-type
  [_]
  (mi/superuser?)) 