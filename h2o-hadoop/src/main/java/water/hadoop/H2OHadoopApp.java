package water.hadoop;

import water.H2O;
import java.io.File;

public class H2OHadoopApp {
  public static void main2( String relpath ) { driver(new String[0],relpath); }

  public static void main( String[] args  ) { driver(args,System.getProperty("user.dir")); }

  private static void driver( String[] args, String relpath ) {
    // This is only for testing HDFS.
    // Don't use this main class for any real purpose.
    H2O.main(args);
    register(relpath);
  }

  static void register(String relpath) {
    H2O.registerResourceRoot(new File(relpath + File.separator + "h2o-web/src/main/resources/www"));
    H2O.registerResourceRoot(new File(relpath + File.separator + "h2o-core/src/main/resources/www"));
    H2O.finalizeRegistration();
  }
}
