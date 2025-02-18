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
  isGenerating: false,
  generatedPrompts: [],
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
  const [isGenerating, setIsGenerating] = useState(INITIAL_STATE.isGenerating);
  const [generatedPrompts, setGeneratedPrompts] = useState(INITIAL_STATE.generatedPrompts);
  const [error, setError] = useState(null);

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
    setIsGenerating(true);
    setError(null);
    try {
      // First create the index
      if (!selectedDb) {
        throw new Error('Please select a database first');
      }

      console.log('Selected DB:', selectedDb, 'type:', typeof selectedDb);
      
      // Convert string ID back to number for the API
      const dbId = parseInt(selectedDb, 10);
      if (isNaN(dbId)) {
        throw new Error('Invalid database ID');
      }
      console.log('Parsed database ID:', dbId, 'type:', typeof dbId);

      // Ring/Compojure will put the database-id from the URL into :params
      const requestBody = {
        description: description || '',
        selectedTables: Array.from(selectedTables).map(id => Number(id))
      };
      
      console.log('Sending request to:', `/api/llm/${dbId}`);
      console.log('Request body:', requestBody);

      const createResponse = await fetch(`/api/llm/${dbId}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(requestBody),
      });
      
      if (!createResponse.ok) {
        const errorText = await createResponse.text();
        console.error('Server error:', errorText);
        throw new Error(`Failed to create index: ${errorText}`);
      }
      
      const { id } = await createResponse.json();
      
      // Then generate prompts
      const generateResponse = await fetch(`/api/llm/${id}/generate-prompts`, {
        method: 'POST',
      });
      
      if (!generateResponse.ok) throw new Error('Failed to generate prompts');
      
      // Get generated prompts
      const promptsResponse = await fetch(`/api/llm/${id}/prompts`);
      if (!promptsResponse.ok) throw new Error('Failed to fetch prompts');
      
      const { prompts } = await promptsResponse.json();
      setGeneratedPrompts(prompts);
    } catch (err) {
      setError(err.message);
    } finally {
      setIsGenerating(false);
    }
  };

  return (
    <AdminApp>
      <div className="wrapper py4" style={{ padding: "0 2rem" }}>
        <div className="flex align-center mb2" style={{ padding: "0 1rem" }}>
          <h1 style={{ margin: 0 }}>{t`Generate Database Prompts`}</h1>
          <div className="ml-auto">
            <Button
              primary
              disabled={selectedTables.size === 0 || isGenerating}
              onClick={handleConfirm}
            >
              {isGenerating ? t`Generating...` : t`Generate Prompts`}
            </Button>
          </div>
        </div>

        <div className="bg-white bordered rounded shadowed">
          <div className="p4" style={{ padding: "2rem 3rem" }}>
            {(isLoading || isGenerating) && (
              <div className="flex justify-center align-center py4">
                <div className="text-centered">
                  <LoadingSpinner />
                  <p className="text-medium mt2">
                    {isGenerating ? t`Generating prompts...` : t`Loading...`}
                  </p>
                </div>
                <LoadingSpinner />
              </div>
            )}
            <div className="mb4">
              <div style={{ maxWidth: "900px" }}>
                <Select
                  value={selectedDb ? String(selectedDb) : null}
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
