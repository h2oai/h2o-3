package water.parser;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.TestUtil;
import water.fvec.FVecFactory;
import water.fvec.Frame;
import water.fvec.Vec;

public class ParseTimeTest extends TestUtil {
  @BeforeClass static public void setup() { stall_till_cloudsize(1); }
  private double[] d(double... ds) { return ds; }

  @Test
  public void testDateWithColonEnding() {
    long parsedMillis = ParseTime.attemptTimeParse(new BufferedString("2011-01-01:"));
    Assert.assertEquals(Long.MIN_VALUE, parsedMillis);
  }

  @Test
  public void testTimeWithColonEnding() {
    long parsedMillis = ParseTime.attemptTimeParse(new BufferedString(":"));
    Assert.assertEquals(Long.MIN_VALUE, parsedMillis);
  }

  // Parse click & query times from a subset of kaggle bestbuy data
  @Test public void testTimeParse1() {
    // File items will be converted to ms in UTC
    Frame fr = parseTestFile("smalldata/junit/test_time.csv");
    Frame fr2 = fr.subframe(new String[]{"click_time","query_time"});
    double[][] exp = new double[][] {  // These ms counts all presume UTC
      d(1314920692533L, 1314920639752L ),
      d(1315225537042L, 1315225501187L ),
      d(1314190618091L, 1314190513012L ),
      d(1319527094722L, 1319527011759L ),
      d(1319527191697L, 1319527011759L ),
      d(1315410887956L, 1315410804353L ),
      d(1316948822603L, 1316948726996L ),
      d(1316781620871L, 1316781614845L ),
      d(1314625052903L, 1314624803249L ),
      d(1319583358683L, 1319583285926L ),
      d(1315745324139L, 1315745178466L ),
      d(1318958493919L, 1318958486057L ),
      d(1315133720427L, 1315133710874L ),
      d(1319819189203L, 1319819180358L ),
      d(1318206926858L, 1318206870708L ),
      d(1316816048965L, 1316816017043L ),
      d(1315656293645L, 1315656270805L ),
      d(1319370275074L, 1319370207011L ),
      d(1319370324416L, 1319370207011L ),
    };

    ParserTest.testParsed(fr2,exp,exp.length);
    fr.delete();
  }

  @Test public void testTimeParse2() {
    double[][] exp = new double[][] {  // These ms counts all presume UTC
      d(1     ,      86400000L, 1136246400000L, 1136246400000L, 1 ),
      d(1500  ,  129600000000L, 1247616000000L, 1247616000000L, 0 ),
      d(15000 , 1296000000000L, 1254268800000L, 1254268800000L, 2 ),
      d(15000 , 1296000000000L, 1254268800000L, 1254268800000L, 2 ),
    };

    // File items will be converted to ms in UTC
    ParserTest.testParsed(parseTestFile("smalldata/junit/ven-11.csv"),exp,exp.length);
  }

