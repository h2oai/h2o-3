package water;

import org.junit.*;

import java.util.Arrays;

public class AutoSerialTest extends Iced {
  @BeforeClass public static void stall() { TestUtil.stall_till_cloudsize(1); }
  @AfterClass public static void checkLeakedKeys() { TestUtil.checkLeakedKeys(); }

  byte _byte, _bytes[];
  short _short, _shorts[];
  int _int, _ints[];
  float _float, _floats[];
  long _long, _longs[], _longss[][];
  double _double,_doubles[],_doubless[][];
  String _string;
  Key _key;

  static AutoBuffer _ab = new AutoBuffer(new byte[1000]);
  static AutoBuffer abw() { return _ab.clearForWriting(0); }
  static AutoBuffer abr() { return _ab. flipForReading(); }



  @Test public void testByte() throws Exception {
    byte[] tests = { 0, 4, -1, 127, -128 };
    for( byte exp : tests) {
      _byte = exp;
      this.write(abw());
      this. read(abr());
      Assert.assertEquals(exp, _byte);
    }
  }

  @Test public void testShort() throws Exception {
    short[] tests = { 0, 4, -1, 127, -128 };
    for( short exp : tests) {
      _short = exp;
      this.write(abw());
      this. read(abr());
      Assert.assertEquals(exp, _short);
    }
  }

  @Test public void testInt() throws Exception {
    int[] tests = { 0, 4, Integer.MAX_VALUE, Integer.MIN_VALUE, -1 };
    for( int exp : tests) {
      _int = exp;
      this.write(abw());
      this. read(abr());
      Assert.assertEquals(exp, _int);
    }
  }

  @Test public void testLong() throws Exception {
    long[] tests = { 0, 4, Integer.MAX_VALUE, Integer.MIN_VALUE, -1,
        Long.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE - Integer.MAX_VALUE
    };
    for( long exp : tests) {
      _long = exp;
      this.write(abw());
      this. read(abr());
      Assert.assertEquals(exp, _long);
    }
  }

  @Test public void testFloat() throws Exception {
    float[] tests = { 0, 4, Integer.MAX_VALUE, Integer.MIN_VALUE, -1,
        Long.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE - Integer.MAX_VALUE,
        Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY
    };
    for( float exp : tests) {
      _float = exp;
      this.write(abw());
      this. read(abr());
      Assert.assertEquals(exp, _float, Math.ulp(exp));
    }
  }

  @Test public void testDouble() throws Exception {
    double[] tests = { 0, 4, Integer.MAX_VALUE, Integer.MIN_VALUE, -1,
        Long.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE - Integer.MAX_VALUE,
        Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY
    };
    for( double exp : tests) {
      _double = exp;
      this.write(abw());
      this. read(abr());
      Assert.assertEquals(exp, _double, Math.ulp(exp));
    }
  }

  @Test public void testKey() throws Exception {
    H2O.main(new String[0]);
    Key[] tests = { Key.make(), Key.make("monkey"), Key.make("ninja"), null };
    for( Key exp : tests) {
      _key = exp;
      this.write(abw());
      this. read(abr());
      Assert.assertEquals(exp, _key);
    }
  }

  @Test public void testString() throws Exception {
    H2O.main(new String[0]);
    String[] tests = { "", "monkey", "ninja", null };
    for( String exp : tests) {
      _string = exp;
      this.write(abw());
      this. read(abr());
      Assert.assertEquals(exp, _string);
    }
  }

  @Test public void testByteArray() throws Exception {
    byte[][] tests = {
        { 0, 1, 2 },
        { },
        null,
        { 6, -1, 19, -49 }
    };
    for( byte[] exp : tests) {
      _bytes = exp;
      this.write(abw());
      this. read(abr());
      Assert.assertArrayEquals(exp, _bytes);
    }
  }

  @Test public void testShortArray() throws Exception {
    short[][] tests = {
        { 0, 1, 2 },
        { },
        null,
        { 6, -1, 19, -49, Short.MAX_VALUE }
    };
    for( short[] exp : tests) {
      _shorts = exp;
      this.write(abw());
      this. read(abr());
      Assert.assertArrayEquals(exp, _shorts);
    }
  }

  @Test public void testIntArray() throws Exception {
    int[][] tests = new int[][] {
        { 0, 1, 2 },
        { },
        null,
        { 6, Integer.MAX_VALUE, -1, 19, -49 }
    };
    for( int[] exp : tests) {
      _ints = exp;
      this.write(abw());
      this. read(abr());
      Assert.assertArrayEquals(exp, _ints);
    }
  }

