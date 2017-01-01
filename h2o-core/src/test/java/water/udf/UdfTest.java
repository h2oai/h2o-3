package water.udf;

import com.google.common.io.Files;
import org.junit.Test;
import water.udf.fp.Function;
import water.udf.fp.Predicate;
import water.udf.fp.PureFunctions;
import water.udf.specialized.Enums;
import water.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;

import static org.junit.Assert.*;
import static water.udf.specialized.Dates.*;
import static water.udf.specialized.Doubles.*;
import static water.udf.specialized.Strings.*;
/**
 * Test for UDF
 */
public class UdfTest extends UdfTestBase {
  
  int requiredCloudSize() { return 3; }
  
  private DataColumn<Double> sines() throws java.io.IOException {
    return willDrop(Doubles.newColumn(1 << 20, new Function<Long, Double>() {
      public Double apply(Long i) { return (i > 10 && i < 20) ? null : Math.sin(i); }
    }));
  }

  private DataColumn<Double> sinesShort() throws java.io.IOException {
    return willDrop(Doubles.newColumn(1001590, new Function<Long, Double>() {
      public Double apply(Long i) { return (i > 10 && i < 20) ? null : Math.sin(i); }
    }));
  }

  private DataColumn<Double> five_x() throws java.io.IOException {
    return willDrop(Doubles.newColumn(1 << 20, new Function<Long, Double>() {
      public Double apply(Long i) { return i*5.0; }
    }));
  }

  @Test
  public void testIsNA() throws Exception {
    Column<Double> c = sines();

    assertFalse(c.apply(10).isNaN());
    Double x11 = c.apply(11);
    assertTrue(x11.isNaN());
    assertTrue(c.apply(19).isNaN());
    assertFalse(c.apply(20).isNaN());
    assertFalse(c.isNA(10));
    assertTrue(c.isNA(11));
    assertTrue(c.isNA(19));
    assertFalse(c.isNA(20));
  }

  @Test
  public void testOfDoubles() throws Exception {
    Column<Double> c = five_x();

    assertEquals(0.0, c.apply(0), 0.000001);
    assertEquals(210.0, c.apply(42), 0.000001);
    final long position = c.index2position(20000);
    
    assertEquals("@" + Long.toHexString(position), 20000*5.0, c.apply(position), 0.000001);
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
    Column<String> materialized = Strings.materialize(c);

    for (int i = 0; i < 100000; i++) {
      long coord = c.index2position(i);
      
      assertEquals(c.apply(coord), materialized.apply(coord));
    }
  }

  @Test
  public void testOfEnums() throws Exception {
    DataColumn<Integer> c = willDrop(Enums.enums(new String[] {"Red", "White", "Blue"})
        .newColumn(1 << 20, new Function<Long, Integer>() {
       public Integer apply(Long i) {
         return (int)( i % 3);
       }
    }));
    assertEquals(0, c.apply(0).intValue());
    assertEquals(0, c.apply(42).intValue());
    assertEquals(1, c.apply(100).intValue());
    assertEquals(1, c.getByIndex(1000).intValue());
    assertEquals(2, c.getByIndex(2000).intValue());
    assertEquals(2, c.getByIndex(5000).intValue());

    final long i1 = c.index2position(10000);
    final int expected = (int) (i1 % 3);
    assertEquals("At " + Long.toHexString(i1), 1, c.apply(i1).intValue());
    assertEquals(2, c.getByIndex(20000).intValue());

    Column<Integer> materialized = Enums.enums(new String[] {"Red", "White", "Blue"}).materialize(c);

    for (int i = 0; i < 100000; i++) {
      final long position = c.index2position(i);
      
      int ci = (int)(position << 32);
      int ic = (int)position & Integer.MAX_VALUE;

//      Chunk ch = c.vec().chunkForRow((int)(i >> 32));
//      long c2 = DataChunk.positionOf(i, ch.cidx(), ch.start());
//      assertEquals(position, c2);
      assertTrue(" got " + ic + " for " + ci + " from " + Long.toHexString(position) + " at " + i, ic < 0x8000);
      
      assertEquals(c.apply(position), materialized.apply(position));
    }
  }

