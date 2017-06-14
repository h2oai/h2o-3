package water.api;

import water.H2O;

import java.io.File;

public class RegisterResourceRoots extends AbstractRegister {
  @Override
  public void register(String relativeResourcePath) {
    H2O.registerResourceRoot(new File(relativeResourcePath + File.separator + "h2o-web/src/main/resources/www"));
    H2O.registerResourceRoot(new File(relativeResourcePath + File.separator + "h2o-core/src/main/resources/www"));
  }
}
