package ai.h2o.automl;

import water.ExtensionManager;
import water.H2O;

import static water.util.OSUtils.isLinux;


// if we need to make the Algo list dynamic, we should just turn this enum into a class...
// implementation of AutoML.algo can be safely removed once we get rid of this interface: current purpose
// is to keep backward compatibility with {@link AutoML.algo}
public enum Algo implements IAlgo {
  GLM,
  DRF,
  GBM,
  DeepLearning,
  StackedEnsemble,
  XGBoost() {
    private static final String DISTRIBUTED_XGBOOST_ENABLED = H2O.OptArgs.SYSTEM_PROP_PREFIX + "automl.xgboost.multinode.enabled";

    @Override
    public boolean enabled() {
      // on single node, XGBoost is enabled by default if the extension is enabled.
      // on multinode, the same condition applies, but only on Linux by default: needs to be activated explicitly for other platforms.
      boolean enabledOnMultinode = Boolean.parseBoolean(System.getProperty(DISTRIBUTED_XGBOOST_ENABLED, isLinux() ? "true" : "false"));
      return ExtensionManager.getInstance().isCoreExtensionEnabled(this.name()) && (H2O.CLOUD.size() == 1 || enabledOnMultinode);
    }
  },
  ;
}
