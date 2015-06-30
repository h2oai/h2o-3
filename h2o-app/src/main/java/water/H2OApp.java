package water;

public class H2OApp {
  public static void main(String[] args) {
    driver(args,System.getProperty("user.dir"));
  }

  @SuppressWarnings("unused")
  public static void main2( String relativeResourcePath ) {
    driver(new String[0], relativeResourcePath);
  }

  private static void driver( String[] args, String relativeResourcePath) {
    // Fire up the H2O Cluster
    H2O.main(args);

    // Register REST API
    register(relativeResourcePath);
    H2O.finalizeRegistration();
  }

  static void register(String relativeResourcePath) {
    new water.api.Register().register(relativeResourcePath);
    new hex.api.Register().register(relativeResourcePath);
  }
}
