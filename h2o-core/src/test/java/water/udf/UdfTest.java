package water.udf;

import com.google.common.io.Files;
import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Chunk;
import water.fvec.Vec;
import water.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.GregorianCalendar;

import static org.junit.Assert.*;
import static water.udf.DataColumns.*;

/**
 * Test for UDF
 */
public class UdfTest extends TestUtil {

  @BeforeClass
  static public void setup() {  stall_till_cloudsize(1); }

  private TypedVector<Double> sines() throws java.io.IOException {
    return willDrop(Doubles.newColumn(1 << 20, new Function<Long, Double>() {
      public Double apply(Long i) { return (i > 10 && i < 20) ? null : Math.sin(i); }
    }));
  }

  private TypedVector<Double> five_x() throws java.io.IOException {
    return willDrop(Doubles.newColumn(1 << 20, new Function<Long, Double>() {
      public Double apply(Long i) { return i*5.0; }
    }));
  }

  @Test
  public void testIsNA() throws Exception {
    Column<Double> c = sines();

    assertFalse(c.apply(10).isNaN());
    assertTrue(c.apply(11).isNaN());
    assertTrue(c.apply(19).isNaN());
    assertFalse(c.apply(20).isNaN());
    assertFalse(c.isNA(10));
    assertTrue(c.isNA(11));
    assertTrue(c.isNA(19));
    assertFalse(c.isNA(20));
  }

  @Test public void testGetString() throws Exception {
    Column<Double> c = willDrop(Doubles.newColumn(1 << 20, new Function<Long, Double>() {
       public Double apply(Long i) {
        return (i > 10 && i < 20) ? null : i*10.0;
      }
    }));

    assertEquals("100.0", c.getString(10));
    assertEquals("(N/A)", c.getString(12));
    assertEquals("(N/A)", c.getString(18));
    assertEquals("123450.0", c.getString(12345));
  }

  @Test
  public void testOfDoubles() throws Exception {
    Column<Double> c = five_x();

    assertEquals(0.0, c.apply(0), 0.000001);
    assertEquals(210.0, c.apply(42), 0.000001);
    assertEquals(100000.0, c.apply(20000), 0.000001);
  }

  @Test
  public void testOfStrings() throws Exception {
    Column<String> c = willDrop(Strings.newColumn(1 << 20, new Function<Long, String>() {
       public String apply(Long i) {
        return i == 42 ? null : "<<" + i + ">>";
      }
    }));
    assertEquals("<<0>>", c.apply(0));
    assertEquals(null, c.apply(42));
    assertEquals("<<2016>>", c.apply(2016));
  }

  @Test
  public void testOfEnums() throws Exception {
    Column<Integer> c = willDrop(Enums
        .newColumn(1 << 20, new String[] {"Red", "White", "Blue"}, new Function<Long, Integer>() {
       public Integer apply(Long i) { return (int)( i % 3); }
    }));
    assertEquals(0, c.apply(0).intValue());
    assertEquals(0, c.apply(42).intValue());
    assertEquals(1, c.apply(100).intValue());
    assertEquals(2, c.apply(20000).intValue());
  }

  @Test
  public void testOfDates() throws Exception {
    Column<Date> c = willDrop(Dates.newColumn(1 << 20, new Function<Long, Date>() {
       public Date apply(Long i) {
        return new Date(i*3600000L*24);
      }
    }));
    assertEquals(new Date(0), c.apply(0));
    Date expected = new GregorianCalendar(1970, 8, 15, 17, 0, 0).getTime();
    assertEquals(expected, c.apply(258));
  }

  @Test
  public void testOfUUIDs() throws Exception {
    Column<UUID> c = willDrop(UUIDs.newColumn(1 << 20, new Function<Long, UUID>() {
       public UUID apply(Long i) {
        return new UUID(i * 7, i * 13);
      }
    }));
    assertEquals(new UUID(0, 0), c.apply(0));
    assertEquals(new UUID(258*7, 258*13), c.apply(258));
  }

  @Test
  public void testOfEnumFun() throws Exception {
    final String[] domain = {"Red", "White", "Blue"};
    Column<Integer> x = willDrop(Enums
        .newColumn(1 << 20, domain, new Function<Long, Integer>() {
           public Integer apply(Long i) { return (int)( i % 3); }
        }));

    ChunkFactory<TypedChunk<String>> factory = new ChunkFactory<TypedChunk<String>>() {
      @Override
      public byte typeCode() {
        return Vec.T_STR;
      }

      @Override
      public TypedChunk<String> apply(Chunk chunk) {
        return Strings.apply(chunk);
      }
    };
    Column<String> y = new FunColumn<>(new Function<Integer, String>() {
      public String apply(Integer i) { return domain[i]; }
    }, x);
    
    assertEquals("Red", y.apply(0));
    assertEquals("Red", y.apply(42));
    assertEquals("White", y.apply(100));
    assertEquals("Blue", y.apply(20000));
  }

