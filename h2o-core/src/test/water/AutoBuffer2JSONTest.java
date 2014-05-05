package water;

import org.junit.*;

public class AutoBuffer2JSONTest extends TestUtil {
  @BeforeClass public static void stall() { TestUtil.stall_till_cloudsize(1); }

  private void assertEqual(Iced test, String expJson) {
    AutoBuffer ab = new AutoBuffer();
    String json = new String(test.writeJSON(ab).buf());
    Assert.assertEquals(expJson, json);
  }

  static class A1 extends Iced {
    double d1 = Double.NaN;
    double d2 = Double.POSITIVE_INFINITY;
    double d3 = Double.NEGATIVE_INFINITY;
    double d4 = -3.141527;
  }

  @Test public void testDouble() {
    assertEqual(new A1(), "{\"d1\":\"NaN\",\"d2\":\"Infinity\",\"d3\":\"-Infinity\",\"d4\":-3.141527}");
  }

  static class A2 extends Iced {
    float f1 = Float.NaN;
    float f2 = Float.POSITIVE_INFINITY;
    float f3 = Float.NEGATIVE_INFINITY;
    float f4 = -3.141527f;
  }

  @Test public void testFloat() {
    assertEqual(new A2(), "{\"f1\":\"NaN\",\"f2\":\"Infinity\",\"f3\":\"-Infinity\",\"f4\":-3.141527}");
  }

  static class A3 extends Iced {
    int i = 3;
    int[] is = new int[]{1,2,Integer.MAX_VALUE,-1};
    String s = "hello";
    String ss[] = new String[]{"there",null,"\"",":"};
  }

  @Test public void testMisc() {
    assertEqual(new A3(), "{\"i\":\"3\",\"f2\":\"Infinity\",\"f3\":\"-Infinity\",\"f4\":-3.141527}");
  }
}
