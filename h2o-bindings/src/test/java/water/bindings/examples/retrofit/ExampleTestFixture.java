package water.bindings.examples.retrofit;

import org.junit.BeforeClass;

import java.util.Random;

import water.H2O;
import water.H2OStarter;

/**
 * Shared support to test examples using Java API.
 */
public class ExampleTestFixture {

  @BeforeClass
  static public void setup() {
    String cloudName = "h2o-bindings-test-" + new Random().nextInt();
    H2OStarter.start(new String[] { "-name", cloudName}, true);
  }

  static String getH2OUrl() {
    return H2O.getURL("http") + "/";
  }
}
