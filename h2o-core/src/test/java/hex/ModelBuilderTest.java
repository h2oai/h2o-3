package hex;

import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.parser.BufferedString;

import static org.junit.Assert.assertEquals;

public class ModelBuilderTest extends TestUtil {

  @BeforeClass()
  public static void setup() { stall_till_cloudsize(1); }

  @Test
  @SuppressWarnings("unchecked")
  public void bulkBuildModels() throws Exception {
    Job j = new Job(null, null, "BulkBuilding");
    Key key1 = Key.make(j._key + "-dummny-1");
    Key key2 = Key.make(j._key + "-dummny-2");
    try {
      j.start(new BulkRunner(j), 10).get();
      assertEquals("Computed Dummy 1", DKV.getGet(key1).toString());
      assertEquals("Computed Dummy 2", DKV.getGet(key2).toString());
    } finally {
      DKV.remove(key1);
      DKV.remove(key2);
    }
  }

  public static class BulkRunner extends H2O.H2OCountedCompleter<BulkRunner> {
    private Job _j;
    private BulkRunner(Job j) { _j = j; }
    @Override
    public void compute2() {
      ModelBuilder<?, ?, ?>[] builders = {
              new DummyModelBuilder(new DummyModelParameters("Dummy 1", Key.make(_j._key + "-dummny-1"))),
              new DummyModelBuilder(new DummyModelParameters("Dummy 2", Key.make(_j._key + "-dummny-2")))
      };
      ModelBuilder.bulkBuildModels("dummy-group", _j, builders, 1 /*sequential*/, 1 /*increment by 1*/);
      // check that progress is as expected
      assertEquals(0.2, _j.progress(), 0.001);
      tryComplete();
    }
  }

  public static class DummyModelOutput extends Model.Output {}
  public static class DummyModelParameters extends Model.Parameters {
    private String _msg;
    private Key _trgt;
    public DummyModelParameters(String msg, Key trgt) { _msg = msg; _trgt = trgt; }
    @Override public String fullName() { return "dummy"; }
    @Override public String algoName() { return "dummy"; }
    @Override public String javaName() { return "dummy"; }
    @Override public long progressUnits() { return 1; }
  }
  public static class DummyModel extends Model<DummyModel, DummyModelParameters, DummyModelOutput> {
    public DummyModel(Key<DummyModel> selfKey, DummyModelParameters parms, DummyModelOutput output) {
      super(selfKey, parms, output);
    }
    @Override
    public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
      return null;
    }
    @Override
    protected double[] score0(double[] data, double[] preds) { return preds; }
  }
  public static class DummyModelBuilder extends ModelBuilder<DummyModel, DummyModelParameters, DummyModelOutput> {
    public DummyModelBuilder(DummyModelParameters parms) {
      super(parms);
    }

    @Override
    protected Driver trainModelImpl() {
      return new Driver() {
        @Override
        public void computeImpl() {
          DKV.put(_parms._trgt, new BufferedString("Computed " + _parms._msg));
        }
      };
    }

    @Override
    public ModelCategory[] can_build() {
      return new ModelCategory[0];
    }

    @Override
    public boolean isSupervised() {
      return false;
    }
  }

}