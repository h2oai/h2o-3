package water.udf;

import com.google.common.io.Files;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.Test;
import water.fvec.Frame;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.parser.ParseTime;
import water.udf.fp.*;
import water.udf.specialized.Enums;
import water.util.StringUtils;
import water.util.TwoDimTable;

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
    long maybeSomething = ParseTime.attemptTimeParse(new BufferedString(s));
    if (maybeSomething != Long.MIN_VALUE) return new Date(maybeSomething);

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
  
  public static final Function2<Date, Date, Double> YEARS_BETWEEN = new Function2<Date, Date, Double>() {
    @Override
    public Double apply(Date from, Date to) {
      return (to.getTime() - from.getTime()) / 1000.0 / 3600 / 24 / 365.25;
    }
  };

  public static final Function2<Date, Date, Double> MONTHS_BETWEEN = new Function2<Date, Date, Double>() {
    @Override
    public Double apply(Date from, Date to) {
      return (to.getTime() - from.getTime()) / 1000.0 / 3600 / 24 / 365.25 * 12;
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
    for (int i = 1; i < 9; i++) {
      assertFalse("@" + i, dates.isNA(i));
    }

    assertEquals(new Date(1489747740567L + 7*3600*1000L), dates.apply(2));
    Column<Date> materialized = Dates.materialize(dates);

    for (int i = 0; i < inputDates.size(); i++) {
      assertEquals(dates.apply(i), materialized.apply(i));
    }

    assertEquals(Vec.T_TIME, materialized.vec().get_type());
  }

  @Test
  public void test_deltaYears() throws Exception {
    Column<String> x2 = Strings.newColumn(1200, new Function<Long, String>() {

      @Override public String apply(Long i) {
        int y = i.intValue() / 12 + 1917;
        int m = i.intValue() % 12 + 1;
        return String.format("%4d-%02d-15 04:20:00", y, m);
      }
    });

    Column<Date> d2 = new FunColumn<>(AS_DATE, x2);

    Column<Date> d1 = Dates.newColumn(1200, new Function<Long, Date>() {
      @Override public Date apply(Long i) {
        return new Date((i-365*53) * 1000L*3600*24);
      }
    });

    Column<Double> years = Doubles.materialize(new Fun2Column<>(YEARS_BETWEEN, d1, d2));
    
    Column<Double> months = Doubles.materialize(new Fun2Column<>(MONTHS_BETWEEN, d1, d2));

    Vec yv = years.vec();
    assertEquals(0.004, yv.at(0), 0.0005);
    assertEquals(88.658, yv.at(1100), 0.0005);

    Vec mv = months.vec();
    assertEquals(0.05, mv.at(0), 0.0005);
    assertEquals(1063.9, mv.at(1100), 0.001);
    Frame f = new Frame(d1.vec(), d2.vec(), years.vec(), months.vec());

    TwoDimTable tdt = f.toTwoDimTable(1198, 1200, false);
    String expected = "Frame null (1200 rows and 4 cols):\n" +
        "                   C1                   C2                 C3                  C4\n" +
        "  1920-04-25 16:00:00  2016-11-15 04:20:00  96.55719066088676  1158.6862879306411\n" +
        "  1920-04-26 16:00:00  2016-12-15 04:20:00  96.63658833371359  1159.6390600045631\n";
    assertEquals(expected, tdt.toString());
  }
}