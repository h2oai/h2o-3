package water;

import org.junit.Test;

import static org.junit.Assert.*;

public class H2OTest extends TestBase {
  @Test
  public void testGetSysProperty() {
    assertEquals("default1", H2O.getSysProperty("test.testGetSysProperty", "default1"));
    System.setProperty(H2O.ARGS.SYSTEM_PROP_PREFIX + "test.testGetSysProperty", "value1");
    assertEquals("value1", H2O.getSysProperty("test.testGetSysProperty", "default1"));
  }

  @Test
  public void testGetSysBoolProperty() {
    assertFalse(H2O.getSysBoolProperty("test.testGetSysBoolProperty", false));
    assertTrue(H2O.getSysBoolProperty("test.testGetSysBoolProperty", true));
    System.setProperty(H2O.ARGS.SYSTEM_PROP_PREFIX + "test.testGetSysBoolProperty", "true");
    assertTrue(H2O.getSysBoolProperty("test.testGetSysBoolProperty", false));
  }

}
