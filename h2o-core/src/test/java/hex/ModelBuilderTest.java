package hex;

import hex.genmodel.utils.DistributionFamily;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import water.*;
import water.fvec.*;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.test.dummy.DummyModel;
import water.test.dummy.DummyModelBuilder;
import water.test.dummy.DummyModelParameters;
import water.test.dummy.MessageInstallAction;
import water.util.ArrayUtils;
import water.util.ReflectionUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;

import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static org.junit.Assert.*;
import static water.TestUtil.*;
import static water.util.RandomUtils.getRNG;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class ModelBuilderTest {

  @Rule
  public transient TemporaryFolder tmp = new TemporaryFolder();

  @Rule
  public transient ExpectedException ee = ExpectedException.none();
  
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

      try (Scope.Safe s = Scope.safe()) { // wrap init in a fresh scope as training frame is protected that scope.
        // expensive init - should include rebalance
        mb.init(true);
        // check that dataset was rebalanced
        long[] espc = mb.train().anyVec().espc();
        assertEquals(nChunks + 1, espc.length);
        assertEquals(nRows, espc[nChunks]);
        for (int i = 0; i < espc.length; i++)
          assertEquals(i * 1000, espc[i]);
      }
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

      try (Scope.Safe s = Scope.safe()) { // wrap init in a fresh scope as training frame is protected that scope.
        // expensive init - should include rebalance
        mb.init(true);
        // check that dataset was rebalanced
        final int rebalancedChunks = mb.train().anyVec().nonEmptyChunks();
        assertEquals(desiredChunks, rebalancedChunks);
      }

    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testWorkSpaceInit() {
    DummyModelBuilder builder = new DummyModelBuilder(new DummyModelParameters());
    // workspace is initialized upon builder instantiation
    ModelBuilder.Workspace workspace = ReflectionUtils.getFieldValue(builder, "_workspace");
    assertNotNull(workspace);
    assertNull(workspace.getToDelete(false));
    // cheap init keeps workspace unchanged
    builder.init(false);
    workspace = ReflectionUtils.getFieldValue(builder, "_workspace");
    assertNotNull(workspace);
    assertNull(workspace.getToDelete(false));
    // it is not possible to get "to delete" structure in for expensive operations
    try {
      workspace.getToDelete(true);
      fail("Exception expected");
    } catch (IllegalStateException e) {
      assertEquals(
              "ModelBuilder was not correctly initialized. Expensive phase requires field `_toDelete` to be non-null. " +
                      "Does your implementation of init method call super.init(true) or alternatively initWorkspace(true)?", 
              e.getMessage()
      );
    }
    // expensive init will switch the workspace and let us get "to delete" for expensive ops
    builder.init(true);
    workspace = ReflectionUtils.getFieldValue(builder, "_workspace");
    assertNotNull(workspace.getToDelete(true));
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
  public void testMakeFromModelParams() {
    DummyModelParameters params = new DummyModelParameters();

    ModelBuilder modelBuilder = ModelBuilder.make(params);

    assertNotNull(modelBuilder._job); 
    assertNotNull(modelBuilder._result); 
    assertNotSame(modelBuilder._parms, params); 
  }

  @Test
  public void testMakeFromParamsAndKey() {
    DummyModelParameters params = new DummyModelParameters();
    Key<Model> mKey = Key.make();

    ModelBuilder modelBuilder = ModelBuilder.make(params, mKey);

    assertNotNull(modelBuilder._job);
    assertEquals(modelBuilder._job._result, mKey);
    assertEquals(mKey, modelBuilder._result);
    assertNotSame(modelBuilder._parms, params);
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
              .withDataForCol(1, ar("A", "B"))
              .withDomain(1, ar("B", "A"))
              .build();

      assertFalse(Arrays.equals(train.vec(1).domain(), test.vec(1).domain()));

      DummyModelParameters p = new DummyModelParameters("Dummy 1", Key.make("dummny-1"));
      p._makeModel = true;
      p._train = train._key;
      p._response_column = "col_1";
      DummyModelBuilder bldr = new DummyModelBuilder(p);

      model = bldr.trainModel().get();
      scored = model.score(test);

      //predict column should have the same domain as the target column of the scored frame
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

  @Test
  public void testValidationOfClassificationStoppingMetrics() {
    try {
      Scope.enter();
      Frame train = TestFrameCatalog.oneChunkFewRows();

      ScoreKeeper.StoppingMetric[] classificationOnly = new ScoreKeeper.StoppingMetric[] {
              ScoreKeeper.StoppingMetric.logloss,
              ScoreKeeper.StoppingMetric.AUC,
              ScoreKeeper.StoppingMetric.AUCPR,
              ScoreKeeper.StoppingMetric.lift_top_group,
              ScoreKeeper.StoppingMetric.misclassification,
              ScoreKeeper.StoppingMetric.mean_per_class_error
      };

      for (ScoreKeeper.StoppingMetric sm : ScoreKeeper.StoppingMetric.values()) {
        assertEquals(sm + "is probably not know to this test", 
                ArrayUtils.contains(classificationOnly, sm), sm.isClassificationOnly());

        DummyModelParameters failingParms = new DummyModelParameters("Failing Dummy " + sm, Key.make("dummny-failing-" + sm));
        failingParms._train = train._key;
        failingParms._stopping_metric = sm;
        failingParms._stopping_rounds = 2;
        failingParms._response_column = train.name(0);

        DummyModelBuilder failingBuilder = new DummyModelBuilder(failingParms);
        if (sm.isClassificationOnly()) {
          assertEquals(
                  "ERRR on field: _stopping_metric: Stopping metric cannot be " + sm + " for regression.\n",
                  failingBuilder.validationErrors()
          );
        } else if ((sm != ScoreKeeper.StoppingMetric.custom) && (sm != ScoreKeeper.StoppingMetric.custom_increasing)) {
          assertEquals("", failingBuilder.validationErrors());
        }
      }

    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testUsedColumns() {
    String[] trainNames = new String[] {
        "c1", "c2", "c3", "c4", "c5", "response"
    };
    DummyModelParameters params = new DummyModelParameters();
    params._dummy_string_array_param = new String[] { "c1" };
    params._dummy_string_param = "c2";
    assertEquals(
        "no columns used", emptySet(), params.getUsedColumns(trainNames));
    params._column_param = "invalid";
    assertEquals(
        "invalid column name not used", emptySet(), params.getUsedColumns(trainNames)
    );
    params._column_param = "response";
    assertEquals(
        "columns from simple param", 
        new HashSet<>(singletonList("response")), params.getUsedColumns(trainNames)
    );
    params._column_param = null;
    params._column_list_param = new String[] { "invalid", "c4", "c5" };
    assertEquals(
        "columns from array param", 
        new HashSet<>(asList("c4", "c5")), params.getUsedColumns(trainNames)
    );
    params._column_param = "response";
    assertEquals(
        "columns from multiple params combined", 
        new HashSet<>(asList("c4", "c5", "response")), params.getUsedColumns(trainNames)
    );
  }

  @Test
  public void testTrainModelNestedExecutesOnExceptionalCompletionSynchronously() {
    Key proofKey = Key.make();
    try {
      Scope.enter();
      Frame trainingFrame = TestFrameCatalog.oneChunkFewRows();

      DummyModelParameters params = new DummyModelParameters();
      params._response_column = trainingFrame.name(0);
      params._train = trainingFrame._key;
      params._cancel_job = true;
      params._on_exception_action = new DelayedMessageInstallAction(proofKey, "onExceptionalCompletion", 1000);

      ee.expect(Job.JobCancelledException.class);
      try {
        new DummyModelBuilder(params).trainModelNested(trainingFrame);
      } finally {
        assertEquals("Computed onExceptionalCompletion", DKV.getGet(proofKey).toString());
      }
    } finally {
      Scope.exit();
      DKV.remove(proofKey);
    }
  }

  private static class DelayedMessageInstallAction extends MessageInstallAction {
    private final int _delay_millis;

    DelayedMessageInstallAction(Key trgt, String msg, int delayMillis) {
      super(trgt, msg);
      _delay_millis = delayMillis;
    }

    @Override
    protected String run(DummyModelParameters parms) {
      try {
        Thread.sleep(_delay_millis);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return super.run(parms);
    }
  }

  @Test
  public void testFoldColumnHaveExtraLevels() {
    DummyModel model = null;
    try {
      Scope.enter();
      Frame trainingFrame = TestFrameCatalog.oneChunkFewRows();
      Vec fold = Scope.track(ivec(1, 3, 1));
      fold.setDomain(new String[]{"NoDataFold0", "fold1", "NoDataFold2", "fold3", "NoDataFold4"});
      DKV.put(fold);
      trainingFrame.add("Fold", fold);
      DKV.put(trainingFrame);

      DummyModelParameters params = new DummyModelParameters();
      params._response_column = "col_3";
      params._train = trainingFrame._key;
      params._fold_column = "Fold";
      params._makeModel = true;

      model = new DummyModelBuilder(params).trainModel().get();
      Scope.track_generic(model);
      assertEquals(2, model._output._cross_validation_models.length);
    } finally {
      if (model != null)
        model.deleteCrossValidationModels();
      Scope.exit();
    }
  }

  @Test
  public void testMakeHoldoutPredictionCombiner() {
    assertFalse(
            ModelBuilder.makeHoldoutPredictionCombiner(-1, -1, 0) instanceof ModelBuilder.ApproximatingHoldoutPredictionCombiner
    );
    assertTrue(
            ModelBuilder.makeHoldoutPredictionCombiner(-1, -1, 4) instanceof ModelBuilder.ApproximatingHoldoutPredictionCombiner
    );
    ee.expectMessage("Precision cannot be negative, got precision = -42");
    ModelBuilder.makeHoldoutPredictionCombiner(-1, -1, -42);
  }

  @Test
  public void testApproximatingHoldoutPredictionCombiner() {
    try {
      Scope.enter();
      Vec v = Vec.makeVec(ard(0, 1.0, 0.1, 0.99994, 0.99995, 1e-5, 0.123456789), Vec.newKey());
      Scope.track(v);
      Frame approx = new ModelBuilder.ApproximatingHoldoutPredictionCombiner(1, 1, 4)
              .doAll(Vec.T_NUM, v).outputFrame();
      Scope.track(approx);
      Vec expected = Vec.makeVec(ard(0, 1.0, 0.1, 0.9999, 1.0, 0, 0.1235), Vec.newKey());
      assertVecEquals(expected, approx.vec(0), 0);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testApproximatingHoldoutPredictionCombinerProducesCSChunks() {
    try {
      Scope.enter();
      Vec v = randomProbabilitiesVec(42, 100_000, 20);
      Scope.track(v);

      checkApproximatingHoldoutPredictionCombiner(v, 8, 2, 0.2);
      checkApproximatingHoldoutPredictionCombiner(v, 4, 4, 0.2);
      checkApproximatingHoldoutPredictionCombiner(v, 2, 8, 0.5);
    } finally {
      Scope.exit();
    }
  }

  private static void checkApproximatingHoldoutPredictionCombiner(Vec v, int precision, int expectedMemoryRatio, double delta) {
    Vec approx = new ModelBuilder.ApproximatingHoldoutPredictionCombiner(1, 1, precision)
            .doAll(Vec.T_NUM, v).outputFrame().vec(0);
    Scope.track(approx);
    assertEquals(expectedMemoryRatio, v.byteSize() / (double) approx.byteSize(), delta);
    for (int i = 0; i < approx.nChunks(); i++) {
      assertTrue(approx.chunkForChunkIdx(i) instanceof CSChunk);
    }
  }
  
  private static Vec randomProbabilitiesVec(final long seed, final long len, final int nChunks) {
    Vec v = Vec.makeConN(len, nChunks);
    new MRTask() {
      @Override public void map(Chunk c) {
        final long chunk_seed = seed + c.start();
        for (int i = 0; i < c._len; i++) {
          double rnd = getRNG(chunk_seed + i).nextDouble();
          c.set(i, rnd);
        }
      }
    }.doAll(v);
    return v;
  }

}
