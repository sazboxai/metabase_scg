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

(defsetting index-db-user
  (deferred-tru "Username for the Index Database")
  :visibility :admin
  :type :string
  :encryption :when-encryption-key-set)

(defsetting index-db-password
  (deferred-tru "Password for the Index Database")
  :visibility :admin
  :type :string
  :sensitive? true
  :encryption :when-encryption-key-set)

(defsetting index-db-name
  (deferred-tru "Database name for the Index Database")
  :visibility :admin
  :type :string
  :encryption :no)

(defsetting index-db-host
  (deferred-tru "Host for the Index Database")
  :visibility :admin
  :type :string
  :encryption :no)

(defsetting index-db-port
  (deferred-tru "Port for the Index Database")
  :visibility :admin
  :type :integer
  :encryption :no) 