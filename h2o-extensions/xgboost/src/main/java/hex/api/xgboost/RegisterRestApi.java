package hex.api.xgboost;

import hex.tree.xgboost.XGBoost;
import hex.tree.xgboost.XGBoostExtension;
import hex.tree.xgboost.remote.RemoteXGBoostHandler;
import water.ExtensionManager;
import water.api.AlgoAbstractRegister;
import water.api.RestApiContext;
import water.api.SchemaServer;

import java.util.Collections;
import java.util.List;

public class RegisterRestApi extends AlgoAbstractRegister {

  @Override
  public void registerEndPoints(RestApiContext context) {
    XGBoostExtension ext = (XGBoostExtension) ExtensionManager.getInstance().getCoreExtension(XGBoostExtension.NAME);
    ext.logNativeLibInfo();
    XGBoost xgBoostMB = new XGBoost(true);
    int version = SchemaServer.getStableVersion();
    // Register XGBoost model builder REST API
    registerModelBuilder(context, xgBoostMB, version);
    // Register Remote XGBoost execution
    context.registerEndpoint(
        "remote_xgb", "POST /3/XGBoostExecutor", 
        RemoteXGBoostHandler.class, "exec",
        "Remote XGBoost execution"
    );
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