  @Test public void testTimeParse3() {
    String[] data = new String[] {
        "12Jun10:10:00:00",
        "12JUN2010:10:00:00",
        "12JUN2010 10:00:00",              // Embedded blank, no  quotes
        "\"12JUN2010 10:00:00\"",          // Embedded blank, yes quotes
        "12JUN2010:10:00:00 PM",           // Embedded blank, no  quotes
        "\"12JUN2010:10:00:00 PM\"",       // Embedded blank, yes quotes
        "12JUN2010:10:00:00.123456789",
        "\"12JUN2010:10:00:00.123456789 PM\"",
        "12June2010",
        "24-MAR-14 06.10.48.000000000 PM",     // Embedded blank, no  quotes
        "\"24-MAR-14 06.10.48.000000000 PM\"", // Embedded blank, yes quotes
        "\"24-MAR-14 06.10.48.000000000PM\"",
        "\"24-MAR-14:06.10.48.123 AM\"",
        "24-MAR-14:06.10.48.123AM",
        "24-MAR-14:06.10.48.000000000",
        "\"24-MAR-14:06.10:48.000 PM\"",
        "\"24MAR14:06.10:48.000 PM\"",
        "\"4MAR2014:06.10:48.000 PM\"",  // should handle days with one digit
        "\"24MAR78:06.10:48.000 PM\"",   // should assume 1978
        "\"24MAR1968:06.10:48.000 PM\"", // should be a negative time, pre-Epoch
        "2015-12-03 15:43:21.654321 ",   // Evil trailing blank
        "\"2015-12-03 15:43:21.654321 \"", // Evil trailing blank, quoted
        "20151203-15:43:21.654",         // No dash '-' separator between yyyyMMdd, and then one between dd-HH
    };

    double[][] exp = new double[][] {  // These ms counts all presume UTC
        d(1276336800000L ),
        d(1276336800000L ),
        d(1276336800000L ),
        d(1276336800000L ),
        d(1276380000000L ),
        d(1276380000000L ),
        d(1276336800123L ),
        d(1276380000123L ),
        d(1276300800000L ),
        d(1395684648000L ),
        d(1395684648000L ),
        d(1395684648000L ),
        d(1395641448123L ),
        d(1395641448123L ),
        d(1395641448000L ),
        d(1395684648000L ),
        d(1395684648000L ),
        d(1393956648000L ),
        d(259611048000L  ),
        d(-55921752000L  ),
        d(1449157401654L ),
        d(1449157401654L ),
        d(1449157401654L ),
    };

    StringBuilder sb1 = new StringBuilder();
    for( String ds : data ) sb1.append(ds).append("\n");
    Key[] k1 = new Key[]{FVecFactory.makeByteVec(sb1.toString())};
    Key r1 = Key.make("r1");
    ParseSetup ps = ParseSetup.guessSetup(k1, false, 0);
    ps._separator = ',';
    ps._number_columns = 1;
    Frame dataFrame = ParseDataset.parse(r1, k1, true, ps);

    //File items will be converted to ms in UTC
    ParserTest.testParsed(dataFrame, exp, exp.length);
  }

  @Test public void testDayParseNoTime1() {
    // Just yyyy-mm-dd, no time
    String data = "Date\n"+
      "2014-1-23\n"+
      "2014-1-24\n"+
      "2014-1-23\n"+
      "2014-1-24\n";
    Key k1 = FVecFactory.makeByteVec(data);
    Key r1 = Key.make("r1");
    Frame fr = ParseDataset.parse(r1, k1);
    Assert.assertTrue(fr.vec(0).get_type_str().equals("Time"));
    long[] exp = new long[] {  // Date, note: these ms counts all presume PST
      1390435200000L,
      1390521600000L,
      1390435200000L,
      1390521600000L,
    };
    Vec vec = fr.vec("Date");
    for (int i=0; i < exp.length; i++ )
      Assert.assertEquals(exp[i],vec.at8(i));
    fr.delete();
  }

  @Test public void testDayParseNoTime2() {
    // Just mm/dd/yyyy, no time
    String data = "Date\n"+
      "1/23/2014  \n"+ // Note evil trailing blanks
      "1/24/2014  \n"+
      "1/23/2014 \n"+
      "1/24/2014\n";
    Key k1 = FVecFactory.makeByteVec(data);
    Key r1 = Key.make("r1");
    Frame fr = ParseDataset.parse(r1, k1);
    Assert.assertTrue(fr.vec(0).get_type_str().equals("Time"));
    long[] exp = new long[] {  // Date, note: these ms counts all presume UTC
            1390435200000L,
            1390521600000L,
            1390435200000L,
            1390521600000L,
    };
    Vec vec = fr.vec("Date");
    for (int i=0; i < exp.length; i++ )
      Assert.assertEquals(exp[i],vec.at8(i));
    fr.delete();
  }

  @Test public void testDayParseNoTime3() {
    // Just yyyy-mm, no time no day
    String data = "Date\n"+
      "2014-1\n"+
      "2014-2\n"+
      "2014-3\n"+
      "2014-4\n";
    Key k1 = FVecFactory.makeByteVec(data);
    Key r1 = Key.make("r1");
    Frame fr = ParseDataset.parse(r1, k1);
    Assert.assertTrue(fr.vec(0).get_type_str().equals("Time"));
    long[] exp = new long[] {  // Date, note: these ms counts all presume UTC
      1388534400000L,
      1391212800000L,
      1393632000000L,
      1396310400000L,
    };
    Vec vec = fr.vec("Date");
    for (int i=0; i < exp.length; i++ )
      Assert.assertEquals(exp[i],vec.at8(i));
    fr.delete();
  }

