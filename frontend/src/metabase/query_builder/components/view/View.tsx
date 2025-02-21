import { useCallback } from "react";
import { useDispatch } from "metabase/lib/redux";
import { generateAIQuery } from "../../actions/ai-query";
import AIQueryButton from "../AIQueryButton/AIQueryButton";

// Add to the props interface:
interface ViewProps {
  isNativeEditorOpen: boolean;
  isShowingTemplateTagsEditor: boolean;
  isShowingDataReference: boolean;
  isShowingSnippetSidebar: boolean;
  // ... other existing props
}

const View = ({ 
  isNativeEditorOpen,
  isShowingTemplateTagsEditor,
  isShowingDataReference,
  isShowingSnippetSidebar,
  ...props 
}: ViewProps) => {
  const dispatch = useDispatch();

  const handleGenerateQuery = useCallback(() => {
    dispatch(generateAIQuery());
  }, [dispatch]);

  const renderNativeEditorSidebar = () => {
    if (!isNativeEditorOpen) {
      return null;
    }

    return (
      <div className="border-left">
        <div className="p2">
          <AIQueryButton onGenerateQuery={handleGenerateQuery} />
        </div>
        {/* Existing sidebar content */}
        {isShowingTemplateTagsEditor && (
          <div className="p2 border-top">
            {/* Template tags editor */}
          </div>
        )}
        {isShowingDataReference && (
          <div className="p2 border-top">
            {/* Data reference */}
          </div>
        )}
        {isShowingSnippetSidebar && (
          <div className="p2 border-top">
            {/* Snippet sidebar */}
          </div>
        )}
      </div>
    );
  };

  return (
    <div className="flex flex-column h-full">
      {/* Main content */}
      <div className="flex flex-row flex-full">
        <div className="flex-full relative">
          {/* Query editor */}
        </div>
        {renderNativeEditorSidebar()}
      </div>
    </div>
  );
};

export default View; 