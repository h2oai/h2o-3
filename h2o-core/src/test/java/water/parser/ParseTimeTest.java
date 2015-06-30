package water.parser;

import org.junit.*;

import org.joda.time.DateTimeZone;
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
}
