package hex.util;

import hex.CreateFrame;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.H2O;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.RebalanceDataSet;
import water.util.Log;

public class AggregatorTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test public void testAggregator() {
    CreateFrame cf = new CreateFrame();
    cf.rows = 100000;
    cf.cols = 2;
    cf.categorical_fraction = 0.1;
    cf.integer_fraction = 0.3;
    cf.real_range = 100;
    cf.integer_range = 100;
    cf.seed = 1234;
    Frame frame = cf.execImpl().get();

    Aggregator agg = new Aggregator(frame, null, 1.0, false).execImpl();
    frame.delete();
    Log.info("Number of exemplars: " + agg._exemplars.length);
    agg.remove();
  }

  @Test public void testCovtype() {
    Frame frame = parse_test_file("smalldata/covtype/covtype.20k.data");

    Key<Frame> output = Key.make();
    Aggregator agg = new Aggregator(frame, output, 5.0, false).execImpl();
    frame.delete();
    Log.info("Exemplars: " + output.get().toString());
    output.remove();
    Log.info("Number of exemplars: " + agg._exemplars.length);
    agg.remove();
  }

  @Test public void testChunks() {
    Frame frame = parse_test_file("smalldata/covtype/covtype.20k.data");

    double radiusScale = 3;
    Key<Frame> output = Key.make();
    Aggregator agg = new Aggregator(frame, output, radiusScale, false).execImpl();
    Log.info("Number of exemplars: " + agg._exemplars.length);
    output.remove();
    agg.remove();

    for (int i : new int[]{1,2,5,10,50,100}) {
      Key key = Key.make();
      RebalanceDataSet rb = new RebalanceDataSet(frame, key, i);
      H2O.submitTask(rb);
      rb.join();
      Frame rebalanced = DKV.get(key).get();

      Aggregator agg2 = new Aggregator(rebalanced, output, radiusScale, false).execImpl();
      Log.info("Number of exemplars for " + i + " chunks: " + agg2._exemplars.length);
      rebalanced.delete();
      Assert.assertTrue(Math.abs(agg._exemplars.length - agg2._exemplars.length) < agg._exemplars.length*0.2);
      output.remove();
      agg2.remove();
    }
    frame.delete();
  }

  @Test public void testCovtypeMemberIndices() {
    Frame frame = parse_test_file("smalldata/covtype/covtype.20k.data");

    Key<Frame> output = Key.make();
    Aggregator agg = new Aggregator(frame, output, 5.0, true).execImpl();
    //Log.info("Exemplars: " + output.get().toString());
    Key<Frame> memberKey = Key.make();
    Frame members = agg.getMembersForExemplar(memberKey, 8);
    assert(members.numRows() == agg._counts[8]);
    Log.info(members);
    output.remove();
    Log.info("Number of exemplars: " + agg._exemplars.length);
    frame.delete();
    agg.remove();
    members.delete();
  }

  @Test public void testMNIST() {
    Frame frame = parse_test_file("bigdata/laptop/mnist/train.csv");

    Key<Frame> output = Key.make();
    Aggregator agg = new Aggregator(frame, output, 100, false).execImpl();
    frame.delete();
    Log.info("Exemplars: " + output.get().toString());
    output.remove();
    Log.info("Number of exemplars: " + agg._exemplars.length);
    agg.remove();
  }
}
