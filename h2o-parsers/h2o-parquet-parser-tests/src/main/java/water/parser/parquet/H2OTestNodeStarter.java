package water.parser.parquet;

import org.junit.Ignore;
import water.H2OStarter;

/**
 * This class is intended to be run during distributed
 * testing from Idea.
 */
@Ignore("Support for tests, but no actual tests here")
public class H2OTestNodeStarter extends H2OStarter {

  public static void main(String[] args) {
    start(args, System.getProperty("user.dir"));
  }
}
