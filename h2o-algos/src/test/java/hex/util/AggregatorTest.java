package hex.util;

import hex.CreateFrame;
import hex.aggregator.Aggregator;
import hex.aggregator.AggregatorModel;
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

    AggregatorModel.AggregatorParameters parms = new AggregatorModel.AggregatorParameters();
    parms._train = frame._key;
    parms._keep_member_indices = false;
    parms._radius_scale = 1.0;
    AggregatorModel agg = new Aggregator(parms).trainModel().get();
    Frame output = agg._output._output_frame.get();
    frame.delete();
    Log.info("Number of exemplars: " + agg._exemplars.length);
    output.remove();
    agg.remove();
  }

  @Test public void testCovtype() {
    Frame frame = parse_test_file("smalldata/covtype/covtype.20k.data");

    AggregatorModel.AggregatorParameters parms = new AggregatorModel.AggregatorParameters();
    parms._train = frame._key;
    parms._keep_member_indices = false;
    parms._radius_scale = 5.0;
    AggregatorModel agg = new Aggregator(parms).trainModel().get();
    frame.delete();
    Frame output = agg._output._output_frame.get();
    Log.info("Exemplars: " + output.toString());
    output.remove();
    Log.info("Number of exemplars: " + agg._exemplars.length);
    agg.remove();
  }

  @Test public void testChunks() {
    Frame frame = parse_test_file("smalldata/covtype/covtype.20k.data");

    AggregatorModel.AggregatorParameters parms = new AggregatorModel.AggregatorParameters();
    parms._train = frame._key;
    parms._keep_member_indices = false;
    parms._radius_scale = 3.0;
    AggregatorModel agg = new Aggregator(parms).trainModel().get();
    Frame output = agg._output._output_frame.get();
    Log.info("Number of exemplars: " + agg._exemplars.length);
    output.remove();
    agg.remove();

    for (int i : new int[]{1,2,5,10,50,100}) {
      Key key = Key.make();
      RebalanceDataSet rb = new RebalanceDataSet(frame, key, i);
      H2O.submitTask(rb);
      rb.join();
      Frame rebalanced = DKV.get(key).get();

      parms = new AggregatorModel.AggregatorParameters();
      parms._train = frame._key;
      parms._keep_member_indices = false;
      parms._radius_scale = 3.0;
      AggregatorModel agg2 = new Aggregator(parms).trainModel().get();
      Log.info("Number of exemplars for " + i + " chunks: " + agg2._exemplars.length);
      rebalanced.delete();
      Assert.assertTrue(Math.abs(agg._exemplars.length - agg2._exemplars.length) < agg._exemplars.length*0.2);
      output = agg2._output._output_frame.get();
      output.remove();
      agg2.remove();
    }
    frame.delete();
  }

  @Test public void testCovtypeMemberIndices() {
    Frame frame = parse_test_file("smalldata/covtype/covtype.20k.data");

    AggregatorModel.AggregatorParameters parms = new AggregatorModel.AggregatorParameters();
    parms._train = frame._key;
    parms._keep_member_indices = true;
    parms._radius_scale = 5.0;
    AggregatorModel agg = new Aggregator(parms).trainModel().get();

    //Log.info("Exemplars: " + output.get().toString());
    Key<Frame> memberKey = Key.make();
    Frame members = agg.scoreExemplarMembers(memberKey, 8);
    assert(members.numRows() == agg._counts[8]);
    Log.info(members);
    Frame output = agg._output._output_frame.get();
    output.remove();
    Log.info("Number of exemplars: " + agg._exemplars.length);
    frame.delete();
    agg.remove();
    members.delete();
  }

  @Test public void testMNIST() {
    Frame frame = parse_test_file("bigdata/laptop/mnist/train.csv.gz");

    AggregatorModel.AggregatorParameters parms = new AggregatorModel.AggregatorParameters();
    parms._train = frame._key;
    parms._keep_member_indices = false;
    parms._radius_scale = 100.0;
    AggregatorModel agg = new Aggregator(parms).trainModel().get();
    frame.delete();
    Frame output = agg._output._output_frame.get();
    Log.info("Exemplars: " + output);
    output.remove();
    Log.info("Number of exemplars: " + agg._exemplars.length);
    agg.remove();
  }
}