  @Test
  public void testOfDates() throws Exception {
    Column<Date> c = willDrop(Dates.newColumn(1 << 20, new Function<Long, Date>() {
       public Date apply(Long i) {
         return new Date(i*3600000L*24);
      }
    }));
    assertEquals(new Date(0), c.apply(0));
    assertEquals(new Date(258 * 24 * 3600 * 1000L), c.apply(258));

    Column<Date> materialized = Dates.materialize(c);
    int i = 0;
    for (long coord : c.positions()) {
      final Date expected = c.apply(coord);
      final Date actual = materialized.apply(coord);

      assertEquals("@" + i + "/" + Long.toHexString(coord), expected, actual);
      if (i++>10000) break;
    }
  }

//// All UUID functionality is currently disabled
//  @Test
//  public void testOfUUIDs() throws Exception {
//    Column<UUID> c = willDrop(UUIDs.newColumn(1 << 20, new Function<Long, UUID>() {
//       public UUID apply(Long i) {
//        return new UUID(i * 7, i * 13);
//      }
//    }));
//    assertEquals(new UUID(0, 0), c.apply(0));
//    assertEquals(new UUID(258*7, 258*13), c.apply(258));
//
//    Column<UUID> materialized = UUIDs.materialize(c);
//
//    for (int i = 0; i < 100000; i++) {
//      assertEquals(c.apply(i), materialized.apply(i));
//    }
//  }

  @Test
  public void testOfEnumFun() throws Exception {
    final String[] domain = {"Red", "White", "Blue"};
    Column<Integer> x = willDrop(Enums.enums(domain)
        .newColumn(1 << 20, new Function<Long, Integer>() {
           public Integer apply(Long i) { return (int)( i % 3); }
        }));

    Column<String> y = new FunColumn<>(new Function<Integer, String>() {
      public String apply(Integer i) { return domain[i]; }
    }, x);
    
    assertEquals("Red", y.apply(0));
    assertEquals("Red", y.apply(42));
    assertEquals("White", y.apply(100));
    assertEquals("Blue", y.getByIndex(20000));
  }

  @Test
  public void testOfSquares() throws Exception {
    Column<Double> x = five_x();

    Column<Double> y = new FunColumn<>(PureFunctions.SQUARE, x);

    assertEquals(0.0, y.apply(0), 0.000001);
    assertEquals(44100.0, y.apply(42), 0.000001);
    final long position = y.index2position(20000);
    assertEquals(25.0*20000*20000, y.apply(position), 0.000001);
  }

  @Test
  public void testIsFunNA() throws Exception {
    Column<Double> x = sines();

    Column<Double> y = new FunColumn<>(PureFunctions.SQUARE, x);

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
    Column<Double> y2 = willDrop(new FunColumn<>(PureFunctions.SQUARE, y));
    Column<Double> z1 = willDrop(new Fun2Column<>(PureFunctions.PLUS, x, y2));
    Column<Double> z2 = willDrop(new Fun2Column<>(PureFunctions.X2_PLUS_Y2, x, y));
    
    assertEquals(0.0, z1.apply(0), 0.000001);
    assertEquals(210.84001174779368, z1.apply(42), 0.000001);
    final long position = z1.index2position(20000);
    assertEquals(100000.3387062632, z1.apply(position), 0.000001);

    assertEquals(0.0, z2.apply(0), 0.000001);
    assertEquals(44100.840011747794, z2.apply(42), 0.000001);
    assertEquals(1000.0, x.getByIndex(200), 0.000001);
    assertEquals(10000.0, x.getByIndex(2000), 0.000001);
    assertEquals(100000.0, x.getByIndex(20000), 0.000001);
    assertEquals(0.3387062632, y2.getByIndex(20000), 0.000001);
    assertEquals(10000000000.3387062632, z2.getByIndex(20000), 0.000001);

    Column<Double> materialized = willDrop(Doubles.materialize(z2));

    for (int i = 0; i < 100000; i++) {
      long j = z2.index2position(i);
      Double expected = z2.apply(j);
      assertEquals(z2.isNA(j), materialized.isNA(j));
      // the following exposes a problem. nulls being returned.
      if (expected == null) assertTrue("At " + i + "/" + Long.toHexString(j) +":", materialized.isNA(j));
      Double actual = materialized.apply(j);
      
      if (!z2.isNA(j)) assertEquals(expected, actual, 0.0001);
    }
  }

