package water.api;

import org.junit.Assert;
import org.junit.Test;

/**
 * Various tests for schema parsing.
 */
public class SchemaParsingTest {

  @Test public void testArrayParse() {
    String[] testCases = new String[] { "null", "[]", "[1.0]",
                                        "[2.0]",
                                        "[1]", "[\"string\"]"};
    Class[]  testClasses = new Class[] { String[].class, String[].class, float[].class,
                                         double[].class,
                                         int[].class, String[].class};
    Object[] expectedValues = new Object[] { null, new String[] {}, new Float[] { 1.0f },
                                             new Double[] { 2.0 },
                                             new Integer[] { 1 }, new String[] { "string"}};

    for (int i = 0; i < testCases.length; i++) {
      assertArrayEquals(testCases[i], testClasses[i], expectedValues[i]);
    }
  }

  @Test public void testSingleValueAsArrayParse() {
    String[] testCases = new String[] { "null", "1.0", "\"string\"" };
    Class[]  testClasses = new Class[] { String[].class, float[].class, String[].class};
    Object[] expectedValues = new Object[] { null, new Float[] { 1.0f }, new String[] { "string" } };

    for (int i = 0; i < testCases.length; i++) {
      assertArrayEquals(testCases[i], testClasses[i], expectedValues[i]);
    }
  }

  private static void assertArrayEquals(String testCase, Class testClass, Object expectedValue) {
    Object result = Schema.parse("test_field", testCase, testClass, false, Schema.class);
    if (expectedValue == null) {
      Assert.assertTrue("Parsed value has to be null", result == null);
    } else {
      Assert.assertTrue("Result has to be array", result.getClass().isArray());
      Assert.assertEquals("Result has to be subclass of specified class", expectedValue.getClass(), result.getClass());
      Assert.assertArrayEquals("Parsed value has to match!", asOA(expectedValue), asOA(result));
    }
  }

  private static Object[] asOA(Object o) {
    return (Object[]) o;
  }

}
