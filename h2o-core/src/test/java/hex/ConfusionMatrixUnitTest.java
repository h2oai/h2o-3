package hex;

import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ProvideSystemProperty;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import water.TestBase;

import static org.junit.Assert.*;

public class ConfusionMatrixUnitTest extends TestBase {

  @Rule
  public final ProvideSystemProperty provideSystemProperty = new ProvideSystemProperty("sys.ai.h2o.cm.maxClasses", "7");

  @Rule
  public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties();

  @Test
  public void tooLarge() {
    assertFalse(new ConfusionMatrix(null, new String[7]).tooLarge());
    assertTrue(new ConfusionMatrix(null, new String[8]).tooLarge());
  }

  @Test
  public void maxClasses() {
    assertEquals(7, ConfusionMatrix.maxClasses());
  }

  @Test
  public void parseMaxClasses() {
    assertEquals(1000, ConfusionMatrix.parseMaxClasses("-1"));
    assertEquals(42, ConfusionMatrix.parseMaxClasses("42"));
    assertEquals(1000, ConfusionMatrix.parseMaxClasses("NA"));
  }
}
