package water.util;

import org.junit.Test;
import water.AutoBuffer;
import water.TestBase;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class IcedHashMapGenericTest extends TestBase {

  @Test
  public void testJavaNativeValues() {
    Map<String, Object> map = Collections.unmodifiableMap(new HashMap<String, Object>() {{
      put("integer", 42);
      put("float", 0.5f);
      put("double", Math.E);
      put("boolean-true", true);
      put("boolean-false", false);
    }});

    final IcedHashMapGeneric<String, Object> icedMapOrig = new IcedHashMapGeneric.IcedHashMapStringObject();
    icedMapOrig.putAll(map);
    assertEquals(map, icedMapOrig);

    final IcedHashMapGeneric<String, Object> icedMapDeser = new AutoBuffer()
            .put(icedMapOrig)
            .flipForReading()
            .get();
    assertEquals(map, icedMapDeser);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    AutoBuffer ab = new AutoBuffer(baos, false);
    icedMapOrig.writeJSON_impl(ab);
    ab.close();
    assertEquals(
            "\0\"integer\":42, \"boolean-false\":false, \"double\":2.718281828459045, \"float\":0.5, \"boolean-true\":true",
            baos.toString());
    System.out.println(baos.toString());
  }

}
