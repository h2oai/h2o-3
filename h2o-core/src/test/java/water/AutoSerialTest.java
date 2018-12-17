package water;

import org.junit.*;
import water.util.JSONUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

public class AutoSerialTest extends Iced {
  @BeforeClass public static void stall() { TestUtil.stall_till_cloudsize(1); }
  @After public void checkLeakedKeys() { TestUtil.performLeakedKeysCheck();}

  byte _byte, _bytes[];
  short _short, _shorts[];
  int _int, _ints[];
  float _float, _floats[];
  long _long, _longs[], _longss[][];
  double _double,_doubles[],_doubless[][];
  String _string;
  Key _key;

  static AutoBuffer _ab = new AutoBuffer(new byte[1000]);
  static AutoBuffer abw() { return _ab.clearForWriting((byte)0); }
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
    Key[] tests = { Key.make(), Key.make("monkey"), Key.make("ninja"), null };
    for( Key exp : tests) {
      _key = exp;
      this.write(abw());
      this. read(abr());
      Assert.assertEquals(exp, _key);
    }
  }

  @Test public void testString() throws Exception {
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

  // test that simple freezable works (gets autoserializaed correctly)
  public static class SimpleFreezableTest implements Freezable<SimpleFreezableTest>, Serializable {
    final int x;
    double y;
    String str;

    public static int DEBUG_WEAVER = 1;
    public SimpleFreezableTest(){x = -1;}
    public SimpleFreezableTest(int x, double y, String str) {this.x = x; this.y = y; this.str = str;}

    @Override public SimpleFreezableTest clone(){
      try {
        return (SimpleFreezableTest)super.clone();
      } catch (CloneNotSupportedException e) {throw water.util.Log.throwErr(e);}
    }

    @Override
    public AutoBuffer write(AutoBuffer ab) {
      return TypeMap.getIcer(this).write(ab,this);
    }

    @Override
    public SimpleFreezableTest read(AutoBuffer ab) {
      Icer icer =TypeMap.getIcer(this);
      return (SimpleFreezableTest) icer.read(ab,this);
    }

    @Override
    public AutoBuffer writeJSON(AutoBuffer ab) {
      return TypeMap.getIcer(this).writeJSON(ab,this);
    }

    @Override
    public SimpleFreezableTest readJSON(AutoBuffer ab) {
      return (SimpleFreezableTest) TypeMap.getIcer(this).read(ab,this);
    }

    @Override
    public int frozenType() {
      return TypeMap.getIcer(this).frozenType();
    }

    @Override
    public byte[] asBytes() {
      return write(new AutoBuffer()).buf();
    }

    @Override
    public SimpleFreezableTest reloadFromBytes(byte[] ary) {
      return read(new AutoBuffer(ary));
    }
  }

  // test that inheritance works
  public static class SimpleFreezableTestChild  extends SimpleFreezableTest{
    public static int DEBUG_WEAVER = 1;

    int [] intAry;
    double [] dAry;

    public SimpleFreezableTestChild(){}
    public SimpleFreezableTestChild(int x, int y, String str, int [] intAry, double [] dAry) {super(x,y,str); this.intAry = intAry; this.dAry = dAry;}
  }

  // test that custom serialization using final method flavor works
  public static class SimpleFreezableTestChild2  extends SimpleFreezableTest {
    public static int DEBUG_WEAVER = 1;

    ArrayList<Number> _nums = new ArrayList<>();

    public SimpleFreezableTestChild2() {
    }

    public SimpleFreezableTestChild2(int x, int y, String str, int[] intAry, double[] dAry) {
      super(x, y, str);
      for (int i : intAry) _nums.add(new Double(i));
      for (double d : dAry) _nums.add(new Double(d));
    }

    public final AutoBuffer write_impl(AutoBuffer ab) {
      ab.put4(_nums.size());
      for (int i = 0; i < _nums.size(); ++i)
        ab.put8d(_nums.get(i).doubleValue());
      return ab;
    }

    public final SimpleFreezableTestChild2 read_impl(AutoBuffer ab) {
      int n = ab.get4();
      _nums = new ArrayList<>();
      for (int i = 0; i < n; ++i)
        _nums.add(ab.get8d());
      return this;
    }
  }
  // test that custom serialization inheritace works
  public static class SimpleFreezableTestChild3  extends SimpleFreezableTestChild2 {
    public static int DEBUG_WEAVER = 1;

    ArrayList<Number> _nums2 = new ArrayList<>();

    public SimpleFreezableTestChild3() {}

    public SimpleFreezableTestChild3(int x, int y, String str, int[] intAry, double[] dAry) {
      super(x, y, str, intAry, dAry);
      for (int i : intAry) _nums2.add(new Double(i) * 2);
      for (double d : dAry) _nums2.add(new Double(d) * 2);
    }

    public static AutoBuffer write_impl(SimpleFreezableTestChild3 self, AutoBuffer ab) {
      ab.put4(self._nums2.size());
      for (int i = 0; i < self._nums2.size(); ++i)
        ab.put8d(self._nums2.get(i).doubleValue());
      return ab;
    }

    public static SimpleFreezableTestChild3 read_impl(SimpleFreezableTestChild3 self, AutoBuffer ab) {
      int n = ab.get4();
      self._nums2 = new ArrayList<>();
      for (int i = 0; i < n; ++i)
        self._nums2.add(ab.get8d());
      return self;
    }
  }



  @Test public void testFreezable(){
    SimpleFreezableTest a = new SimpleFreezableTest(12,345,"6789");
    SimpleFreezableTest b = new SimpleFreezableTest().read(new AutoBuffer(a.write(new AutoBuffer()).bufClose()));
    byte [] abytes = AutoBuffer.javaSerializeWritePojo(a);
    b = new AutoBuffer(new AutoBuffer().put(a).bufClose()).get();
    Assert.assertArrayEquals(abytes,AutoBuffer.javaSerializeWritePojo(b));
    byte [] jsonBytes = a.writeJSON(new AutoBuffer()).buf();
    String jsonStr = new String(jsonBytes);
    Map<String, Object> m = JSONUtils.parse(jsonStr);
    int x = (int)((Double)m.get("x")).doubleValue();
    int y = (int)((Double)m.get("y")).doubleValue();
    String str = (String) m.get("str");
    Assert.assertEquals(12,x);
    Assert.assertEquals(345,y);
    Assert.assertEquals("6789",str);
//    todo readJSON does not work, it is also not used anywehere, we should either fix it or remove it.
//    SimpleFreezableTest c = new SimpleFreezableTest().readJSON(new AutoBuffer(jsonBytes));
//    byte [] cbytes = AutoBuffer.javaSerializeWritePojo(c);
//    Assert.assertArrayEquals(abytes,cbytes);
    SimpleFreezableTestChild d = new SimpleFreezableTestChild(12,345,"6789",new int[]{10,20,30,40,50}, new double[]{10.1,10.2,10.3,10.4,10.5});
    Assert.assertArrayEquals(AutoBuffer.javaSerializeWritePojo(d),AutoBuffer.javaSerializeWritePojo(new SimpleFreezableTestChild().reloadFromBytes(d.asBytes())));
    SimpleFreezableTestChild3 e = new SimpleFreezableTestChild3(12,345,"6789",new int[]{10,20,30,40,50}, new double[]{10.1,10.2,10.3,10.4,10.5});
    SimpleFreezableTest e2 = new AutoBuffer(new AutoBuffer().put(e).buf()).get();
    Assert.assertArrayEquals(AutoBuffer.javaSerializeWritePojo(e),AutoBuffer.javaSerializeWritePojo(e2));
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
