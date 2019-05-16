package hex;

import water.H2O;

public abstract class FeatureTransformer {

  protected FeatureTransformerWriter getWriter() {
    throw H2O.unimpl("Writer is not available for " + getClass().getSimpleName() + " feature transformer.");
  }
}
