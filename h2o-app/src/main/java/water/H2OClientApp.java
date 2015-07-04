package water;

/**
 * Simple client application wrapper.
 *
 * CAUTION:
 * This is used by Sparkling Water and other environments where an H2O client node is needed.
 * A client node is a node that can launch and monitor work, but doesn't do any work.
 * Don't use this unless you know what you are doing.  You probably really just want to use
 * H2OApp directly.
 */
public class H2OClientApp {
  public static void main(String[] args) {
    // Prepend "-client" parameter.
    String[] args2 = new String[args.length + 1];
    args2[0] = "-client";
    int i = 1;
    for (String s : args) {
      args2[i] = s;
      i++;
    }

    // Call regular H2OApp.
    H2OApp.main(args2);
  }
}
