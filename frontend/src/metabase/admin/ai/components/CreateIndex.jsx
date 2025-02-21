import { useEffect, useState } from "react";
import { t } from "ttag";
import { push } from "react-router-redux";

import ErrorBoundary from "metabase/ErrorBoundary";
import AdminApp from "metabase/admin/app/components/AdminApp";
import LoadingSpinner from "metabase/components/LoadingSpinner";
import Button from "metabase/core/components/Button";
import TextArea from "metabase/core/components/TextArea";
import { GET } from "metabase/lib/api";
import { Select, Progress } from "metabase/ui";

// Constants
const INITIAL_STATE = {
  databases: [],
  selectedDb: null,
  description: "",
  tables: [],
  selectedTables: new Set(),
  isGenerating: false,
  generatedPrompts: [],
  progress: 0,
  error: null,
};

const CreateIndex = () => {
  const [state, setState] = useState(INITIAL_STATE);
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    async function fetchDatabases() {
      setIsLoading(true);
      try {
        const response = await GET("/api/database")();
        const databaseArray = Array.isArray(response)
          ? response
          : response.data || [];
        setState(prev => ({ ...prev, databases: databaseArray }));
      } catch (error) {
        setState(prev => ({ ...prev, databases: [] }));
      } finally {
        setIsLoading(false);
      }
    }
    fetchDatabases();
  }, []);

  useEffect(() => {
    async function fetchTables() {
      if (state.selectedDb) {
        setIsLoading(true);
        try {
          const response = await GET("/api/table")();
          const filteredTables = response.filter(
            table => table.db_id === Number(state.selectedDb),
          );
          setState(prev => ({ ...prev, tables: filteredTables, selectedTables: new Set(filteredTables.map(table => table.id)) }));
        } catch (error) {
          setState(prev => ({ ...prev, tables: [], selectedTables: new Set() }));
        } finally {
          setIsLoading(false);
        }
      }
    }
    fetchTables();
  }, [state.selectedDb]);

  const handleTableToggle = tableId => {
    const newSelected = new Set(state.selectedTables);
    if (newSelected.has(tableId)) {
      newSelected.delete(tableId);
    } else {
      newSelected.add(tableId);
    }
    setState(prev => ({ ...prev, selectedTables: newSelected }));
  };

  const handleSelectAll = () => {
    const allTableIds = state.tables.map(table => table.id);
    setState(prev => ({ ...prev, selectedTables: new Set(allTableIds) }));
  };

  const handleUnselectAll = () => {
    setState(prev => ({ ...prev, selectedTables: new Set() }));
  };

  const createLLMIndex = async (pineconeIndexId) => {
    try {
      if (!state.description.trim()) {
        throw new Error('Description is required');
      }

      const response = await fetch('/api/llm', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          database_id: Number(state.selectedDb),
          description: state.description.trim(),
          selected_tables: Array.from(state.selectedTables).map(Number),
          pinecone_index_id: pineconeIndexId
        }),
      });

      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.errors?.description || 'Failed to create LLM index');
      }

      const data = await response.json();
      return data.data.id;
    } catch (error) {
      console.error('Error creating LLM index:', error);
      setState(prev => ({
        ...prev,
        error: error.message
      }));
      throw error;
    }
  };

  const createPineconeIndex = async () => {
    try {
      const response = await fetch('/api/llm/pinecone/index', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          database_id: Number(state.selectedDb)
        })
      });

      const data = await response.json();

      // If we get a 409 (Already Exists), that's fine - we can use the existing index
      if (response.status === 409) {
        return `metabase-index-${state.selectedDb}`; // Return the expected index name
      }

      if (!response.ok && response.status !== 409) {
        throw new Error(data.message || 'Failed to create Pinecone index');
      }

      return data.data.index_name;
    } catch (error) {
      console.error('Error creating/getting Pinecone index:', error);
      throw new Error(`Failed to create/get Pinecone index: ${error.message}`);
    }
  };

  const checkPineconeIndexStatus = async (indexName) => {
    try {
      const response = await fetch(`/api/llm/pinecone/index/${indexName}/status`);
      if (!response.ok) {
        throw new Error('Failed to check index status');
      }
      const data = await response.json();
      return data.ready;
    } catch (error) {
      console.error('Error checking Pinecone index status:', error);
      return false;
    }
  };

  const createPromptForTable = async (indexId, tableId) => {
    try {
      // Get database metadata which includes table information
      const dbMetadata = await GET(`/api/database/${state.selectedDb}/metadata`)();
      
      // Find the specific table metadata from the response
      const tableMetadata = dbMetadata.tables.find(table => table.id === tableId);
      
      if (!tableMetadata) {
        throw new Error(`Table metadata not found for table ${tableId}`);
      }

      // Format the metadata into a structured prompt
      const formattedPrompt = formatTableMetadata(tableMetadata);

      const response = await fetch('/api/llm/prompt', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          name: tableMetadata.name,
          description: tableMetadata.description || `Prompt for table ${tableMetadata.name}`,
          prompt: formattedPrompt,
          index_database_llm_id: indexId,
          table_reference: tableId,
        }),
      });

      if (!response.ok) {
        throw new Error(`Failed to create prompt for table ${tableId}`);
      }

      return await response.json();
    } catch (error) {
      console.error(`Error creating prompt for table ${tableId}:`, error);
      throw error;
    }
  };

  // Helper function to format table metadata into a prompt
  const formatTableMetadata = (tableMetadata) => {
    // Generate schema details table
    const schemaTable = [
      '| Column Name | Data Type | Description |',
      '|------------|-----------|-------------|',
      ...tableMetadata.fields.map(field => 
        `| ${field.name} | ${field.base_type} | ${field.description || 'No description available'} |`
      )
    ].join('\n');

    // Generate relationships section based on semantic types
    const relationships = tableMetadata.fields
      .filter(field => field.semantic_type?.includes('FK'))
      .map(field => `- **\`${field.name}\`** → References **${field.target?.table?.display_name || 'Unknown'}** table`)
      .join('\n');

    // Create example query based on table structure
    const exampleQuery = `SELECT ${tableMetadata.fields.slice(0, 3).map(f => f.name).join(', ')}
FROM ${tableMetadata.name}
LIMIT 5;`;

    // Format the complete markdown document
    const markdown = `# Table: ${tableMetadata.name}

## Description
${tableMetadata.description || `The \`${tableMetadata.name}\` table in the ${tableMetadata.schema || 'default'} schema.`}

