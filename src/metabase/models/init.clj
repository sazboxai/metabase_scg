(ns metabase.models.init
  "Loaded for side effects on system launch."
  (:require
   [metabase.models.resolution]
   [metabase.models.interface]
   [metabase.models.prompt-file-llm]
   [metabase.models.index-database-llm]
   [metabase.models.llm]))
