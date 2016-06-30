package water.parser;

import org.junit.*;

import org.joda.time.DateTimeZone;

import water.Key;
import water.TestUtil;
import water.fvec.*;

public class ParseTimeTest extends TestUtil {
  @BeforeClass static public void setup() { stall_till_cloudsize(1); }
  private double[] d(double... ds) { return ds; }

  // Parse click & query times from a subset of kaggle bestbuy data
  @Test public void testTimeParse1() {
    //File items will be converted to ms for local timezone
    Frame fr = parse_test_file("smalldata/junit/test_time.csv");
    Frame fr2 = fr.subframe(new String[]{"click_time","query_time"});
    DateTimeZone pst = DateTimeZone.forID("America/Los_Angeles");
    DateTimeZone localTZ = DateTimeZone.getDefault();
    double[][] exp = new double[][] {  // These ms counts all presume PST
      d(1314945892533L, 1314945839752L ),
      d(1315250737042L, 1315250701187L ),
      d(1314215818091L, 1314215713012L ),
      d(1319552294722L, 1319552211759L ),
      d(1319552391697L, 1319552211759L ),
      d(1315436087956L, 1315436004353L ),
      d(1316974022603L, 1316973926996L ),
      d(1316806820871L, 1316806814845L ),
      d(1314650252903L, 1314650003249L ),
      d(1319608558683L, 1319608485926L ),
      d(1315770524139L, 1315770378466L ),
      d(1318983693919L, 1318983686057L ),
      d(1315158920427L, 1315158910874L ),
      d(1319844389203L, 1319844380358L ),
      d(1318232126858L, 1318232070708L ),
      d(1316841248965L, 1316841217043L ),
      d(1315681493645L, 1315681470805L ),
      d(1319395475074L, 1319395407011L ),
      d(1319395524416L, 1319395407011L ),
    };

    for (int i=0; i < exp.length; i++ ) // Adjust exp[][] to local time
      for (int j=0; j < exp[i].length; j++)
        exp[i][j] += pst.getOffset((long)exp[i][j]) - localTZ.getOffset((long)exp[i][j]);
    ParserTest.testParsed(fr2,exp,exp.length);
    fr.delete();
  }

  @Test public void testTimeParse2() {
    DateTimeZone pst = DateTimeZone.forID("America/Los_Angeles");
    DateTimeZone localTZ = DateTimeZone.getDefault();
    double[][] exp = new double[][] {  // These ms counts all presume PST
      d(1     ,     115200000L, 1136275200000L, 1136275200000L, 1 ),
      d(1500  ,  129625200000L, 1247641200000L, 1247641200000L, 0 ),
      d(15000 , 1296028800000L, 1254294000000L, 1254294000000L, 2 ),
      d(15000 , 1296028800000L, 1254294000000L, 1254294000000L, 2 ),
    };

    for (int i=0; i < exp.length; i++ )  // Adjust exp[][] to local time
      for (int j=1; j < 4; j++)
        exp[i][j] += pst.getOffset((long)exp[i][j]) - localTZ.getOffset((long)exp[i][j]);
    //File items will be converted to ms for local timezone
    ParserTest.testParsed(parse_test_file("smalldata/junit/ven-11.csv"),exp,exp.length);
  }

