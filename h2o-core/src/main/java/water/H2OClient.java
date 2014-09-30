package water;

import static water.util.ArrayUtils.contains;
import static water.util.ArrayUtils.prepend;

/**
 * A simple wrapper around H2O starter to launch H2O in client mode.
 */
final public class H2OClient {
    /** Entry point which ensure launching H2O in client mode */
    public static void main( String[] args ) {
      String[] nargs = args;
        if (!contains(nargs, "-client")) {
            nargs = prepend(nargs, "-client");
        }
        H2O.main(nargs);
    }

    public static void waitForCloudSize(int x, long ms) {
      H2O.waitForCloudSize(x, ms);
    }
}
