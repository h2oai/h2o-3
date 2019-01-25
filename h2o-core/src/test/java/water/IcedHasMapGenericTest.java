package water;

import org.junit.BeforeClass;
import org.junit.Test;
import water.parser.BufferedString;
import water.util.IcedDouble;
import water.util.IcedHashMapGeneric;
import water.util.IcedLong;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by tomas on 8/10/16.
 */
public class IcedHasMapGenericTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test
  public void testSerialization(){
    IcedHashMapGeneric m = new IcedHashMapGeneric();
    m.put("haha","gaga"); // String -> String pair
    m.put("str->freezable", new IcedDouble(3.14)); // String -> String pair
    m.put("str->freezable[]", new Freezable[]{new IcedDouble(3.14)}); // String -> String pair
    m.put("str->Integer", 314); // String -> String pair

    m.put(new BufferedString("haha2"),"gaga"); // Freezable -> String pair
    m.put(new BufferedString("str->freezable2"), new IcedDouble(3.14)); // String -> String pair
    m.put(new BufferedString("str->freezable[]2"), new Freezable[]{new IcedDouble(3.14)}); // String -> String pair
    m.put(new BufferedString("str->Integer2"), 314); // String -> String pair
    m.put(new IcedLong(1234), 1234); // String -> String pair

    byte [] buf = new AutoBuffer().put(m).buf();
    IcedHashMapGeneric m2 = new AutoBuffer(buf).get();
    assertEquals(m.size(),m2.size());
    Set<Map.Entry> entries = m.entrySet();
    for(Map.Entry e:entries){
      if(e.getValue() instanceof Freezable[])
        assert Arrays.deepEquals((Freezable[]) e.getValue(), (Freezable[]) m2.get(e.getKey()));
      else
        assertEquals(e.getValue(),m2.get(e.getKey()));
    }
  }
}
