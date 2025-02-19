(ns metabase.api.llm
  "API endpoints for LLM functionality."
  (:require
   [metabase.api.common :as api]
   [metabase.api.macros :as api.macros]
   [metabase.models.index-database-llm :as index-database-llm]
   [metabase.util.malli :as mu]
   [metabase.util.malli.schema :as ms]
   [metabase.util.i18n :refer [tru]]
   [metabase.util :as u]
   [toucan2.core :as t2]
   [metabase.util.log :as log]))

(log/info "Loading LLM API namespace")

(api.macros/defendpoint :get "/"
  "Get all LLM indexes."
  []
  (log/info "List endpoint called")
  (try 
    (api/check-superuser)
    {:data (t2/select :model/index-database-llm)}
    (catch Exception e
      (log/error e "Error fetching LLM indexes")
      {:error (tru "Error fetching LLM indexes")
       :details (.getMessage e)})))

;; Export routes using ns-handler
(def routes (api.macros/ns-handler))
