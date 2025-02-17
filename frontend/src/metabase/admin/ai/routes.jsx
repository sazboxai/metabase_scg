import { IndexRedirect } from "react-router";
import { t } from "ttag";

import { Route } from "metabase/hoc/Title";

import CreateIndex from "./components/CreateIndex";

export const getRoutes = () => {
  return (
    <Route path="ai" title={t`AI Configuration`}>
      <IndexRedirect to="create-index" />
      <Route
        path="create-index"
        title={t`Create Index`}
        component={CreateIndex}
      />
    </Route>
  );
};
