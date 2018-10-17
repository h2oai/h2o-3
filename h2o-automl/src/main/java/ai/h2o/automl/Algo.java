package ai.h2o.automl;

// if we need to make the Algo list dynamic, we should just turn this enum into a class...
// implementation of AutoML.algo can be safely removed once we get rid of this interface: current purpose
// is to keep backward compatibility with {@link AutoML.algo}
public enum Algo implements AutoML.algo {
  GLM,
  DRF,
  GBM,
  DeepLearning,
  StackedEnsemble,
  XGBoost,
  ;

  String urlName() {
    return this.name().toLowerCase();
  }
}
