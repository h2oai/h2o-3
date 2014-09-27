package water;

import static water.H2OApp.register;

/**
 * Simple client application wrapper.
 */
public class H2OClientApp {
  public static void main2( String relpath ) { driver(new String[0],relpath); }

  public static void start() { main(new String[0]); }

  public static void main( String[] args  ) { driver(args,System.getProperty("user.dir")); }

  private static void driver( String[] args, String relpath ) {
    H2OClient.main(args);

    register(relpath);
  }
}