  @Test
  public void testFun2Compatibility() throws Exception {
    Column<Double> x = five_x();
    Column<Double> y = sinesShort();
    Column<Double> z = willDrop(Doubles.newColumn(1 << 20, new Function<Long, Double>() {
      public Double apply(Long i) { return Math.sin(i*0.0001); }
    }));

    try {
      Column<Double> z1 = new Fun2Column<>(PureFunctions.PLUS, x, y);
      fail("Column incompatibility should be detected");
    } catch (AssertionError ae) {
      // as designed
    }

    try {
      Column<Double> r = new Fun3Column<>(PureFunctions.X2_PLUS_Y2_PLUS_Z2, x, y, z);
      fail("Column incompatibility should be detected");
    } catch (AssertionError ae) {
      // as designed
    }
    
    try {
      Column<Double> r = new Fun3Column<>(PureFunctions.X2_PLUS_Y2_PLUS_Z2, x, z, y);
      fail("Column incompatibility should be detected");
    } catch (AssertionError ae) {
      // as designed
    }
  }

  @Test
  public void testFun2CompatibilityWithConst() throws Exception {
    Column<Double> x = five_x();
    Column<Double> y = Doubles.constColumn(42.0, 1 << 20);
    Column<Double> z = willDrop(Doubles.newColumn(1 << 20, new Function<Long, Double>() {
      public Double apply(Long i) { return Math.sin(i*0.0001); }
    }));

    try {
      Column<Double> z1 = new Fun2Column<>(PureFunctions.PLUS, x, y);
      fail("Column incompatibility should be detected");
    } catch (AssertionError ae) {
      // as designed
    }

    try {
      Column<Double> r = new Fun3Column<>(PureFunctions.X2_PLUS_Y2_PLUS_Z2, x, y, z);
      fail("Column incompatibility should be detected");
    } catch (AssertionError ae) {
      // as designed
    }

    try {
      Column<Double> r = new Fun3Column<>(PureFunctions.X2_PLUS_Y2_PLUS_Z2, x, z, y);
      fail("Column incompatibility should be detected");
    } catch (AssertionError ae) {
      // as designed
    }
  }

  @Test
  public void testFun3() throws Exception {
    Column<Double> xs = willDrop(Doubles.newColumn(1 << 20, new Function<Long, Double>() {
        public Double apply(Long i) { return Math.cos(i*0.0001)*Math.cos(i*0.0000001); }
      }));

    Column<Double> ys = willDrop(Doubles.newColumn(1 << 20, new Function<Long, Double>() {
      public Double apply(Long i) { return Math.cos(i*0.0001)*Math.sin(i*0.0000001); }
    }));

    Column<Double> zs = willDrop(Doubles.newColumn(1 << 20, new Function<Long, Double>() {
      public Double apply(Long i) { return Math.sin(i*0.0001); }
    }));

    Column<Double> rs = new Fun3Column<>(PureFunctions.X2_PLUS_Y2_PLUS_Z2, xs, ys, zs);

    for (int i = 0; i < 100000; i++) {
      long j = rs.index2position(i*10);
      final Double x = xs.apply(j);
      final Double y = ys.apply(j);
      final Double z = zs.apply(j);
      final Double r = rs.apply(j);
      assertNotNull("@" + i + "/" + Long.toHexString(j) + " from " + x +"," +y+","+z + "->" + r, r);
      assertEquals(1.00, r, 0.0001);
    }

    Column<Double> materialized = Doubles.materialize(rs);

    for (int i = 0; i < 100000; i++) {
      long j = rs.index2position(i*10);
      assertEquals(rs.apply(j), materialized.apply(j), 0.0001);
    }
  }