  @Test public void testMonthParseNoDay() {
    // Just mmmyy, no time no day
    // Just yy-mmm, no time no day
    String data = "Date\n"+
      "JAN14\n"+"FEB14\n"+"MAR14\n"+"APR14\n"+"MAY14\n"+"JUN14\n"+
      "JUL14\n"+"AUG14\n"+"SEP14\n"+"OCT14\n"+"NOV14\n"+"DEC14\n"+
      "JAN16\n"+"MAR17\n"+"JUN18\n"+"SEP19\n"+"DEC20\n"+
      "14-JAN\n"+"14-FEB\n"+"14-MAR\n"+"14-APR\n"+"14-MAY\n"+"14-JUN\n"+
      "14-JUL\n"+"14-AUG\n"+"14-SEP\n"+"14-OCT\n"+"14-NOV\n"+"14-DEC\n"+
      "16-JAN\n"+"17-MAR\n"+"18-JUN\n"+"19-SEP\n"+"20-DEC\n";
    Key k1 = FVecFactory.makeByteVec(data);
    Key r1 = Key.make("r1");
    Frame fr = ParseDataset.parse(r1, k1);
    Assert.assertTrue(fr.vec(0).get_type_str().equals("Time"));
    long[] exp = new long[] {  // Date, note: these ms counts all presume PST
      1388534400000L, 1391212800000L, 1393632000000L, 1396310400000L, // jan, feb, mar, apr 2014
      1398902400000L, 1401580800000L, 1404172800000L, 1406851200000L, // may, jun, jul, aug 2014
      1409529600000L, 1412121600000L, 1414800000000L, 1417392000000L, // sep, oct, nov, dec 2014
      1451606400000L, 1488326400000L, 1527811200000L, 1567296000000L, 1606780800000L, // jan 2016, mar 2017, jun 2018, sep 2019, dec 2020
      1388534400000L, 1391212800000L, 1393632000000L, 1396310400000L, // jan, feb, mar, apr 2014
      1398902400000L, 1401580800000L, 1404172800000L, 1406851200000L, // may, jun, jul, aug 2014
      1409529600000L, 1412121600000L, 1414800000000L, 1417392000000L, // sep, oct, nov, dec 2014
      1451606400000L, 1488326400000L, 1527811200000L, 1567296000000L, 1606780800000L, // jan 2016, mar 2017, jun 2018, sep 2019, dec 2020
    };
    Vec vec = fr.vec("Date");
    for (int i=0; i < exp.length; i++ )
      Assert.assertEquals(exp[i],vec.at8(i));
    fr.delete();
  }

  @Test public void testTimeParseNoDate() {
    // Just time, no date.  Parses as msec on the Unix Epoch start date, i.e. <24*60*60*1000
    // HH:mm:ss.S          // tenths second
    // HH:mm:ss.SSS        // milliseconds
    // HH:mm:ss.SSSnnnnnn  // micros and nanos also
    String data = "Time\n"+
      "0:0:0.0\n"+
      "0:54:13.0\n"+
      "10:36:2.0\n"+
      "10:36:8.0\n"+
      "10:37:49.0\n"+
      "11:18:48.0\n"+
      "11:41:34.0\n"+
      "11:4:49.0\n"+
      "12:47:41.0\n"+
      "3:24:19.0\n"+
      "3:45:55.0\n"+
      "3:45:56.0\n"+
      "3:58:24.0\n"+
      "6:13:55.0\n"+
      "6:25:14.0\n"+
      "7:0:15.0\n"+
      "7:3:8.0\n"+
      "8:20:8.0\n";
    Key k1 = FVecFactory.makeByteVec(data);
    Key r1 = Key.make("r1");
    Frame fr = ParseDataset.parse(r1, k1);
    Assert.assertTrue(fr.vec(0).get_type_str().equals("Time"));
    long[] exp = new long[] {  // Time
      0L,                      // Notice: no TZ at all ==> GMT!
      3253000L,
      38162000L,
      38168000L,
      38269000L,
      40728000L,
      42094000L,
      39889000L,
      46061000L,
      12259000L,
      13555000L,
      13556000L,
      14304000L,
      22435000L,
      23114000L,
      25215000L,
      25388000L,
      30008000L,
    };
    Vec vec = fr.vec("Time");
    for (int i=0; i < exp.length; i++ )
      Assert.assertEquals(exp[i],vec.at8(i));
    fr.delete();
  }

  @Test public void testParseInvalidPubDev3675() {
    long millis = ParseTime.attemptTimeParse(new BufferedString("XXXX0101"));
    Assert.assertEquals("Expected Long.MIN_VALUE as a marker of invalid date", Long.MIN_VALUE, millis);
  }

}
