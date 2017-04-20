package water;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;

class Stuff extends Keyed<Stuff> {
  public Stuff() {}
}

public class KeyTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Ignore("TODO(vlad): make it work when performance issues are resolved")
  @Test public void testKeyDisconnectFromArray() {
    byte[] bytes = "This  is a key".getBytes();
    Key<Stuff> key1 = Key.make(bytes);
    bytes[5] = 'w';
    bytes[6] = 'a';
    assertEquals("This  is a key", key1.toString());
  }

  @Test public void testEquals() {
    String s1 = "From fairest creatures we desire increase";
    Key<Stuff> key1 = Key.make(s1);
    final String s2 = s1 + "\nThat thereby beauty's rose must never die.";
    Key<Stuff> key2 = Key.make(s2);
    assertEquals(key1, key1);
    assertTrue(key1.equals((Object)key1));
    assertFalse(key1.equals(null));
    Key<Stuff> k0 = s1.startsWith("F") ? null : key1; // to cheat the system
    assertFalse(key1.equals(k0));
    Object os1 = ("?"+s1).substring(1); // to cheat the system
    Object key3 = Key.make(s1); // to cheat the system
    assertTrue(key1.equals(key3));
    assertFalse(key1.equals(os1));
    assertFalse(key1.equals(key2));
    Key<Stuff> kh1 = Key.buildKeyForTestingPurposes(s1.getBytes(), 42);
    Key<Stuff> kh2 = Key.buildKeyForTestingPurposes(s1.getBytes(), 43);
    Key<Stuff> kh3 = Key.buildKeyForTestingPurposes(s2.getBytes(), 42);
    assertFalse(kh1.equals(kh2));
    assertFalse(kh1.equals(kh3));
    assertFalse(kh3.equals(kh1));
  }
}
