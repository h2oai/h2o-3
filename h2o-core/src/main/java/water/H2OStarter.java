package water;

/**
 * H2O starter which manages start and registration of application extensions.
 */
public class H2OStarter {
  /**
   * Start H2O node.
   *
   * @param args  H2O parameters
   * @param relativeResourcePath
   */
  public static void start(String[] args, String relativeResourcePath) {
    H2O.configureLogging();
    H2O.registerExtensions();

    // Fire up the H2O Cluster
    H2O.main(args);

    H2O.registerRestApis(relativeResourcePath);
    H2O.finalizeRegistration();
  }
}
