package ai.h2o.automl;

import water.ExtensionManager;
import water.H2O;


// if we need to make the Algo list dynamic, we should just turn this enum into a class...
// implementation of AutoML.algo can be safely removed once we get rid of this interface: current purpose
// is to keep backward compatibility with {@link AutoML.algo}
public enum Algo {
  GLM,
  DRF,
  GBM,
  DeepLearning,
  StackedEnsemble,
  XGBoost() {
    private static final String DISTRIBUTED_XGBOOST_ENABLED = H2O.OptArgs.SYSTEM_PROP_PREFIX + "automl.xgboost.multinode.enabled";

    @Override
    boolean enabled() {
      return ExtensionManager.getInstance().isCoreExtensionEnabled(this.name())
              && !(H2O.CLOUD.size() > 1 && !Boolean.parseBoolean(System.getProperty(DISTRIBUTED_XGBOOST_ENABLED, "true")));
    }
  },
  ;

  String urlName() {
    return this.name().toLowerCase();
  }

  boolean enabled() {
    return true;
  }
}
