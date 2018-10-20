package ai.h2o.automl;

// if we need to make the Algo list dynamic, we should just turn this enum into a class...
// implementation of AutoML.algo can be safely removed once we get rid of this interface: current purpose
// is to keep backward compatibility with {@link AutoML.algo}
public enum Algo implements AutoML.algo {
  GLM(hex.glm.GLM.class.getSimpleName().toLowerCase()),
  DRF(hex.tree.drf.DRF.class.getSimpleName().toLowerCase()),
  GBM(hex.tree.gbm.GBM.class.getSimpleName().toLowerCase()),
  DeepLearning(hex.deeplearning.DeepLearning.class.getSimpleName().toLowerCase()),
  StackedEnsemble(hex.ensemble.StackedEnsemble.class.getSimpleName().toLowerCase()),
  XGBoost(hex.tree.xgboost.XGBoost.class.getSimpleName().toLowerCase());

  public final String _baseName;

  Algo(String baseName) {
    _baseName = baseName;
  }

  String urlName() {
    return this.name().toLowerCase();
  }
}
