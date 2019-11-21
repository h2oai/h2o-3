package water;

import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import water.util.Log;
import water.util.Timer;

@Ignore("Support for basic test behavior. Should be extended by all tests.")
public class TestBase extends Iced {
  private static String[] ignoreTestsNames;
  private static String[] doonlyTestsNames;
  /** Minimal cloud size to start test. */
  protected static int MINCLOUDSIZE = Integer.parseInt(System.getProperty("cloudSize", "1"));

  public TestBase() { this(1); }
  public TestBase(int minCloudSize) {
    MINCLOUDSIZE = Math.max(MINCLOUDSIZE,minCloudSize);
    String ignoreTests = System.getProperty("ignore.tests");
    if (ignoreTests != null) {
      ignoreTestsNames = ignoreTests.split(",");
      if (ignoreTestsNames.length == 1 && ignoreTestsNames[0].equals("")) {
        ignoreTestsNames = null;
      }
    }
    String doonlyTests = System.getProperty("doonly.tests");
    if (doonlyTests != null) {
      doonlyTestsNames = doonlyTests.split(",");
      if (doonlyTestsNames.length == 1 && doonlyTestsNames[0].equals("")) {
        doonlyTestsNames = null;
      }
    }
  }

  /** Execute this rule before each test to print test name and test class */
  @Rule transient public TestRule logRule = new TestRule() {

    @Override public Statement apply(Statement base, Description description) {
      Log.info("###########################################################");
      Log.info("  * Test class name:  " + description.getClassName());
      Log.info("  * Test method name: " + description.getMethodName());
      Log.info("###########################################################");
      return base;
    }
  };

  /* Ignore tests specified in the ignore.tests system property */
  @Rule transient public TestRule runRule = new TestRule() {
    @Override public Statement apply(Statement base, Description description) {
      String testName = description.getClassName() + "#" + description.getMethodName();
      boolean ignored = false;
      if (ignoreTestsNames != null && ignoreTestsNames.length > 0) {
        for (String tn : ignoreTestsNames) {
          if (testName.startsWith(tn)) {
            ignored = true;
            break;
          }
        }
      }
      if (doonlyTestsNames != null && doonlyTestsNames.length > 0) {
        ignored = true;
        for (String tn : doonlyTestsNames) {
          if (testName.startsWith(tn)) {
            ignored = false;
            break;
          }
        }
      }
      if (ignored) {
        // Ignored tests trump do-only tests
        Log.info("#### TEST " + testName + " IGNORED");
        return new Statement() {
          @Override
          public void evaluate() throws Throwable {}
        };
      } else {
        return base;
      }
    }
  };

  @Rule transient public TestRule timerRule = new TestRule() {
    @Override public Statement apply(Statement base, Description description) {
      return new TimerStatement(base, description.getClassName()+"#"+description.getMethodName());
    }
    class TimerStatement extends Statement {
      private final Statement _base;
      private final String _tname;
      Throwable _ex;
      public TimerStatement(Statement base, String tname) { _base = base; _tname = tname;}
      @Override public void evaluate() throws Throwable {
        Timer t = new Timer();
        try {
          _base.evaluate();
        } catch( Throwable ex ) {
          _ex=ex;
          throw _ex;
        } finally {
          Log.info("#### TEST "+_tname+" EXECUTION TIME: " + t.toString());
        }
      }
    }
  };

}
