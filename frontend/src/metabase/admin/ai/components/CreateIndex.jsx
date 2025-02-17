import { useEffect, useState } from "react";
import { t } from "ttag";

import ErrorBoundary from "metabase/ErrorBoundary";
import AdminApp from "metabase/admin/app/components/AdminApp";
import LoadingSpinner from "metabase/components/LoadingSpinner";
import Button from "metabase/core/components/Button";
import TextArea from "metabase/core/components/TextArea";
import { GET } from "metabase/lib/api";
import { Select } from "metabase/ui";

// Constants
const INITIAL_STATE = {
  databases: [],
  selectedDb: null,
  description: "",
  tables: [],
  selectedTables: new Set(),
};

const CreateIndex = () => {
  const [databases, setDatabases] = useState(INITIAL_STATE.databases);
  const [selectedDb, setSelectedDb] = useState(INITIAL_STATE.selectedDb);
  const [description, setDescription] = useState(INITIAL_STATE.description);
  const [tables, setTables] = useState(INITIAL_STATE.tables);
  const [selectedTables, setSelectedTables] = useState(
    INITIAL_STATE.selectedTables,
  );
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    async function fetchDatabases() {
      setIsLoading(true);
      try {
        const response = await GET("/api/database")();
        const databaseArray = Array.isArray(response)
          ? response
          : response.data || [];
        setDatabases(databaseArray);
      } catch (error) {
        setDatabases([]);
      } finally {
        setIsLoading(false);
      }
    }
    fetchDatabases();
  }, []);

  useEffect(() => {
    async function fetchTables() {
      if (selectedDb) {
        setIsLoading(true);
        try {
          const response = await GET("/api/table")();
          const filteredTables = response.filter(
            table => table.db_id === Number(selectedDb),
          );
          setTables(filteredTables);
          setSelectedTables(new Set(filteredTables.map(table => table.id)));
        } catch (error) {
          setTables([]);
          setSelectedTables(new Set());
        } finally {
          setIsLoading(false);
        }
      }
    }
    fetchTables();
  }, [selectedDb]);

  const handleTableToggle = tableId => {
    const newSelected = new Set(selectedTables);
    if (newSelected.has(tableId)) {
      newSelected.delete(tableId);
    } else {
      newSelected.add(tableId);
    }
    setSelectedTables(newSelected);
  };

  const handleConfirm = async () => {
    const data = {
      database: selectedDb,
      description,
      selectedTables: Array.from(selectedTables),
    };
    // TODO: Add API endpoint to save index
    return data;
  };

  return (
    <AdminApp>
      <div className="wrapper py4">
        <div className="bg-white bordered rounded shadowed">
          <div className="p4">
            {isLoading && (
              <div className="flex justify-center align-center py4">
                <LoadingSpinner />
              </div>
            )}
            <div className="mb4">
              <h3 className="text-dark mb2">{t`Select Database`}</h3>
              <Select
                value={selectedDb}
                data={databases.map(db => ({
                  label: db.name || String(db.id),
                  value: String(db.id),
                }))}
                onChange={setSelectedDb}
                placeholder={t`Choose a database...`}
                className="block full"
                searchable
                clearable
              />
            </div>

            {selectedDb && (
              <>
                <div className="mb4">
                  <h3 className="text-dark mb2">{t`Database Description`}</h3>
                  <TextArea
                    value={description}
                    onChange={e => setDescription(e.target.value)}
                    placeholder={t`Enter a description of your database...`}
                    className="block full"
                    rows={4}
                  />
                </div>

                <div className="mb4">
                  <h3 className="text-dark mb2">{t`Select Tables`}</h3>
                  <div className="rounded border border-dark p2">
                    {tables.map(table => (
                      <label
                        key={table.id}
                        className="flex align-center p1 cursor-pointer hover-bg-light"
                      >
                        <input
                          type="checkbox"
                          checked={selectedTables.has(table.id)}
                          onChange={() => handleTableToggle(table.id)}
                          className="mr2"
                        />
                        <span className="text-bold">{table.name}</span>
                      </label>
                    ))}
                  </div>
                </div>

                <div>
                  <Button
                    primary
                    disabled={selectedTables.size === 0}
                    onClick={handleConfirm}
                    className="block"
                  >
                    {t`Create Index`}
                  </Button>
                </div>
              </>
            )}
          </div>
        </div>
      </div>
    </AdminApp>
  );
};

export default function CreateIndexWithErrorBoundary() {
  return (
    <ErrorBoundary>
      <CreateIndex />
    </ErrorBoundary>
  );
}
