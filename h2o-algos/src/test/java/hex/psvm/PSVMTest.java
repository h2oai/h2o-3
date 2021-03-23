package hex.psvm;

import hex.*;
import hex.genmodel.algos.psvm.ScorerFactory;
import hex.genmodel.algos.psvm.SupportVectorScorer;
import hex.splitframe.ShuffleSplitFrame;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.*;
import water.rapids.ast.prims.math.AstSgn;
import water.test.util.ConfusionMatrixUtils;
import water.util.FrameUtils;

import static org.junit.Assert.*;

public class PSVMTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void testSplice() {
    try {
      Scope.enter();
      Frame fr = parseTestFile("./smalldata/splice/splice.svm");
      Scope.track(fr);

      PSVMModel.PSVMParameters parms = new PSVMModel.PSVMParameters();
      parms._gamma = 0.01;
      parms._rank_ratio = 0.1;
      parms._train = fr._key;
      parms._response_column = "C1";

      PSVMModel model = new SVMTrainer(parms).train();
      assertNotNull(model);
      Scope.track_generic(model);
      assertEquals(2.38873807, model._output._rho, 1e-6);
      assertEquals(662, model._output._svs_count);
      assertEquals(612, model._output._bsv_count);
      assertNotNull(model._output._compressed_svs);
      assertNotEquals(0, model._output._compressed_svs.length);

      Frame expected = parseTestFile("./smalldata/splice/splice_icf100_preds.csv");
      Scope.track(expected);
      expected.replace(
              expected.find("predict"),
              Scope.track(Scope.track(new TransformWrappedVec(expected.vec("score"), new AstSgn()).toStringVec()).toCategoricalVec())
      );

      Frame predicted = model.score(fr);
      Scope.track(predicted);

      ModelMetricsSupervised mm = (ModelMetricsSupervised) ModelMetrics.getFromDKV(model, fr);
      assertNotNull(mm);
      Scope.track_generic(mm);
      
      System.out.println(predicted.toTwoDimTable().toString());

      assertVecEquals(expected.vec("predict"), predicted.vec("predict"), 0);

      checkCM(model, fr, fr.vec("C1"), predicted.vec(0));

      checkScorers(model, fr, expected.vec("score"));
    } finally {
      Scope.exit();
    }
  }

  private static class SVMTrainer extends H2O.RemoteRunnable<SVMTrainer> {
    private final PSVMModel.PSVMParameters _parms;
    private PSVMModel _model;
    private SVMTrainer(PSVMModel.PSVMParameters parms) {
      _parms = parms;
    }
    @Override
    public void run() {
      _model = new PSVM(_parms).trainModel().get();
    }
    private PSVMModel train() {
      return H2O.runOnLeaderNode(this)._model;
    }
  }
  
  @Test
  public void testProstate() {
    try {
      Scope.enter();
      Frame train = parseTestFile("./smalldata/logreg/prostate_train.csv")
              .toCategoricalCol("CAPSULE");
      Scope.track(train);

      Frame test = parseTestFile("./smalldata/logreg/prostate_test.csv")
              .toCategoricalCol("CAPSULE");
      Scope.track(test);

      PSVMModel.PSVMParameters parms = new PSVMModel.PSVMParameters();
      parms._train = train._key;
      parms._response_column = "CAPSULE";
      parms._gamma = 0.1;
      parms._hyper_param = 2;

      PSVM svm = new PSVM(parms);

      PSVMModel model = svm.trainModel().get();
      assertNotNull(model);
      Scope.track_generic(model);

      Frame predsTrain = model.score(train);
      Scope.track(predsTrain);

      ModelMetricsBinomial mmbTrain = (ModelMetricsBinomial) ModelMetrics.getFromDKV(model, train);
      assertNotNull(mmbTrain);

      Frame predsTest = model.score(test);
      Scope.track(predsTest);

      ModelMetricsBinomial mmbTest = (ModelMetricsBinomial) ModelMetrics.getFromDKV(model, test);
      assertNotNull(mmbTest);

      checkCM(model, test, test.vec(parms._response_column), predsTest.vec(0));
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testProstateWithCategoricals() {
    PSVMModel.PSVMModelOutput regular = trainOnProstate(false);
    PSVMModel.PSVMModelOutput encoded = trainOnProstate(true);

    assertEquals(encoded._training_metrics._MSE, regular._training_metrics._MSE, 0);
    assertEquals(encoded._validation_metrics._MSE, regular._validation_metrics._MSE, 0);
  }

  private PSVMModel.PSVMModelOutput trainOnProstate(boolean encode) {
    try {
      Scope.enter();
      Frame train;
      Frame valid;
      {
        Frame fr = parseTestFile("./smalldata/logreg/prostate.csv")
                .toCategoricalCol("CAPSULE")
                .toCategoricalCol("RACE");
        Scope.track(fr);

        if (encode) {
          fr.insertVec(0, "RACE", fr.remove("RACE"));
          Frame encoded = new FrameUtils.CategoricalOneHotEncoder(fr, new String[]{"CAPSULE"}).exec().get();
          Scope.track(encoded);
          
          fr = encoded;
        }

        Frame[] fs = splitFrameTrainValid(fr, 0.8, 0xCAFEBABE);
        train = Scope.track(fs[0]);
        valid = Scope.track(fs[1]);
      }
      
      PSVMModel.PSVMParameters parms = new PSVMModel.PSVMParameters();
      parms._train = train._key;
      parms._valid = valid._key;
      parms._response_column = "CAPSULE";
      parms._ignored_columns = new String[]{"ID"};
      parms._gamma = 0.4;
      parms._hyper_param = 2;
      parms._disable_training_metrics = false;

      PSVM svm = new PSVM(parms);

      PSVMModel model = svm.trainModel().get();
      assertNotNull(model);
      Scope.track_generic(model);

      return model._output;
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testSVMGuide1() {
    try {
      Scope.enter();
      Frame fr = parseTestFile("./smalldata/svm_test/svmguide1.svm")
              .toCategoricalCol("C1");
      Scope.track(fr);

      PSVMModel.PSVMParameters parms = new PSVMModel.PSVMParameters();
      parms._train = fr._key;
      parms._response_column = "C1";
      parms._gamma = 0.1;

      PSVM svm = new PSVM(parms);

      PSVMModel model = svm.trainModel().get();
      assertNotNull(model);
      Scope.track_generic(model);

      Frame test = parseTestFile("./smalldata/svm_test/svmguide1_test.svm")
              .toCategoricalCol("C1");
      Scope.track(test);

      Frame testPreds = model.score(test);
      Scope.track(testPreds);

      ModelMetricsBinomial mmb = (ModelMetricsBinomial) ModelMetrics.getFromDKV(model, test);
      assertNotNull(mmb);
      assertEquals(0.1, mmb.mse(), 0.05);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testSVMGuide3() {
    try {
      Scope.enter();
      Frame fr = parseTestFile("./smalldata/svm_test/svmguide3scale.svm");
      Scope.track(fr);

      PSVMModel.PSVMParameters parms = new PSVMModel.PSVMParameters();
      parms._train = fr._key;
      parms._response_column = "C1";
      parms._gamma = 0.125;
      parms._hyper_param = 1;

      PSVM svm = new PSVM(parms);

      PSVMModel model = svm.trainModel().get();
      assertNotNull(model);
      Scope.track_generic(model);

      Frame test = parseTestFile("./smalldata/svm_test/svmguide3scale_test.svm");
      Scope.track(test);

      Frame testPreds = model.score(test);
      Scope.track(testPreds);

      // test frame has a constant (+1) response
      assertEquals(1.0, testPreds.vec(0).nzCnt() / (double) testPreds.numRows(), 0.15); // this essentially means >= 0.85

      // check confusion matrix
      checkCM(model, test, test.vec(parms._response_column), testPreds.vec(0));
    } finally {
      Scope.exit();
    }
  }

  private static Frame[] splitFrameTrainValid(Frame fr, double ratio, long seed) {
    return ShuffleSplitFrame.shuffleSplitFrame(fr, new Key[]{Key.<Frame>make(fr._key + "_train"),Key.<Frame>make(fr._key + "_valid")}, new double[]{ratio, 1-ratio}, seed);
  }
  
  private static void checkCM(PSVMModel model, Frame frame, Vec actuals, Vec predicted) {
    String[] domain = model._output._domains[model._output.responseIdx()];
    Scope.enter();
    try {
      if (! actuals.isCategorical()) {
        actuals = Scope.track(actuals.toCategoricalVec());
        if ("1".equals(actuals.domain()[actuals.domain().length - 1])) {
          actuals.domain()[actuals.domain().length - 1] = "+1";
        }
        actuals = Scope.track(actuals.adaptTo(domain));
      }
      if (! predicted.isCategorical()) {
        predicted = Scope.track(predicted.toCategoricalVec());
      }
      ConfusionMatrix expectedCM = ConfusionMatrixUtils.buildCM(actuals, predicted);
      ConfusionMatrix actualCM = ModelMetricsBinomial.getFromDKV(model, frame).cm();
      System.out.println(actualCM.table().toString());
      ConfusionMatrixUtils.assertCMEqual(domain, expectedCM._cm, actualCM);
    } finally {
      Scope.exit();
    }
  }

  private static void checkScorers(PSVMModel model, Frame f, Vec expected) {
    assertEquals(model._parms._response_column, f.name(0)); // expect SVM-light kind of format
    Frame adapted = new Frame(f); // naive adapt
    adapted.remove(model._parms._response_column);
    Frame scores = new CheckScorersTask(model._key).doAll(3, Vec.T_NUM, adapted).outputFrame();
    Scope.track(scores);
    assertVecEquals(expected, scores.vec(0), 1e-6); // per-row
    assertVecEquals(expected, scores.vec(1), 1e-6); // bulk (raw)
    assertVecEquals(expected, scores.vec(2), 1e-6); // bulk (pojo)
  }

  private static class CheckScorersTask extends MRTask {

    private final Key<PSVMModel> _model_key;
    private transient PSVMModel _model;

    CheckScorersTask(Key<PSVMModel> modelKey) {
      _model_key = modelKey;
    }

    @Override
    protected void setupLocal() {
      _model = _model_key.get();
    }

    @Override
    public void map(Chunk[] cs, NewChunk[] ncs) {
      final double rho = _model._output._rho;
      
      // per row scoring (MOJO-like)
      final SupportVectorScorer scorer = ScorerFactory.makeScorer(
              _model._parms._kernel_type, _model._parms.kernelParms(), _model._output._compressed_svs);
      double[] row = new double[cs.length];
      for (int i = 0; i < cs[0]._len; i++) {
        for (int j = 0; j < cs.length; j++) {
          row[j] = cs[j].atd(i);
        }
        double s = scorer.score0(row);
        ncs[0].addNum(s + rho);
      }

      // bulk scoring (raw bytes)
      final BulkSupportVectorScorer rawBulkScorer = BulkScorerFactory.makeScorer(
              _model._parms._kernel_type, _model._parms.kernelParms(), _model._output._compressed_svs,
              (int) _model._output._svs_count, true);
      double[] scoresRaw = rawBulkScorer.bulkScore0(cs);
      for (double s : scoresRaw) {
        ncs[1].addNum(s + rho);
      }

      // bulk scoring (parsed objects)
      final BulkSupportVectorScorer pojoBulkScorer = BulkScorerFactory.makeScorer(
              _model._parms._kernel_type, _model._parms.kernelParms(), _model._output._compressed_svs,
              (int) _model._output._svs_count, true);
      double[] scoresPojo = pojoBulkScorer.bulkScore0(cs);
      for (double s : scoresPojo) {
        ncs[2].addNum(s + rho);
      }
    }
  }
  
}
