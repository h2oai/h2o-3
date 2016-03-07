package hex;

import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;

public class DataInfoTest extends TestUtil {

  @BeforeClass static public void setup() {  stall_till_cloudsize(1); }


  @Test public void testAirlines() {
    Frame fr = parse_test_file(Key.make("a.hex"), "smalldata/airlines/allyears2k_headers.zip");
    try {
      DataInfo dinfo = new DataInfo(fr.clone(), null, 1, true,  DataInfo.TransformType.STANDARDIZE, DataInfo.TransformType.NONE, true, false ,false, false, false, false, DataInfo.InteractionPair.generatePairwiseInteractions(8, 16, 2));
      dinfo.dropInteractions();
      dinfo.remove();
    } finally {
      fr.delete();
    }
  }

}
