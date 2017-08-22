package hex.tree.xgboost;

import ml.dmlc.xgboost4j.java.NativeLibLoader;
import water.AbstractH2OExtension;
import water.H2O;
import water.util.Log;

import java.io.IOException;

/**
 * XGBoost Extension
 */
public class XGBoostExtension extends AbstractH2OExtension {

  private static String XGBOOST_MIN_REQUIREMENTS =
          "Xgboost (enabled GPUs) needs: \n"
                  + "  - CUDA 8.0\n"
                  + "XGboost (minimal version) needs: \n"
                  + "  - GCC 4.7+\n"
                  + "For more details, run in debug mode: `java -Dlog4j.configuration=file:///tmp/log4j.properties -jar h2o.jar`\n";

  // XGBoost initialization sequence was called flag
  private boolean isInitCalled = false;
  // XGBoost binary presence on the system
  private boolean isXgboostPresent = false;

  public static String NAME = "XGBoost";

  @Override
  public String getExtensionName() {
    return NAME;
  }

  @Override
  public boolean isEnabled() {
    //Check if multinode (XGBoost will not work for multinode)
    if (H2O.getCloudSize() > 1) {
      Log.warn("Detected more than 1 H2O node. H2O only supports XGBoost in single node setting.");
      return false;
    }
    // Check if some native library was loaded
    if (!isInitCalled) {
      synchronized (this) {
        if (!isInitCalled) {
          isXgboostPresent = initXgboost();
          isInitCalled = true;
        }
      }
    }
    return isXgboostPresent;
  }

  private final boolean initXgboost() {
    try {
      String libName = NativeLibLoader.getLoadedLibraryName();
      if (libName != null) {
        Log.info("Found XGBoost backend with library: " + libName);
        String suffix = NativeLibLoader.getLoadedLibrarySuffix();
        if (suffix.equals(NativeLibLoader.MINIMAL_LIB_SUFFIX)) {
          Log.warn("Your system supports only minimal version of XGBoost (no GPUs, no multithreading)!");
        }
        return true;
      } else {
        Log.warn("Cannot get XGBoost backend!" + XGBOOST_MIN_REQUIREMENTS);
        return false;
      }
    } catch (IOException e) {
      // Ups no lib loaded or load failed
      Log.warn("Cannot initialize XGBoost backend! " + XGBOOST_MIN_REQUIREMENTS);
      return false;
    }
  }
}