  @Test
  public void testFoldingColumn() throws Exception {
    Column<Double> x = willDrop(Doubles.newColumn(1 << 20, new Function<Long, Double>() {
      public Double apply(Long i) { return Math.cos(i*0.0001)*Math.cos(i*0.0000001); }
    }));

    Column<Double> y = willDrop(Doubles.newColumn(1 << 20, new Function<Long, Double>() {
      public Double apply(Long i) { return Math.cos(i*0.0001)*Math.sin(i*0.0000001); }
    }));

    Column<Double> z = willDrop(Doubles.newColumn(1 << 20, new Function<Long, Double>() {
      public Double apply(Long i) { return Math.sin(i*0.0001); }
    }));

    Column<Double> r = new FoldingColumn<>(PureFunctions.SUM_OF_SQUARES, x, y, z);

    for (int i = 0; i < 100000; i++) {
      long j = r.index2position(i*10);
      assertEquals(1.00, r.apply(j), 0.0001);
    }

    Column<Double> x1 = new FoldingColumn<>(PureFunctions.SUM_OF_SQUARES, x);

    for (int i = 0; i < 100000; i++) {
      double xi = x.apply(i);
      assertEquals(xi*xi, x1.apply(i), 0.0001);
    }

    try {
      Column<Double> x0 = new FoldingColumn<>(PureFunctions.SUM_OF_SQUARES);
      fail("This should have failed - no empty foldings");
    } catch (AssertionError ae) {
      // good, good!
    }
    
    Column<Double> materialized = Doubles.materialize(r);

    for (int i = 0; i < 100000; i++) {
      long j = r.index2position(i);
      assertEquals(r.apply(j), materialized.apply(j), 0.0001);
    }
  }
  @Test
  public void testFoldingColumnCompatibility() throws Exception {
    Column<Double> x = willDrop(Doubles.newColumn(1 << 20, new Function<Long, Double>() {
      public Double apply(Long i) { return Math.cos(i*0.0001)*Math.cos(i*0.0000001); }
    }));

    Column<Double> y = willDrop(Doubles.newColumn(1 << 20, new Function<Long, Double>() {
      public Double apply(Long i) { return Math.cos(i*0.0001)*Math.sin(i*0.0000001); }
    }));

    Column<Double> z = sinesShort();

    try {
      Column<Double> r = new FoldingColumn<>(PureFunctions.SUM_OF_SQUARES, x, y, z);
      fail("Should have failed on incompatibility");
    } catch(AssertionError ae) {
      // as expected
    }
  }

  // test how file can be unfolded into multiple columns
  @Test public void testUnfoldingColumn() throws IOException {
    // here's the file
    File file = getFile("smalldata/chicago/chicagoAllWeather.csv");

    // get all its lines
    final List<String> lines = Files.readLines(file, Charset.defaultCharset());

    // store it in H2O, with typed column as a wrapper (core H2O storage is a type-unaware Vec class)
    DataColumn<String> source = willDrop(Strings.newColumn(lines));

    // produce another (virtual) column that stores a list of strings as a row value
    Column<List<String>> split = new UnfoldingColumn<>(PureFunctions.splitBy(","), source, 10);

    // now check that we have the right data
    for (int i = 0; i < lines.size(); i++) {
      long j = split.index2position(i);
      long j1 = source.index2position(i);
      assertEquals(j, j1);

      // since we specified width (10), the rest of the list is filled with nulls; have to ignore them. 
      // It's important to have the same width for the whole frame.
      final List<String> found = split.apply(j);
      String actual = StringUtils.join(" ", Predicate.NOT_NULL.filter(found));
      // so, have we lost any data?
      final String expected = lines.get(i).replaceAll("\\,", " ").trim();
      assertEquals("At " + i + "/" + Long.toHexString(j) + "... " + source.get(j1), expected, actual);
    }
  }

  @Test
  public void testUnfoldingFrame() throws IOException {
    File file = getFile("smalldata/chicago/chicagoAllWeather.csv");
    final List<String> lines = Files.readLines(file, Charset.defaultCharset());
    Column<String> source = willDrop(Strings.newColumn(lines));
    Column<List<String>> split = new UnfoldingColumn<>(PureFunctions.splitBy(","), source, 10);
    UnfoldingFrame<String> frame = new UnfoldingFrame<>(Strings, split.size(), split, 11);
    List<DataColumn<String>> columns = frame.materialize();
    
    for (int i = 0; i < lines.size(); i++) {
      List<String> fromColumns = new ArrayList<>(10);
      long coord = columns.get(0).index2position(i);
      for (int j = 0; j < 10; j++) {
        String value = columns.get(j).get(coord);
        if (value != null) fromColumns.add(value);
      }
      String actual = StringUtils.join(" ", fromColumns);
      final String expected = lines.get(i).replaceAll("\\,", " ").trim();
      assertEquals("At " + i, expected, actual);
    }
    
    assertTrue("Need to align the result", columns.get(5).isCompatibleWith(source));
  }
}