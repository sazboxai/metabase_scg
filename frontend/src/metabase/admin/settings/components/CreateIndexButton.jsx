import { t } from "ttag";

import Button from "metabase/core/components/Button";

const CreateIndexButton = () => (
  <Button
    primary
    onClick={() => {
      window.location.href = "/admin/ai/create-index";
    }}
  >
    {t`Create Index`}
  </Button>
);

export default CreateIndexButton;
