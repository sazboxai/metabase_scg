(ns metabase.models.document-file-llm
  "Entity definitions and helper functions for DocumentFileLlm."
  (:require [metabase.models.interface :as mi]
            [metabase.util :as u]
            [toucan2.core :as t2]
            [methodical.core :as m]))

(derive :model/document-file-llm ::base-model)

;; Define the model with table name
(m/defmethod t2/table-name :model/document-file-llm
  [_model]
  :document_file_llm)

;; Define transforms for automatic type conversion
(t2/deftransforms :model/document-file-llm
  {:status      {:in name :out keyword}
   :created_at  {:in t2/parse-datetime :out identity}
   :updated_at  {:in t2/parse-datetime :out identity}})

;; Define permissions
(m/defmethod mi/perms-objects-set :model/document-file-llm
  [_model prompt-file-id]
  #{(perms/adhoc-native-query-path prompt-file-id)})

;; Add after-select hook for any additional processing
(t2/define-after-select :model/document-file-llm
  [instance]
  (assoc instance
         :model :model/document-file-llm))

(defn create-document-file!
  "Creates a new DocumentFileLlm entry."
  [{:keys [prompt-file-id] :as document-data}]
  (db/insert! DocumentFileLlm
              (merge
               {:status :pending
                :prompt_file_id prompt-file-id}
               (dissoc document-data :prompt-file-id))))

(defn update-document-status!
  "Updates the status of a DocumentFileLlm entry."
  [id new-status]
  (db/update! DocumentFileLlm id
              {:status new-status
               :updated_at (java.time.ZonedDateTime/now)}))
