package water.test.util;

import org.junit.Assert;
import org.junit.Ignore;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import hex.Model;

/**
 * Helper function for grid testing.
 */
@Ignore("Support for tests, but no actual tests here")
public class GridTestUtils {

  public static Map<String, Set<Object>> initMap(String[] paramNames) {
    Map<String, Set<Object>> modelParams = new HashMap<>();
    for (String name : paramNames) {
      modelParams.put(name, new HashSet<>());
    }
    return modelParams;
  }

  public static <P extends Model.Parameters> Map<String, Set<Object>> extractParams(Map<String, Set<Object>> params,
                                                       P modelParams,
                                                       String[] paramNames) {
    try {
      for (String paramName : paramNames) {
        Field f = modelParams.getClass().getField(paramName);
        params.get(paramName).add(f.get(modelParams));
      }
      return params;
    } catch (NoSuchFieldException e) {
      throw new IllegalArgumentException(e);
    } catch (IllegalAccessException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public static void assertParamsEqual(String message, Map<String, Object[]> expected, Map<String, Set<Object>> actual) {
    String[] expectedNames = expected.keySet().toArray(new String[expected.size()]);
    String[] actualNames = actual.keySet().toArray(new String[actual.size()]);
    Assert.assertArrayEquals(message + ": names of used hyper parameters have to match",
                             expectedNames,
                             actualNames);
    for (String name : expectedNames) {
      Object[] expectedValues = expected.get(name);
      Arrays.sort(expectedValues);
      Object[] actualValues = actual.get(name).toArray(new Object[0]);
      Arrays.sort(actualValues);
      Assert.assertArrayEquals(message + ": used hyper values have to match",
                               expectedValues,
                               actualValues);
    }
  }

}