  @Test
  public void testOfSquares() throws Exception {
    Column<Double> x = five_x();

    Column<Double> y = new FunColumn<>(Functions.SQUARE, x);

    assertEquals(0.0, y.apply(0), 0.000001);
    assertEquals(44100.0, y.apply(42), 0.000001);
    assertEquals(10000000000.0, y.apply(20000), 0.000001);
  }

  @Test
  public void testIsFunNA() throws Exception {
    Column<Double> x = sines();

    Column<Double> y = new FunColumn<>(Functions.SQUARE, x);

    assertFalse(y.isNA(10));
    assertTrue(y.isNA(11));
    assertTrue(y.isNA(19));
    assertFalse(y.isNA(20));
    assertEquals(0.295958969093304, y.apply(10), 0.0001);
  }

  @Test
  public void testFun2() throws Exception {
    Column<Double> x = five_x();
    Column<Double> y = sines();
    Column<Double> y2 = new FunColumn<>(Functions.SQUARE, y);
    Column<Double> z1 = new Fun2Column<>(Functions.PLUS, x, y2);
    Column<Double> z2 = new Fun2Column<>(Functions.X2_PLUS_Y2, x, y);
    
    assertEquals(0.0, z1.apply(0), 0.000001);
    assertEquals(210.84001174779368, z1.apply(42), 0.000001);
    assertEquals(100000.3387062632, z1.apply(20000), 0.000001);

    assertEquals(0.0, z2.apply(0), 0.000001);
    assertEquals(44100.840011747794, z2.apply(42), 0.000001);
    assertEquals(10000000000.3387062632, z2.apply(20000), 0.000001);
  }

  @Test
  public void testFun3() throws Exception {
    Column<Double> x = willDrop(Doubles.newColumn(1 << 20, new Function<Long, Double>() {
        public Double apply(Long i) { return Math.cos(i*0.0001)*Math.cos(i*0.0000001); }
      }));

    Column<Double> y = willDrop(Doubles.newColumn(1 << 20, new Function<Long, Double>() {
      public Double apply(Long i) { return Math.cos(i*0.0001)*Math.sin(i*0.0000001); }
    }));

    Column<Double> z = willDrop(Doubles.newColumn(1 << 20, new Function<Long, Double>() {
      public Double apply(Long i) { return Math.sin(i*0.0001); }
    }));

    Column<Double> r = new Fun3Column<>(Functions.X2_PLUS_Y2_PLUS_Z2, x, y, z);

    for (int i = 0; i < 100000; i++) {
      assertEquals(1.00, r.apply(i*10), 0.0001);
    }
  }

  @Test
  public void testFunN() throws Exception {
    Column<Double> x = willDrop(Doubles.newColumn(1 << 20, new Function<Long, Double>() {
      public Double apply(Long i) { return Math.cos(i*0.0001)*Math.cos(i*0.0000001); }
    }));

    Column<Double> y = willDrop(Doubles.newColumn(1 << 20, new Function<Long, Double>() {
      public Double apply(Long i) { return Math.cos(i*0.0001)*Math.sin(i*0.0000001); }
    }));

    Column<Double> z = willDrop(Doubles.newColumn(1 << 20, new Function<Long, Double>() {
      public Double apply(Long i) { return Math.sin(i*0.0001); }
    }));

    Column<Double> r = new FoldingColumn<>(Functions.SUM_OF_SQUARES, x, y, z);

    for (int i = 0; i < 100000; i++) {
      assertEquals(1.00, r.apply(i*10), 0.0001);
    }

    Column<Double> x1 = new FoldingColumn<>(Functions.SUM_OF_SQUARES, x);

    for (int i = 0; i < 100000; i++) {
      double xi = x.apply(i);
      assertEquals(xi*xi, x1.apply(i), 0.0001);
    }

// empty collection currently does not work, sorry
//    Column<Double> x0 = new FoldingColumn<>(Functions.SUM_OF_SQUARES);
//
//    for (int i = 0; i < 100000; i++) {
//      assertEquals(0., x0.get(i), 0.0001);
//    }
    
    Column<Double> materialized = DataColumns.materialize(r, Doubles);

    for (int i = 0; i < 100000; i++) {
      assertEquals(r.apply(i), materialized.apply(i), 0.0001);
    }
  }

  @Test
  public void testUnfoldingColumn() throws IOException {
    File file = getFile("smalldata/chicago/chicagoAllWeather.csv");
    final List<String> lines = Files.readLines(file, Charset.defaultCharset());
    Column<String> source = willDrop(Strings.newColumn(lines));
    Column<List<String>> split = new UnfoldingColumn<>(Functions.splitBy(","), source, 10);
    for (int i = 0; i < lines.size(); i++) {
      System.out.println(StringUtils.join(" ", split.apply(i)));
    }
  }

  @Test
  public void testOfTypedFrame2() throws Exception {
    Column<Double> x = five_x();

    Column<Double> y = new FunColumn<>(Functions.SQUARE, x);

    TypedFrame2<Double, Double> f2 = new TypedFrame2<>(x, Functions.SQUARE);
    
    assertEquals(0.0, y.apply(0), 0.000001);
    assertEquals(44100.0, y.apply(42), 0.000001);
    assertEquals(10000000000.0, y.apply(20000), 0.000001);
  }

}