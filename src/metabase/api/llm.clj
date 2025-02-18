(ns metabase.api.llm
  "/api/llm endpoints."
  (:require [metabase.api.common :as api]
            [metabase.api.macros :as api.macros]
            [metabase.models.index-database-llm :as index-db]
            [metabase.models.prompt-file-llm :as prompt-file]
            [metabase.models.database :as database]
            [metabase.llm.prompt-generator :as prompt-gen]
            [metabase.util.malli :as mu]
            [metabase.util.malli.schema :as ms]
            [metabase.util.i18n :refer [tru]]
            [metabase.util :as u]
            [toucan2.core :as t2]))

(def ^:private IndexCreateParams
  [:map
   [:description [:maybe ms/NonBlankString]]
   [:selectedTables [:sequential ms/PositiveInt]]])

(def ^:private DatabaseIdParam
  [:or
   [:int {:min 1}]
   [:string {:min 1}]])

(defn- verify-database-exists!
  "Verify that a database exists and is accessible."
  [db-id]
  (when-not (t2/exists? :model/Database :id db-id)
    (throw (ex-info (tru "Database {0} does not exist" db-id)
                   {:status-code 404
                    :id db-id})))
  (let [db (t2/select-one :model/Database :id db-id)]
    (api/write-check db)
    db))

(api.macros/defendpoint :post "/:database-id"
  "Create a new index for LLM prompts."
  [{{:keys [database-id]} :params :as request} :- [:map [:params [:map [:database-id DatabaseIdParam]]]]
   _query-params
   {:keys [description selectedTables] :as body} :- IndexCreateParams]
  ;; Log the entire request structure
  (println "\n[DEBUG] ===== REQUEST DETAILS =====")
  (println "[DEBUG] Full Request Map:" (pr-str request))
  (println "\n[DEBUG] ===== PARAMETER SOURCES =====")
  (println "[DEBUG] :route-params =" (pr-str (:route-params request)))
  (println "[DEBUG] :params =" (pr-str (:params request)))
  (println "[DEBUG] :query-params =" (pr-str (:query-params request)))
  (println "[DEBUG] :form-params =" (pr-str (:form-params request)))
  (println "[DEBUG] :body =" (pr-str (:body request)))
  
  (println "\n[DEBUG] ===== PARSED VALUES =====")
  (println "[DEBUG] database-id =" (pr-str database-id) "(type:" (type database-id) ")")
  (println "[DEBUG] body =" (pr-str body))
  
  (println "\n[DEBUG] ===== MIDDLEWARE INFO =====")
  (println "[DEBUG] Content-Type:" (get-in request [:headers "content-type"]))
  (println "[DEBUG] Character Encoding:" (get-in request [:character-encoding]))
  (println "[DEBUG] Request Method:" (:request-method request))
  
  (println "\n[DEBUG] ===== SCHEMA VALIDATION =====")
  (println "[DEBUG] Params Schema:" [:map [:params [:map [:database-id DatabaseIdParam]]]]
          "\nActual params:" (:params request))
  (println "[DEBUG] Body Schema:" IndexCreateParams "\nActual body:" body)
  
  (let [db-id (cond
                (integer? database-id) database-id
                (string? database-id) (try
                                      (Long/parseLong (str database-id))
                                      (catch Exception e
                                        (throw (ex-info (tru "Invalid database ID format")
                                                      {:status-code 400
                                                       :error (.getMessage e)
                                                       :id database-id}))))
                :else (throw (ex-info (tru "Invalid database ID type")
                                    {:status-code 400
                                     :type (type database-id)
                                     :id database-id})))]
    (println "[DEBUG] Parsed db-id:" db-id "type:" (type db-id))
    
    ;; Try to fetch and verify the database
    (let [db (verify-database-exists! db-id)]
      (println "[DEBUG] Found database:" db)
      
      ;; Create the index
      (let [index (t2/insert! :model/index-database-llm
                            {:database_id db-id
                             :description description
                             :selected_tables selectedTables
                             :status :pending
                             :created_by api/*current-user-id*})]
        {:id (:id index)
         :status :success
         :message "Index created successfully"}))))

(api.macros/defendpoint :post "/:index-id/generate-prompts"
  "Generate prompts for all selected tables in an index."
  [{{:keys [index-id]} :params}
   _query-params
   _body]
  (api/let-404 [index (t2/select-one :model/index-database-llm :id (Integer/parseInt index-id))]
    (api/write-check index)
    (let [selected-tables (:selected_tables index)]
      (doseq [table-id selected-tables]
        (let [prompt-content (prompt-gen/generate-table-prompt table-id)
              {:keys [file-path]} (prompt-gen/save-prompt-file! table-id prompt-content)
              prompt-file (t2/insert! :model/prompt-file-llm
                                   {:index_database_llm_id (Integer/parseInt index-id)
                                    :table_reference table-id
                                    :status :completed
                                    :file_path file-path})]
          (t2/update! :model/index-database-llm (Integer/parseInt index-id)
                     {:status :completed})))
      {:status :success
       :message "Prompts generated successfully"})))

(api.macros/defendpoint :get "/:index-id/prompts"
  "Get all generated prompts for an index."
  [{{:keys [index-id]} :params}]
  (api/let-404 [index (t2/select-one :model/index-database-llm :id (Integer/parseInt index-id))]
    (api/write-check index)
    (let [prompt-files (t2/select :model/prompt-file-llm :index_database_llm_id (Integer/parseInt index-id))]
      {:prompts prompt-files})))
