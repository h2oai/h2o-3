package hex;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;
import water.util.Log;

public class InteractionTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  Frame makeFrame(long rows) {
    CreateFrame cf = new CreateFrame();
    cf.rows = rows;
    cf.cols = 10;
    cf.categorical_fraction = 0.7;
    cf.integer_fraction = 0.1;
    cf.missing_fraction = 0.1;
    cf.binary_fraction = 0.1;
    cf.factors = 5;
    cf.response_factors = 2;
    cf.positive_response = false;
    cf.has_response = false;
    cf.seed = 1234;
    return cf.execImpl().get();
  }

  @Test public void basicTest() {
    Frame frame = makeFrame(100);
    Log.info(frame.toString());

    Interaction in = new Interaction();
    in._source_frame = frame._key;
    in._factor_columns = new String[]{"C5","C6","C4","C3"};
    in._max_factors = 20;
    in._min_occurrence = 1;
    in._pairwise = false;
    Frame frame2 = in.execImpl(null).get();
    Log.info(frame2.toString());

    frame.delete();
    frame2.delete();
  }
  @Test public void basicTestPairWise() {
    Frame frame = makeFrame(100);
    Log.info(frame.toString());

    Interaction in = new Interaction();
    in._source_frame = frame._key;
    in._factor_columns = new String[]{"C3","C8","C9"};
    in._max_factors = 10;
    in._min_occurrence = 1;
    in._pairwise = true;
    Frame frame2 = in.execImpl(null).get();
    Log.info(frame2.toString());

    frame.delete();
    frame2.delete();
  }
  @Test public void basicTestMinOccurrence() {
    Frame frame = makeFrame(300);
    Log.info(frame.toString());

    Interaction in = new Interaction();
    in._source_frame = frame._key;
    in._factor_columns = new String[]{"C8","C6","C4"};
    in._max_factors = 106;
    in._min_occurrence = 4;
    in._pairwise = true;
    Frame frame2 = in.execImpl(null).get();
    Log.info(frame2.toString());

    frame.delete();
    frame2.delete();
  }
  @Test public void basicTest3() {
    Frame frame = makeFrame(10000);
    Log.info(frame.toString());

    Interaction in = new Interaction();
    in._source_frame = frame._key;
    in._factor_columns = new String[]{"C4","C6","C8"};
    in._max_factors = 20;
    in._min_occurrence = 2;
    in._pairwise = false;
    Frame frame2 = in.execImpl(null).get();
    Log.info(frame2.toString());

    frame.delete();
    frame2.delete();
  }
}