## Schema Details
${schemaTable}

${relationships ? `## Relationships\n${relationships}\n` : ''}

## Example Query
\`\`\`sql
${exampleQuery}
\`\`\``;

    return markdown;
  };

  const handleGeneratePrompts = async () => {
    setState(prev => ({ 
      ...prev, 
      isGenerating: true, 
      error: null, 
      progress: 0,
      status: 'Preparing Pinecone index...' 
    }));

    try {
      // Step 1: Get or Create Pinecone Index
      const pineconeIndexId = await createPineconeIndex();

      // Step 2: Create the LLM index with Pinecone index ID
      setState(prev => ({ ...prev, status: 'Creating LLM index...' }));
      const indexId = await createLLMIndex(pineconeIndexId);

      // Step 3: Generate prompts for each selected table
      setState(prev => ({ ...prev, status: 'Generating prompts...' }));
      const selectedTablesArray = Array.from(state.selectedTables);
      const totalTables = selectedTablesArray.length;
      const prompts = [];

      for (let i = 0; i < selectedTablesArray.length; i++) {
        const tableId = selectedTablesArray[i];
        const prompt = await createPromptForTable(indexId, tableId);
        prompts.push(prompt);
        
        setState(prev => ({
          ...prev,
          progress: ((i + 1) / totalTables) * 100,
          generatedPrompts: [...prev.generatedPrompts, prompt],
        }));
      }

      // Step 4: Fetch all generated prompts
      const response = await fetch('/api/llm/prompt');
      const allPrompts = await response.json();
      
      setState(prev => ({
        ...prev,
        isGenerating: false,
        generatedPrompts: allPrompts.data,
        progress: 100,
        status: 'Complete'
      }));

    } catch (error) {
      setState(prev => ({
        ...prev,
        isGenerating: false,
        error: error.message,
        status: 'Failed'
      }));
    }
  };

  // Add a helper function to get prompt status
  const getPromptStatus = (tableId) => {
    if (!state.selectedTables.has(tableId)) {
      return { status: 'Not Selected', className: 'text-medium' };
    }
    
    const prompt = state.generatedPrompts.find(p => p.table_reference === tableId);
    if (prompt) {
      return { status: 'Generated', className: 'text-success' };
    }
    
    if (state.error) {
      return { status: 'Failed', className: 'text-error' };
    }
    
    return { status: 'Pending', className: 'text-medium' };
  };

  return (
    <AdminApp>
      <div className="wrapper py4" style={{ padding: "0 2rem" }}>
        <div className="flex align-center mb2" style={{ padding: "0 1rem" }}>
          <h1 style={{ margin: 0 }}>{t`Generate Database Prompts`}</h1>
          <div className="ml-auto">
            <Button
              primary
              disabled={state.selectedTables.size === 0 || state.isGenerating}
              onClick={handleGeneratePrompts}
            >
              {state.isGenerating ? t`Generating...` : t`Generate Prompts`}
            </Button>
          </div>
        </div>

        <div className="bg-white bordered rounded shadowed">
          <div className="p4" style={{ padding: "2rem 3rem" }}>
            {(isLoading || state.isGenerating) && (
              <div className="flex justify-center align-center py4">
                <div className="text-centered">
                  <LoadingSpinner />
                  <p className="text-medium mt2">
                    {state.isGenerating ? t`Generating prompts...` : t`Loading...`}
                  </p>
                </div>
                <LoadingSpinner />
              </div>
            )}
            <div className="mb4">
              <div style={{ maxWidth: "900px" }}>
                <Select
                  value={state.selectedDb ? String(state.selectedDb) : null}
                  data={state.databases.map(db => ({
                    label: db.name || String(db.id),
                    value: String(db.id),
                  }))}
                  onChange={selectedDb => setState(prev => ({ ...prev, selectedDb }))}
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

            {state.selectedDb && (
              <>
                <div className="mb4">
                  <h2 className="text-dark mb3">{t`Database Description`}</h2>
                  <div style={{ maxWidth: "900px" }}>
                    <TextArea
                      value={state.description}
                      onChange={e => setState(prev => ({ ...prev, description: e.target.value }))}
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
                        disabled={state.selectedTables.size === state.tables.length}
                      >
                        {t`Select All`}
                      </Button>
                      <Button
                        small
                        borderless
                        onClick={handleUnselectAll}
                        disabled={state.selectedTables.size === 0}
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
                      <col style={{ width: "150px" }} />
                    </colgroup>
                    <thead>
                      <tr>
                        <th></th>
                        <th className="text-wrap text-left">{t`Name`}</th>
                        <th className="text-center">{t`Select`}</th>
                        <th className="text-center">{t`Prompt Status`}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {state.tables.map(table => {
                        const { status, className } = getPromptStatus(table.id);
                        return (
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
                                checked={state.selectedTables.has(table.id)}
                                onChange={e => e.stopPropagation()}
                                onClick={e => e.stopPropagation()}
                                className="cursor-pointer"
                              />
                            </td>
                            <td className={`text-center ${className}`}>
                              {status}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </>
            )}

            {state.isGenerating && (
              <div className="my4">
                <h3>{t`Generating Prompts...`}</h3>
                <Progress value={state.progress} />
                <p className="text-medium mt2">
                  {t`Generated ${state.generatedPrompts.length} prompts out of ${state.selectedTables.size} tables`}
                </p>
              </div>
            )}

            {state.error && (
              <div className="bg-error text-white p2 rounded my2">
                {state.error}
              </div>
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
