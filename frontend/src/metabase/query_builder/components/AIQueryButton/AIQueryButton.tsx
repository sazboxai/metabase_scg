import { t } from "ttag";
import { Icon } from "metabase/ui";
import Button from "metabase/core/components/Button";

interface AIQueryButtonProps {
  onGenerateQuery: () => void;
  isDisabled?: boolean;
  className?: string;
}

const AIQueryButton = ({ onGenerateQuery, isDisabled, className }: AIQueryButtonProps) => {
  return (
    <Button
      className={className}
      onlyIcon
      primary
      disabled={isDisabled}
      onClick={onGenerateQuery}
      icon="robot"
      iconSize={20}
      aria-label={t`Generate SQL with AI`}
    />
  );
};

export default AIQueryButton; 