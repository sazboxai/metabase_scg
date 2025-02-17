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

  const handleSelectAll = () => {
    const allTableIds = tables.map(table => table.id);
    setSelectedTables(new Set(allTableIds));
  };

  const handleUnselectAll = () => {
    setSelectedTables(new Set());
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
      <div className="wrapper py4" style={{ padding: "0 2rem" }}>
        <div className="flex align-center mb2" style={{ padding: "0 1rem" }}>
          <h1 style={{ margin: 0 }}>{t`Select Database`}</h1>
          <div className="ml-auto">
            <Button
              primary
              disabled={selectedTables.size === 0}
              onClick={handleConfirm}
            >
              {t`Create Index`}
            </Button>
          </div>
        </div>

        <div className="bg-white bordered rounded shadowed">
          <div className="p4" style={{ padding: "2rem 3rem" }}>
            {isLoading && (
              <div className="flex justify-center align-center py4">
                <LoadingSpinner />
              </div>
            )}
            <div className="mb4">
              <div style={{ maxWidth: "900px" }}>
                <Select
                  value={selectedDb}
                  data={databases.map(db => ({
                    label: db.name || String(db.id),
                    value: String(db.id),
                  }))}
                  onChange={setSelectedDb}
                  placeholder={t`Choose a database...`}
                  className="block full rounded-lg border-2"
                  searchable
                  clearable
                  styles={{
                    root: { minHeight: "48px" },
                    input: { fontSize: "16px" },
                  }}
                />
              </div>
            </div>

            {selectedDb && (
              <>
                <div className="mb4">
                  <h2 className="text-dark mb3">{t`Database Description`}</h2>
                  <div style={{ maxWidth: "900px" }}>
                    <TextArea
                      value={description}
                      onChange={e => setDescription(e.target.value)}
                      placeholder={t`Enter a description of your database...`}
                      className="block full rounded-lg border-2 p3"
                      rows={6}
                    />
                  </div>
                </div>

                <div className="mb4">
                  <div className="flex justify-between items-center mb3">
                    <h2 className="text-dark">{t`Select Tables`}</h2>
                    <div className="flex gap-2">
                      <Button
                        small
                        borderless
                        onClick={handleSelectAll}
                        disabled={selectedTables.size === tables.length}
                      >
                        {t`Select All`}
                      </Button>
                      <Button
                        small
                        borderless
                        onClick={handleUnselectAll}
                        disabled={selectedTables.size === 0}
                      >
                        {t`Unselect All`}
                      </Button>
                    </div>
                  </div>
                  <table
                    className="AdminTable"
                    style={{
                      tableLayout: "fixed",
                      width: "100%",
                      maxWidth: "900px",
                    }}
                  >
                    <colgroup>
                      <col style={{ width: "80px" }} />
                      <col style={{ width: "auto" }} />
                      <col style={{ width: "120px" }} />
                    </colgroup>
                    <thead>
                      <tr>
                        <th></th>
                        <th className="text-wrap text-left">{t`Name`}</th>
                        <th className="text-center">{t`Select`}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {tables.map(table => (
                        <tr
                          key={table.id}
                          className="cursor-pointer"
                          onClick={() => handleTableToggle(table.id)}
                        >
                          <td></td>
                          <td className="text-wrap text-left">{table.name}</td>
                          <td className="text-center">
                            <input
                              type="checkbox"
                              checked={selectedTables.has(table.id)}
                              onChange={e => e.stopPropagation()}
                              onClick={e => e.stopPropagation()}
                              className="cursor-pointer"
                            />
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
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
