package water.udf;

import com.google.common.io.Files;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.Test;
import water.udf.fp.FP;
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
import static water.udf.specialized.Dates.Dates;
import static water.udf.specialized.Doubles.Doubles;
import static water.udf.specialized.Strings.Strings;
import static water.util.FileUtils.getFile;

/**
 * Test for UDF
 */
public class PUBDEF4159Test extends UdfTestBase {
  
  int requiredCloudSize() { return 1; }
  
  List<String> inputDates = Arrays.asList(
      "my first date",
      "2017-05-28 23:15:42.123",
      "2017-03-17 10:49:00.567", //#2
      "192005",
      "200101",
      "200202",
      "20151010",
      "20151011",
      "20151122",
      "my last date" //#9
  );

  static List<String> dateFormats = Arrays.asList(
        "YYYY-MM-dd HH:mm:ss.SSS",
        "YYYYMMdd",
        "YYYYMM"
      );

  static List<DateTimeFormatter> dtfs = new LinkedList<DateTimeFormatter>() {{
    for (String f : dateFormats) add(DateTimeFormat.forPattern(f));
  }};
  
  private static Date d(String s) {
    for (DateTimeFormatter dtf : dtfs) 
      try {
        return dtf.parseDateTime(s).toDate();
      } catch (Exception ignored) {}
    
    return null;
  }

  public static final Function<String, Date> AS_DATE = new Function<String, Date>() {
    @Override public Date apply(String s) {
      return d(s);
    }
  };


  private DataColumn<String> source() throws IOException {
    return willDrop(Strings.newColumn(inputDates));
  }

  @Test
  public void testProduceDates() throws Exception {
    
    Column<String> source = source();
    Column<Date> dates = new FunColumn<>(AS_DATE, source);
    final boolean na_0 = dates.isNA(0);
    assertTrue(na_0);
    assertTrue(dates.isNA(9));
    for (int i = 1; i < 9; i++) assertFalse("@" + i, dates.isNA(i));

    assertEquals(new Date(1489747740567L + 7*3600*1000L), dates.apply(2));
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

    for (int i = 0; i < 100000; i++) {
      assertEquals(c.apply(i), materialized.apply(i));
    }
  }

//  @Test
//  public void testOfSquares() throws Exception {
//    Column<Double> x = five_x();
//
//    Column<Double> y = new FunColumn<>(PureFunctions.SQUARE, x);
//
//    assertEquals(0.0, y.apply(0), 0.000001);
//    assertEquals(44100.0, y.apply(42), 0.000001);
//    assertEquals(10000000000.0, y.apply(20000), 0.000001);
//  }
//
//  @Test
//  public void testFun2CompatibilityWithConst() throws Exception {
//    Column<Double> x = five_x();
//    Column<Double> y = Doubles.constColumn(42.0, 1 << 20);
//    Column<Double> z = willDrop(Doubles.newColumn(1 << 20, new Function<Long, Double>() {
//      public Double apply(Long i) { return Math.sin(i*0.0001); }
//    }));
//
//    try {
//      Column<Double> z1 = new Fun2Column<>(PureFunctions.PLUS, x, y);
//      fail("Column incompatibility should be detected");
//    } catch (AssertionError ae) {
//      // as designed
//    }
//
//    try {
//      Column<Double> r = new Fun3Column<>(PureFunctions.X2_PLUS_Y2_PLUS_Z2, x, y, z);
//      fail("Column incompatibility should be detected");
//    } catch (AssertionError ae) {
//      // as designed
//    }
//
//    try {
//      Column<Double> r = new Fun3Column<>(PureFunctions.X2_PLUS_Y2_PLUS_Z2, x, z, y);
//      fail("Column incompatibility should be detected");
//    } catch (AssertionError ae) {
//      // as designed
//    }
//  }

//  @Test
//  public void testUnfoldingFrame() throws IOException {
//    File file = getFile("smalldata/chicago/chicagoAllWeather.csv");
//    final List<String> lines = Files.readLines(file, Charset.defaultCharset());
//    Column<String> source = willDrop(Strings.newColumn(lines));
//    Column<List<String>> split = new UnfoldingColumn<>(PureFunctions.splitBy(","), source, 10);
//    UnfoldingFrame<String> frame = new UnfoldingFrame<>(Strings, split.size(), split, 11);
//    List<DataColumn<String>> columns = frame.materialize();
//    
//    for (int i = 0; i < lines.size(); i++) {
//      List<String> fromColumns = new ArrayList<>(10);
//      for (int j = 0; j < 10; j++) {
//        String value = columns.get(j).get(i);
//        if (value != null) fromColumns.add(value);
//      }
//      String actual = StringUtils.join(" ", fromColumns);
//      assertEquals(lines.get(i).replaceAll("\\,", " ").trim(), actual);
//    }
//    
//    assertTrue("Need to align the result", columns.get(5).isCompatibleWith(source));
//  }
}