  @Test public void testLongArray() throws Exception {
    long[][] tests = {
        { 0, 1, 2 },
        { },
        null,
        { 6, -1, 19, -49 },
        { Long.MAX_VALUE, Long.MIN_VALUE}
    };
    for( long[] exp : tests) {
      _longs = exp;
      this.write(abw());
      this. read(abr());
      Assert.assertArrayEquals(exp, _longs);
    }
  }

  @Test public void testFloatArray() throws Exception {
    float[][] tests = {
        { 0, 1, 2 },
        { },
        null,
        { 6, -1, 19, -49 },
        { Float.MAX_VALUE, Float.MIN_VALUE}
    };
    for( float[] exp : tests) {
      _floats = exp;
      this.write(abw());
      this. read(abr());
      Assert.assertTrue(Arrays.equals(exp, _floats));
    }
  }

  @Test public void testDoubleArray() throws Exception {
    double[][] tests = {
        { 0, 1, 2 },
        { },
        null,
        { 6, -1, 19, -49 },
        { Double.MAX_VALUE, Double.MIN_VALUE}
    };
    for( double[] exp : tests) {
      _doubles = exp;
      this.write(abw());
      this. read(abr());
      Assert.assertTrue(Arrays.equals(exp, _doubles));
    }
  }


  @Test public void testLongArrayArray() throws Exception {
    long[][][] tests = {
      { { 0, 1, 2 },
        { },
        null,
        { 6, -1, 19, -49 },
        { Long.MAX_VALUE, Long.MIN_VALUE}
      },
      null,
      { },
    };
    for( long[][] exp : tests) {
      _longss = exp;
      this.write(abw());
      this. read(abr());
      if( exp != null ) {
        Assert.assertEquals(_longss.length,exp.length);
        for( int i=0; i<exp.length; i++ )
          Assert.assertArrayEquals(_longss[i],exp[i]);
      } else Assert.assertNull(_longss);
    }
  }

  @Test public void testDoubleArrayArray() throws Exception {
    double[][][] tests = {
      { { 0.5, 1.5, 2.5 },
        { },
        null,
        { 6.3, -1.3, 19.3, -49.4 },
        { Double.MAX_VALUE, Double.MIN_VALUE}
      },
      null,
      { },
    };
    for( double[][] exp : tests) {
      _doubless = exp;
      this.write(abw());
      this. read(abr());
      if( exp != null ) {
        Assert.assertEquals(_doubless.length,exp.length);
        for( int i=0; i<exp.length; i++ )
          Assert.assertTrue(Arrays.equals(_doubless[i],exp[i]));
      } else Assert.assertNull(_doubless);
    }
  }

  private static class IcedSerTest extends Iced {
    final double x;
    public IcedSerTest(double x){this.x = x;}
  }
  Freezable [][][] _aaa;
  @Test public void testIcedArrays() {
    _aaa = new IcedSerTest[][][]{{{new IcedSerTest(Math.PI)}}};
    this.write(abw());
    this.read(abr());
    Assert.assertTrue(_aaa.length == 1);
    Assert.assertTrue(_aaa[0].length == 1);
    Assert.assertTrue(_aaa[0][0].length == 1);
    Assert.assertTrue(((IcedSerTest)_aaa[0][0][0]).x == Math.PI);
    _aaa = null;
  }

  /* =======================
     Enum array serialization
    ======================== */
  enum TestEnum {
    A, B, C;
  }

  TestEnum[] _ea;

  /** Test for PUBDEV-1914 */
  @Test
  public void testArrayOfEnums() {
    _ea = new TestEnum[] { TestEnum.B, null, TestEnum.A, TestEnum.B, TestEnum.C};
    this.write(abw());
    _ea = null;
    this.read(abr());
    Assert.assertTrue(_ea.length == 5);
    Assert.assertTrue(Arrays.deepEquals(_ea, new TestEnum[] { TestEnum.B, null, TestEnum.A, TestEnum.B, TestEnum.C}));
    _ea = null;
  }

  /* =======================
     Generic type array serialization
    ======================== */

  abstract static class P extends Iced { }
  static class P1 extends P {}
  static class PA<T extends P> extends Iced<PA> {
    public PA(T[] ps) {
      _ps = ps;
    }
    final T[] _ps;
  }

  PA<P1> _gcs;

  // Right now we do not support serialization of generic arrays since
  // the weaver forgets type annotation.
  @Ignore("PUBDEV-1863")
  public void testGenericArray() {
    _gcs = new PA(new P1[] { new P1(), null, new P1() });
    this.write(abw());
    _gcs = null;
    this.read(abr());
    Assert.assertEquals("Size of array has to match", _gcs._ps.length, 3);
    Assert.assertArrayEquals("Content of array has to match", _gcs._ps, new P1[]{new P1(), null, new P1() });
    _gcs = null;
  }

}
