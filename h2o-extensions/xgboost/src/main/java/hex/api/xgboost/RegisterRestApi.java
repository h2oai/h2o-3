package hex.api.xgboost;

import hex.tree.xgboost.XGBoost;
import hex.tree.xgboost.XGBoostExtension;
import water.api.SchemaServer;

import java.util.Collections;
import java.util.List;

public class RegisterRestApi extends water.api.AbstractRegister {

  @Override
  public void register(String relativeResourcePath) {
    XGBoost xgBoostMB = new XGBoost(true);
    // Register XGBoost model builder REST API
    registerModelBuilder(xgBoostMB, SchemaServer.getStableVersion());
  }

  @Override
  public String getName() {
    return "XGBoost";
  }

  @Override
  public List<String> getRequiredCoreExtensions() {
    return Collections.singletonList(XGBoostExtension.NAME);
  }
}