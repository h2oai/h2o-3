package ai.h2o.automl.api;

import water.H2O;
import water.api.AbstractRegister;

public class Register extends AbstractRegister{
  @Override public void register(String relativeResourcePath) throws ClassNotFoundException {
    H2O.registerPOST("/AutoML", AutoMLHandler.class, "automl", "automatically build models");
  }
}
