package water;

import org.junit.Test;

import static org.junit.Assert.*;

public class H2OTest {

  @Test
  public void decodeClientInfoNotClient(){
    short timestamp = H2O.calculateNodeTimestamp(1540375717281L, false);
    assertEquals(timestamp, 9633);
    assertFalse(H2O.decodeIsClient(timestamp));
  }

  @Test
  public void decodeClientInfoClient(){
    short timestamp = H2O.calculateNodeTimestamp(1540375717281L, true);
    assertEquals(timestamp, -9633);
    assertTrue(H2O.decodeIsClient(timestamp));
  }

  @Test
  public void decodeNotClientZeroTimestamp(){
    short timestamp = H2O.calculateNodeTimestamp(0L, false);
    assertEquals(timestamp, 1);
    assertFalse(H2O.decodeIsClient(timestamp));
  }

  @Test
  public void decodeClientZeroTimestamp(){
    short timestamp = H2O.calculateNodeTimestamp(0L, true);
    assertEquals(timestamp, -1);
    assertTrue(H2O.decodeIsClient(timestamp));
  }

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
