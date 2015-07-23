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
}
