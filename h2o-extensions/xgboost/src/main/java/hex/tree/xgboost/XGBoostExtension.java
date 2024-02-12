package hex.tree.xgboost;

import ai.h2o.xgboost4j.java.INativeLibLoader;
import ai.h2o.xgboost4j.java.NativeLibLoader;
import hex.tree.xgboost.util.NativeLibrary;
import hex.tree.xgboost.util.NativeLibraryLoaderChain;
import org.apache.log4j.Logger;
import water.AbstractH2OExtension;
import water.H2O;

import java.io.IOException;
import java.util.Arrays;

/**
 * XGBoost Extension
 *
 * This is responsible for early initialization of
 * XGBoost per cluster node. The registration
 * of XGBoost REST API requires thix extension
 * to be enabled.
 */
public class XGBoostExtension extends AbstractH2OExtension {
  
  private static final Logger LOG = Logger.getLogger(XGBoostExtension.class);

  private static final String XGBOOST_MIN_REQUIREMENTS =
          "Xgboost (enabled GPUs) needs: \n"
                  + "  - CUDA 8.0\n"
                  + "XGboost (minimal version) needs: \n"
                  + "  - GCC 4.7+\n"
                  + "Apple silicon is not supported yet\n"
                  + "For more details, run in debug mode: `java -Dlog4j.configuration=file:///tmp/log4j.properties -jar h2o.jar`\n";

  // XGBoost initialization sequence was called flag
  private boolean isInitCalled = false;
  // XGBoost binary presence on the system
  private boolean isXgboostPresent = false;
  private NativeLibInfo nativeLibInfo = null;
  
  public static String NAME = "XGBoost";

  @Override
  public String getExtensionName() {
    return NAME;
  }

  @Override
  public boolean isEnabled() {
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

  public void logNativeLibInfo() {
    if (nativeLibInfo == null) {
      LOG.warn("No native XGBoost library found.");
      return;
    }
    LOG.info("Found XGBoost backend with library: " + nativeLibInfo.name);
    if (nativeLibInfo.flags.length == 0) {
      LOG.warn("Your system supports only minimal version of XGBoost (no GPUs, no multithreading)!");
    } else {
      LOG.info("XGBoost supported backends: " + Arrays.toString(nativeLibInfo.flags));
    }
  }

  public static NativeLibraryLoaderChain getLoader() throws IOException {
    INativeLibLoader loader = NativeLibLoader.getLoader();
    if (! (loader instanceof NativeLibraryLoaderChain)) {
      LOG.warn("Unexpected XGBoost library loader found. Custom loaders are not supported in this version. " +
              "XGBoost extension will be disabled.");
      return null;
    }
    return(NativeLibraryLoaderChain) loader;
  }

  @Override
  public void onLocalNodeStarted() {
    if (!isEnabled())
      return;
    final double ratio = H2O.ARGS.off_heap_memory_ratio;
    if (H2O.ARGS.off_heap_memory_ratio > 0) {
      MemoryCheck.Report report = MemoryCheck.runCheck(ratio);
      if (!report.isOffHeapRequirementMet()) {
        LOG.warn("There doesn't seem to be enough memory available for XGBoost model training (off_heap_memory_ratio=" + ratio + "), " +
                "training XGBoost models is not advised. Details: " + report);
      }
    }
  }

  private boolean initXgboost() {
    try {
      NativeLibraryLoaderChain chainLoader = getLoader();
      if (chainLoader == null)
        return false;
      NativeLibrary lib = chainLoader.getLoadedLibrary();
      nativeLibInfo = new NativeLibInfo(lib);
      return true;
    } catch (IOException e) {
      // Ups no lib loaded or load failed
      LOG.debug("Cause of the xgboost unsuccessful load", e);
      LOG.warn("Cannot initialize XGBoost backend! " + XGBOOST_MIN_REQUIREMENTS);
      return false;
    }
  }

  private static class NativeLibInfo {
    String name;
    NativeLibrary.CompilationFlags[] flags;

    private NativeLibInfo(NativeLibrary nl) {
      name = nl.getName();
      flags = nl.getCompilationFlags();
    }
  }
  
}
