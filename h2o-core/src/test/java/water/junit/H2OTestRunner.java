package water.junit;

import org.junit.Ignore;
import org.junit.internal.JUnitSystem;
import org.junit.internal.RealSystem;
import org.junit.internal.TextListener;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

import java.util.ArrayList;
import java.util.List;

/**
 * Replacement of JUnitCore runner which handles
 * generation into XML file.
 */
@Ignore("Support for tests, but no actual tests here")
public class H2OTestRunner {

  public Result run(String[] args) throws Exception {

    // List all classes - adapted from JUnitCore code
    List<Class<?>> classes = new ArrayList<Class<?>>();
    List<Failure> missingClasses = new ArrayList<Failure>();
    for (String arg : args) {
      try {
        classes.add(Class.forName(arg));
      } catch (ClassNotFoundException e) {
        Description description = Description.createSuiteDescription(arg);
        Failure failure = new Failure(description, e);
        missingClasses.add(failure);
      }
    }
    // Create standard JUnitCore
    JUnitCore jcore = new JUnitCore();
    // Create default "system"
    JUnitSystem jsystem = new RealSystem();
    // Setup default listener
    jcore.addListener(new TextListener(jsystem));
    // Add XML generator listener
    jcore.addListener(new XMLTestReporter());
    Result result = jcore.run(classes.toArray(new Class[0]));
    for (Failure each : missingClasses) {
      System.err.println("FAIL Missing class in H2OTestRunner: " + each);
      result.getFailures().add(each);
    }
    return result;
  }

  public static void main(String[] args) throws Exception {
    H2OTestRunner testRunner = new H2OTestRunner();
    Result result = testRunner.run(args);
    System.exit(result.wasSuccessful() ? 0 : 1);
  }
}
