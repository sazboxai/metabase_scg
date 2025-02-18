(ns metabase.models.llm
  "Models for LLM-related functionality."
  (:require [metabase.models.interface :as mi]
            [metabase.util :as u]
            [toucan2.core :as t2]))

(def ^:private status-values
  "Valid status values for LLM operations."
  #{:pending :processing :completed :error})

(defn- valid-status?
  "Validate if a status value is valid."
  [status]
  (contains? status-values status))

;; Declare models to avoid unresolved symbol warnings
(declare IndexDatabaseLlm)
(declare PromptFileLlm)
(declare DocumentFileLlm)

(t2/deftransforms index_database_llm
  {:selected_tables mi/transform-json})

(t2/defmodel IndexDatabaseLlm :index_database_llm
  t2/IModel
  (types [_]
    {:selected_tables :json
     :status         :keyword}))

(t2/defmodel PromptFileLlm :prompt_file_llm
  t2/IModel
  (types [_]
    {:status :keyword}))

(t2/defmodel DocumentFileLlm :document_file_llm
  t2/IModel
  (types [_]
    {:status :keyword}))

(u/strict-extend IndexDatabaseLlm
  mi/IObjectPermissions
  (merge mi/IObjectPermissionsDefaults
         {:can-read?  (constantly true)
          :can-write? mi/superuser?})) 