package water;

import static org.junit.Assert.*;
import org.junit.*;

public class KeyToString extends TestUtil {
  @Test public void testKeyToString() {
    byte[] b = "XXXHelloAll".getBytes();
    assertEquals("XXXHelloAll", Key.make(b).toString());
    assertArrayEquals(b, Key.make(b).bytes());
    Key k = Key.make("$202020$");
    assertArrayEquals(new byte[]{32,32,32}, k.bytes());
    k = Key.make("$fffe85$Azaz09-.");
    assertTrue(k.toString().equals("$fffe85$Azaz09-."));
    k = Key.make("Hi There");
    assertTrue(k.toString().equals("Hi There"));
  }
  
}
