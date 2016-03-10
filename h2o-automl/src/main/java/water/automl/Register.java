package water.automl;

import water.H2O;
import water.api.AbstractRegister;

public class Register extends AbstractRegister{
  @Override public void register(String relativeResourcePath) throws ClassNotFoundException {
    H2O.registerPOST("/99/AutoML", AutoMLHandler.class, "automl", "automatically build models");
  }
}
