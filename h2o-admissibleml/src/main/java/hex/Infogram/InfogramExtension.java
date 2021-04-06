package hex.Infogram;

import hex.tree.xgboost.XGBoostExtension;
import org.apache.log4j.Logger;
import water.AbstractH2OExtension;

public class InfogramExtension extends AbstractH2OExtension {
  private static final Logger LOG = Logger.getLogger(XGBoostExtension.class);
  public static String NAME = "Infogram";
  @Override
  public String getExtensionName() {
    return NAME;
  }

  public void logNativeLibInfo() {
    LOG.info("InfogramExtension is called.");
  }
}
