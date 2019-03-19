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

}