  @Test public void testTimeParse3() {
    DateTimeZone pst = DateTimeZone.forID("America/Los_Angeles");
    DateTimeZone localTZ = DateTimeZone.getDefault();
    String[] data = new String[] {
        "12Jun10:10:00:00",
        "12JUN2010:10:00:00",
        "\"12JUN2010 10:00:00\"",
        "\"12JUN2010:10:00:00 PM\"",
        "12JUN2010:10:00:00.123456789",
        "\"12JUN2010:10:00:00.123456789 PM\"",
        "12June2010",
        "\"24-MAR-14 06.10.48.000000000 PM\"",
        "\"24-MAR-14 06.10.48.000000000PM\"",
        "\"24-MAR-14:06.10.48.123 AM\"",
        "24-MAR-14:06.10.48.123AM",
        "24-MAR-14:06.10.48.000000000",
        "\"24-MAR-14:06.10:48.000 PM\"",
        "\"24MAR14:06.10:48.000 PM\"",
        "\"4MAR2014:06.10:48.000 PM\"",  // should handle days with one digit
        "\"24MAR78:06.10:48.000 PM\"",   // should assume 1978
        "\"24MAR1968:06.10:48.000 PM\"",   // should be a negative time, pre-Epoch

    };

    double[][] exp = new double[][] {  // These ms counts all presume PST
        d(1276362000000L ),
        d(1276362000000L ),
        d(1276362000000L ),
        d(1276405200000L ),
        d(1276362000123L ),
        d(1276405200123L ),
        d(1276326000000L ),
        d(1395709848000L ),
        d(1395709848000L ),
        d(1395666648123L ),
        d(1395666648123L ),
        d(1395666648000L ),
        d(1395709848000L ),
        d(1395709848000L ),
        d(1393985448000L ),
        d(259639848000L  ),
        d(-55892952000L  ),
    };

    StringBuilder sb1 = new StringBuilder();
    for( String ds : data ) sb1.append(ds).append("\n");
    Key k1 = ParserTest.makeByteVec(sb1.toString());
    Key r1 = Key.make("r1");
    Frame dataFrame = ParseDataset.parse(r1, k1);

    for (int i=0; i < exp.length; i++ )  // Adjust exp[][] to local time
      for (int j=0; j < 1; j++)
        exp[i][j] += pst.getOffset((long)exp[i][j]) - localTZ.getOffset((long)exp[i][j]);
    //File items will be converted to ms for local timezone
    ParserTest.testParsed(dataFrame, exp, exp.length);
  }

  @Test public void testDayParseNoTime1() {
    // Just yyyy-mm-dd, no time
    String data = "Date\n"+
      "2014-1-23\n"+
      "2014-1-24\n"+
      "2014-1-23\n"+
      "2014-1-24\n";
    Key k1 = ParserTest.makeByteVec(data);
    Key r1 = Key.make("r1");
    Frame fr = ParseDataset.parse(r1, k1);
    Assert.assertTrue(fr.vec(0).get_type_str().equals("Time"));
    long[] exp = new long[] {  // Date
      1390464000000L,
      1390550400000L,
      1390464000000L,
      1390550400000L,
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
    Key k1 = ParserTest.makeByteVec(data);
    Key r1 = Key.make("r1");
    Frame fr = ParseDataset.parse(r1, k1);
    Assert.assertTrue(fr.vec(0).get_type_str().equals("Time"));
    long[] exp = new long[] {  // Date
            1390464000000L,
            1390550400000L,
            1390464000000L,
            1390550400000L,
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
    Key k1 = ParserTest.makeByteVec(data);
    Key r1 = Key.make("r1");
    Frame fr = ParseDataset.parse(r1, k1);
    Assert.assertTrue(fr.vec(0).get_type_str().equals("Time"));
    long[] exp = new long[] {  // Date
      1388563200000L,
      1391241600000L,
      1393660800000L,
      1396335600000L,
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
    Key k1 = ParserTest.makeByteVec(data);
    Key r1 = Key.make("r1");
    Frame fr = ParseDataset.parse(r1, k1);
    Assert.assertTrue(fr.vec(0).get_type_str().equals("Time"));
    long[] exp = new long[] {  // Date
      1388563200000L, 1391241600000L, 1393660800000L, 1396335600000L, // jan, feb, mar, apr 2014
      1398927600000L, 1401606000000L, 1404198000000L, 1406876400000L, // may, jun, jul, aug 2014
      1409554800000L, 1412146800000L, 1414825200000L, 1417420800000L, // sep, oct, nov, dec 2014
      1451635200000L, 1488355200000L, 1527836400000L, 1567321200000L, 1606809600000L, // jan 2016, mar 2017, jun 2018, sep 2019, dec 2020
      1388563200000L, 1391241600000L, 1393660800000L, 1396335600000L, // jan, feb, mar, apr 2014
      1398927600000L, 1401606000000L, 1404198000000L, 1406876400000L, // may, jun, jul, aug 2014
      1409554800000L, 1412146800000L, 1414825200000L, 1417420800000L, // sep, oct, nov, dec 2014
      1451635200000L, 1488355200000L, 1527836400000L, 1567321200000L, 1606809600000L, // jan 2016, mar 2017, jun 2018, sep 2019, dec 2020
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
    Key k1 = ParserTest.makeByteVec(data);
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
}
