package hex.tree.xgboost;

import org.junit.Test;

import java.text.DecimalFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class BoosterParmsTest {

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
      put("float", DecimalFormat.getNumberInstance().format(0.5f));
      put("double", DecimalFormat.getNumberInstance().format(Math.E));
      put("boolean", true);
    }});

    Map<String, Object> localized = BoosterParms.fromMap(map).get();
    assertEquals(expected, localized);
  }

}