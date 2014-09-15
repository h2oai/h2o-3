package water;

import org.junit.*;

public class AutoBuffer2JSONTest extends TestUtil {
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
    public  float f1 = Float.NaN;
    private float f2 = Float.POSITIVE_INFINITY;
    final float f3 = Float.NEGATIVE_INFINITY;
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
    assertEqual(new A3(), "{\"i\":3,\"is\":[1,2,2147483647,-1],\"s\":\"hello\",\"ss\":[\"there\",null,\"\\\"\",\":\"]}");
  }

  static class A4 extends Iced { int a=7; }
  static class A5 extends Iced { float b=9f; }
  static class A6 extends A4 { final A5 a5=new A5(); char c='Q'; }
  @Test public void testNest() {
    assertEqual(new A4(), "{\"a\":7}");
    assertEqual(new A5(), "{\"b\":9.0}");
    assertEqual(new A6(), "{\"a\":7,\"a5\":{\"b\":9.0},\"c\":81}");
  }

  static class A7 extends Iced { }
  static class A8 extends A7 { }
  @Test public void testEmpty() {
    assertEqual(new A8(), "{}");
    assertEqual(new A7(), "{}");
  }

  // TODO: support arrays of booleans
  static class A9 extends Iced {boolean yep = true; boolean nope = false; }; // Boolean[] yepNope = new Boolean[] {true, false};
  @Test public void testBoolean() {
    assertEqual(new A9(), "{\"yep\":true,\"nope\":false}"); // ,"yepNope":[true,false]
  }
}
