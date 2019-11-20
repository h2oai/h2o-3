package hex.tree.xgboost;

import org.junit.Test;
import water.TestBase;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class BoosterParmsTest extends TestBase {

  @Test
  public void testGetLocalized() {
    Map<String, Object> map = Collections.unmodifiableMap(new HashMap<String, Object>() {{
      put("integer", 42);
      put("float", 0.5f);
      put("double", Math.E);
      put("boolean", true);
    }});
    Map<String, Object> expected = Collections.unmodifiableMap(new HashMap<String, Object>() {{
      put("integer", 42);
      put("float", new Float(0.5F).toString());
      put("double", new Double(Math.E).toString());
      put("boolean", true);
    }});

    Map<String, Object> localized = BoosterParms.fromMap(map).get();
    assertEquals(expected, localized);
  }

}
