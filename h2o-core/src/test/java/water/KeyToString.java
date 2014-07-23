package water;

import static org.junit.Assert.*;
import org.junit.*;

import java.util.Arrays;

public class KeyToString extends TestUtil {
  @Test public void testKeyToString() {
    byte[] b = "XXXHelloAll".getBytes();
    assertTrue(Key.make(b).toString().equals("XXXHelloAll"));
    assertTrue(Arrays.equals(Key.make(b)._kb,b));
    b[0] = 16;
    b[1] = 20;
    Key k = Key.make("$202020$");
    assertEquals(k._kb.length, 3);
    assertEquals(k._kb[0], 32);
    assertEquals(k._kb[1], 32);
    assertEquals(k._kb[2], 32);
    k = Key.make("$fffe85$Azaz09-.");
    assertTrue(k.toString().equals("$fffe85$Azaz09-."));
    k = Key.make("Hi There");
    assertTrue(k.toString().equals("Hi There"));
  }
  
}
