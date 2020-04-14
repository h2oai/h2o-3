package water;

import org.junit.Ignore;

/**
 * This class is intended to be ran during distributed
 * testing from Idea.
 */
@Ignore("Support for tests, but no actual tests here")
public class H2OTestNodeStarter extends H2OStarter {

  public static void main(String[] args) {
    start(args, System.getProperty("user.dir"));
  }
}