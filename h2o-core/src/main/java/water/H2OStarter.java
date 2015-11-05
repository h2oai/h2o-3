package water;

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
    H2O.configureLogging();
    H2O.registerExtensions();

    // Fire up the H2O Cluster
    H2O.main(args);

    H2O.registerRestApis(relativeResourcePath);
    if (finalizeRestRegistration) {
      H2O.finalizeRegistration();
    }
  }

  public static void start(String[] args, String relativeResourcePath) {
    start(args, relativeResourcePath, true);
  }

  public static void start(String[] args, boolean finalizeRestRegistration) {
    start(args, System.getProperty("user.dir"), finalizeRestRegistration);
  }
}
