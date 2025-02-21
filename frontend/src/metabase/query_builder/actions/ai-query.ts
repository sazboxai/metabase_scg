import { createThunkAction } from "metabase/lib/redux";
import { getQuestion } from "../selectors";
import { updateQuestion } from "./core/core";
import type { Dispatch, GetState } from "metabase-types/store";
import type NativeQuery from "metabase-lib/v1/queries/NativeQuery";

export const GENERATE_AI_QUERY = "metabase/qb/GENERATE_AI_QUERY";
export const generateAIQuery = createThunkAction(
  GENERATE_AI_QUERY,
  () => async (dispatch: Dispatch, getState: GetState) => {
    const question = getQuestion(getState());
    
    if (!question) {
      return;
    }

    const query = question.query();
    const nativeQuery = query as NativeQuery;
    const inputText = nativeQuery.queryText();
    
    if (!inputText.trim()) {
      // Handle empty input
      return;
    }

    try {
      const response = await fetch("/api/llm/generate-query", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          question: inputText,
          database_id: question.databaseId(),
          table_ids: question.tableIds()
        }),
      });

      if (!response.ok) {
        throw new Error("Failed to generate query");
      }
      
      const { query: generatedQuery } = await response.json();
      
      // Update the question with the generated query
      const updatedQuestion = question.setQuery(generatedQuery);
      dispatch(updateQuestion(updatedQuestion, { run: true }));
      
    } catch (error) {
      console.error("Error generating query:", error);
      // TODO: Show error notification
    }
  }
); 