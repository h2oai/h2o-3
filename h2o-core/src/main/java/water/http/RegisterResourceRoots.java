package water.http;

import water.H2O;
import water.api.AbstractRegister;

import java.io.File;

public class RegisterResourceRoots extends AbstractRegister {
  @Override
  public void register(String relativeResourcePath) {
    H2O.registerResourceRoot(new File(relativeResourcePath + File.separator + "h2o-web/src/main/resources/www"));
    H2O.registerResourceRoot(new File(relativeResourcePath + File.separator + "h2o-core/src/main/resources/www"));
  }
}
