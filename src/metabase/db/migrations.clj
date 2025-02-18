(ns metabase.db.migrations
  "Clojure-land data migration definitions and fns for running them.
   These migrations are all ran once when Metabase is first launched, except when transferring data from an existing H2 to
   MySQL/Postgres database.
   If you need to make data model changes, they need to be added both here *and* to the entities that define them."
  (:require
   ;; ...other requires...
   [metabase.db.migrations.v50-00 :as v50.00]))

;; In the migrations vector, add:
(def ^:private migrations
  [;; ...other migrations...
   ["v55.00.01" "Add index database llm table"]
   ["v55.00.02" "Add llm prompt and document tables"]]) 