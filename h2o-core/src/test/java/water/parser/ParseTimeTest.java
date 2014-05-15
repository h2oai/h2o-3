package water.parser;

import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;

public class ParseTimeTest extends TestUtil {

  // Parse click & query times from a subset of kaggle bestbuy data
  @Test public void testTimeParse1() {
    Frame fr = parse_test_file("smalldata/junit/test_time.csv");
    Frame fr2 = fr.subframe(new String[]{"click_time","query_time"});
    double[][] exp = new double[][] {
      ard(1314945892533L, 1314945839752L ),
      ard(1315250737042L, 1315250701187L ),
      ard(1314215818091L, 1314215713012L ),
      ard(1319552294722L, 1319552211759L ),
      ard(1319552391697L, 1319552211759L ),
      ard(1315436087956L, 1315436004353L ),
      ard(1316974022603L, 1316973926996L ),
      ard(1316806820871L, 1316806814845L ),
      ard(1314650252903L, 1314650003249L ),
      ard(1319608558683L, 1319608485926L ),
      ard(1315770524139L, 1315770378466L ),
      ard(1318983693919L, 1318983686057L ),
      ard(1315158920427L, 1315158910874L ),
      ard(1319844389203L, 1319844380358L ),
      ard(1318232126858L, 1318232070708L ),
      ard(1316841248965L, 1316841217043L ),
      ard(1315681493645L, 1315681470805L ),
      ard(1319395475074L, 1319395407011L ),
      ard(1319395524416L, 1319395407011L ),
    };

    ParserTest.testParsed(fr2,exp,exp.length);
    fr.delete();
  }

  @Test public void testTimeParse2() {
    double[][] exp = new double[][] {
      ard(1     ,     115200000L, 1136275200000L, 1136275200000L, 1 ),
      ard(1500  ,  129625200000L, 1247641200000L, 1247641200000L, 0 ),
      ard(15000 , 1296028800000L, 1254294000000L, 1254294000000L, 2 ),
    };
    ParserTest.testParsed(parse_test_file("smalldata/junit/ven-11.csv"),exp,exp.length);
  }
}
