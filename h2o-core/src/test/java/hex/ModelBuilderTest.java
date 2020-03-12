package hex;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import water.*;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.parser.BufferedString;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.*;


public class ModelBuilderTest extends TestUtil {

  @Rule
  public transient TemporaryFolder tmp = new TemporaryFolder();

  @BeforeClass()
  public static void setup() { stall_till_cloudsize(1); }

  @Test
  public void testRebalancePubDev5400() {
    try {
      Scope.enter();
      // create a frame where only the last chunk has data and the rest is empty
      final int nChunks = H2O.NUMCPUS;
      final int nRows = nChunks * 1000;
      double[] colA = new double[nRows];
      String[] resp = new String[nRows];
      for (int i = 0; i < colA.length; i++) {
        colA[i] = i % 7;
        resp[i] = i % 3 == 0 ? "A" : "B";
      }
      long[] layout = new long[nChunks];
      layout[nChunks - 1] = colA.length;
      final Frame train = Scope.track(new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "Response")
              .withVecTypes(Vec.T_NUM, Vec.T_CAT)
              .withDataForCol(0, colA)
              .withDataForCol(1, resp)
              .withChunkLayout(layout)
              .build());
      assertEquals(nChunks, train.anyVec().nChunks());
      assertEquals(colA.length, train.numRows());

      DummyModelParameters parms = new DummyModelParameters("Rebalance Test", Key.make( "rebalance-test"));
      parms._train = train._key;
      ModelBuilder<?, ?, ?> mb = new DummyModelBuilder(parms);

      // the frame looks ideal (it has as many chunks as desired)
      assertEquals(nChunks, mb.desiredChunks(train, true));

      // expensive init - should include rebalance
      mb.init(true);

      // check that dataset was rebalanced
      long[] espc = mb.train().anyVec().espc();
      assertEquals(nChunks + 1, espc.length);
      assertEquals(nRows, espc[nChunks]);
      for (int i = 0; i < espc.length; i++)
        assertEquals(i * 1000, espc[i]);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testRebalanceMulti() {
    org.junit.Assume.assumeTrue(H2O.getCloudSize() > 1);
    try {
      Scope.enter();
      double[] colA = new double[1000000];
      String[] resp = new String[colA.length];
      for (int i = 0; i < colA.length; i++) {
        colA[i] = i % 7;
        resp[i] = i % 3 == 0 ? "A" : "B";
      }
      final Frame train = Scope.track(new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "Response")
              .withVecTypes(Vec.T_NUM, Vec.T_CAT)
              .withDataForCol(0, colA)
              .withDataForCol(1, resp)
              .withChunkLayout(colA.length) // single chunk
              .build());
      assertEquals(1, train.anyVec().nChunks());

      DummyModelParameters parms = new DummyModelParameters("Rebalance Test", Key.make( "rebalance-test"));
      parms._train = train._key;
      ModelBuilder<?, ?, ?> mb = new DummyModelBuilder(parms) {
        @Override
        protected String getSysProperty(String name, String def) {
          if (name.equals("rebalance.ratio.multi"))
            return "0.5";
          if (name.equals("rebalance.enableMulti"))
            return "true";
          if (name.startsWith(H2O.OptArgs.SYSTEM_PROP_PREFIX + "rebalance"))
            throw new IllegalStateException("Unexpected property: " + name);
          return super.getSysProperty(name, def);
        }
      };

      // the rebalance logic should spread the Frame across the whole cluster (>> single node CPUs)
      final int desiredChunks = mb.desiredChunks(train, false);
      assertTrue(desiredChunks > 4 * H2O.NUMCPUS);

      // expensive init - should include rebalance
      mb.init(true);

      // check that dataset was rebalanced
      final int rebalancedChunks = mb.train().anyVec().nonEmptyChunks();
      assertEquals(desiredChunks, rebalancedChunks);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testMakeUnknownModel() {
    try {
      ModelBuilder.make("invalid", null, null);
      fail();
    } catch (IllegalStateException e) {
      // core doesn't have any algos but depending on the order of tests' execution DummyModelBuilder might already be registered
      assertTrue( e.getMessage().startsWith("Algorithm 'invalid' is not registered. Available algos: [")); 
    }
  }
  
  @Test
  public void testMakeByModelParameters() {
    DummyModelParameters params = new DummyModelParameters();

    ModelBuilder modelBuilder = ModelBuilder.make(params);

    assertNotNull(modelBuilder._job); 
    assertNotNull(modelBuilder._result); 
    assertEquals(params, params); 
    assertNotEquals(modelBuilder._parms, params); 
  }

  @Test
  public void testScoreReorderedDomain() {
    Frame train = null, test = null, scored = null;
    Model model = null;
    try {
      train = new TestFrameBuilder()
              .withVecTypes(Vec.T_NUM, Vec.T_CAT)
              .withDataForCol(0, ar(0, 1))
              .withDataForCol(1, ar("A", "B"))
              .build();
      test = new TestFrameBuilder()
              .withVecTypes(Vec.T_NUM, Vec.T_CAT)
              .withDataForCol(0, ar(0, 1))
              .withDataForCol(1, ar("B", "A"))
              .build();

      assertFalse(Arrays.equals(train.vec(1).domain(), test.vec(1).domain()));

      DummyModelParameters p = new DummyModelParameters("Dummy 1", Key.make("dummny-1"));
      p._makeModel = true;
      p._train = train._key;
      p._response_column = "col_1";
      DummyModelBuilder bldr = new DummyModelBuilder(p);

      model = bldr.trainModel().get();
      scored = model.score(test);

      assertArrayEquals(new String[]{"B", "A"}, scored.vec(0).domain());
    } finally {
      if (model != null)
        model.remove();
      if (train != null)
        train.remove();
      if (test != null)
      test.remove();
      if (scored != null)
        scored.remove();
    }
  }
  
  
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

  @Test
  public void testExportCheckpointsWriteCheck() throws IOException {
    try {
      Scope.enter();
      File dummyFile = tmp.newFile("dummy");

      Frame train = TestFrameCatalog.oneChunkFewRows();
      
      // 1. Validation is only down
      DummyModelParameters failingParms = new DummyModelParameters("Failing Dummy", Key.make("dummny-failing"));
      failingParms._export_checkpoints_dir = dummyFile.getAbsolutePath();
      failingParms._train = train._key;
      failingParms._response_column = train.name(0); 

      DummyModelBuilder failingBuilder = new DummyModelBuilder(failingParms);
      assertEquals(
              "ERRR on field: _export_checkpoints_dir: Checkpoints directory path must point to a writable path.\n",
              failingBuilder.validationErrors()
      );

      // 2. Now test that CV model will do no validation 
      DummyModelParameters cvParams = new DummyModelParameters("Failing Dummy", Key.make("dummny-failing"));
      cvParams._export_checkpoints_dir = dummyFile.getAbsolutePath();
      cvParams._train = train._key;
      cvParams._response_column = train.name(0);
      cvParams._is_cv_model = true; // Emulate CV

      DummyModelBuilder cvBuilder = new DummyModelBuilder(cvParams);
      assertEquals("", cvBuilder.validationErrors()); // shouldn't fail
    } finally {
      Scope.exit();
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

  public static class DummyModelOutput extends Model.Output {
    public final String _msg;
    public DummyModelOutput(ModelBuilder b, Frame train, String msg) {
      super(b, train);
      _msg = msg;
    }
    @Override
    public ModelCategory getModelCategory() {
      return ModelCategory.Binomial;
    }
    @Override
    public boolean isSupervised() {
      return true;
    }
  }
  public static class DummyExtension extends AbstractH2OExtension {
    @Override
    public String getExtensionName() {
      return "dummy";
    }
    @Override
    public void init() {
      DummyModelParameters params = new DummyModelParameters();
      new DummyModelBuilder(params, true); // as a side effect DummyModelBuilder will be registered in a static field ModelBuilder.ALGOBASES
    }
  }
  public static class DummyModelParameters extends Model.Parameters {
    public DummyAction _action;
    public boolean _makeModel;
    public boolean _cancel_job;
    public DummyModelParameters() {}
    public DummyModelParameters(String msg, Key trgt) {
      _action = new MessageInstallAction(trgt, msg);
    }
    @Override public String fullName() { return algoName(); }
    @Override public String algoName() { return "dummymodelbuilder"; }
    @Override public String javaName() { return DummyModelBuilder.class.getName(); }
    @Override public long progressUnits() { return 1; }
  }
  public static class DummyModel extends Model<DummyModel, DummyModelParameters, DummyModelOutput> {
    public DummyModel(Key<DummyModel> selfKey, DummyModelParameters parms, DummyModelOutput output) {
      super(selfKey, parms, output);
    }
    @Override
    public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
      return new ModelMetricsBinomial.MetricBuilderBinomial(domain);
    }
    @Override
    protected double[] score0(double[] data, double[] preds) { return preds; }
    @Override
    protected Futures remove_impl(Futures fs, boolean cascade) {
      super.remove_impl(fs, cascade);
      if (_parms._action != null) {
        _parms._action.cleanUp();
      }
      return fs;
    }
  }
  public static class DummyModelBuilder extends ModelBuilder<DummyModel, DummyModelParameters, DummyModelOutput> {
    public DummyModelBuilder(DummyModelParameters parms) {
      super(parms);
      init(false);
    }

    public DummyModelBuilder(DummyModelParameters parms, boolean startup_once ) { super(parms,startup_once); }

    @Override
    protected Driver trainModelImpl() {
      return new Driver() {
        @Override
        public void computeImpl() {
          if (_parms._cancel_job)
            throw new Job.JobCancelledException();
          String msg = null;
          if (_parms._action != null) 
            msg = _parms._action.run(_parms);
          if (! _parms._makeModel)
            return;
          init(true);
          Model model = null;
          try {
            model = new DummyModel(dest(), _parms, new DummyModelOutput(DummyModelBuilder.this, train(), msg));
            model.delete_and_lock(_job);
            model.update(_job);
          } finally {
            if (model != null)
              model.unlock(_job);
          }
        }
      };
    }

    @Override
    public ModelCategory[] can_build() {
      return new ModelCategory[0];
    }

    @Override
    public boolean isSupervised() {
      return true;
    }
  }
  public static abstract class DummyAction<T> extends Iced<DummyAction<T>> {
    protected abstract String run(DummyModelParameters parms);
    protected void cleanUp() {};
  }
  private static class MessageInstallAction extends DummyAction<MessageInstallAction> {
    private final Key _trgt;
    private final String _msg;
    
    public MessageInstallAction(Key trgt, String msg) {
      _trgt = trgt;
      _msg = msg;
    }

    @Override
    protected String run(DummyModelParameters parms) {
      DKV.put(_trgt, new BufferedString("Computed " + _msg));
      return _msg;
    }

    @Override
    protected void cleanUp() {
      DKV.remove(_trgt);
    }
  } 
}
