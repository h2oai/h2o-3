package water;

/**
 * Simple client application wrapper.
 *
 *
 * A way to use:
 *  1. Start H2O cloud as usual
 *  2. Start H2OClientApp
 *
 *  Another way to use:
 *  1. Start H2OClient App
 *  2. Start H2O Clouds
 */
public class H2OClientApp {
  public static void start() { main(new String[0]); }

  public static void main( String[] args ) { driver(args,System.getProperty("user.dir")); }

  @SuppressWarnings("unused")
  public static void main2( String relpath ) { driver(new String[0],relpath); }

  private static void driver( String[] args, String relpath ) {
    H2OClient.main(args);
    H2OApp.register(relpath);
    H2O.finalizeRegistration();
  }

  @SuppressWarnings("unused")
  public static void waitForCloudSize(int x, long ms) {
    H2O.waitForCloudSize(x, ms);
  }
}
