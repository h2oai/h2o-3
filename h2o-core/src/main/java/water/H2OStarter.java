package water;

import water.init.NetworkInit;
import water.util.Log;

import java.io.File;

/**
 * H2O starter which manages start and registration of application extensions.
 */
public class H2OStarter {
  /**
   * Start H2O node.
   *
   * @param args  H2O parameters
   * @param relativeResourcePath  FIXME remove it
   * @param finalizeRestRegistration  close registration of REST API
   */
  public static void start(String[] args, String relativeResourcePath, boolean finalizeRestRegistration) {
    long time0 = System.currentTimeMillis();
    // Fire up the H2O Cluster
    H2O.main(args);

    if (H2O.ARGS.disable_flow) {
      Log.info("Access to H2O Flow is disabled");
    } else {
      H2O.registerResourceRoot(new File(relativeResourcePath + File.separator + "h2o-web/src/main/resources/www"));
      H2O.registerResourceRoot(new File(relativeResourcePath + File.separator + "h2o-core/src/main/resources/www"));
    }
    ExtensionManager.getInstance().registerRestApiExtensions();
    if (!H2O.ARGS.disable_web) {
      if (finalizeRestRegistration) {
        H2O.startServingRestApi();
      }
    }

    long timeF = System.currentTimeMillis();
    Log.info("H2O started in " + (timeF - time0) + "ms");
    if (!H2O.ARGS.disable_web) {
      Log.info("");
      String message = H2O.ARGS.disable_flow ? "Connect to H2O from your R/Python client: " : "Open H2O Flow in your web browser: ";
      message += H2O.ARGS.web_ip == null ?
              H2O.getURL(NetworkInit.h2oHttpView.getScheme()) :
              H2O.getURL(NetworkInit.h2oHttpView.getScheme(), H2O.ARGS.web_ip, H2O.API_PORT, H2O.ARGS.context_path);
      Log.info(message);
      Log.info("");
    }
  }

  public static void start(String[] args, String relativeResourcePath) {
    start(args, relativeResourcePath, true);
  }

  public static void start(String[] args, boolean finalizeRestRegistration) {
    start(args, System.getProperty("user.dir"), finalizeRestRegistration);
  }
}
