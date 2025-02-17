(ns metabase.models.ai-settings
  (:require [metabase.models.setting :refer [defsetting]]
            [metabase.util.i18n :refer [deferred-tru]]))

;; AI Configuration Settings
(defsetting openai-api-key
  (deferred-tru "OpenAI API Key for AI features")
  :visibility :admin
  :type :string
  :sensitive? true
  :encryption :when-encryption-key-set)

(defsetting pinecone-api-key
  (deferred-tru "Pinecone API Key for vector database")
  :visibility :admin
  :type :string
  :sensitive? true
  :encryption :when-encryption-key-set)