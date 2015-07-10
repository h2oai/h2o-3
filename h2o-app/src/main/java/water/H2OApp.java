package water;

public class H2OApp {
  public static void main(String[] args) {
    driver(args, System.getProperty("user.dir"));
  }

  @SuppressWarnings("unused")
  public static void main2(String relativeResourcePath) {
    driver(new String[0], relativeResourcePath);
  }

  private static void driver(String[] args, String relativeResourcePath) {
    H2O.configureLogging();
    H2O.registerExtensions();

    // Fire up the H2O Cluster
    H2O.main(args);

    H2O.registerRestApis(relativeResourcePath);
    H2O.finalizeRegistration();
  }
}
