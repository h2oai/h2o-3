package hex.svm;

import hex.*;
import hex.splitframe.ShuffleSplitFrame;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.*;
import water.rapids.ast.prims.math.AstSgn;
import water.util.FrameUtils;

import static org.junit.Assert.*;

public class SVMTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void testSplice() {
    try {
      Scope.enter();
      Frame fr = parse_test_file("./smalldata/splice/splice.svm");
      Scope.track(fr);

      SVMModel.SVMParameters parms = new SVMModel.SVMParameters();
      parms._gamma = 0.01;
      parms._rank_ratio = 0.1;
      parms._train = fr._key;
      parms._response_column = "C1";

      SVMModel model = new SVMTrainer(parms).train();
      assertNotNull(model);
      Scope.track_generic(model);
      assertEquals(2.38873807, model._output._rho, 1e-6);
      assertEquals(662, model._output._svs_count);
      assertEquals(612, model._output._bsv_count);
      assertNotNull(model._output._compressed_svs);
      assertNotEquals(0, model._output._compressed_svs.length);

      Frame expected = parse_test_file("./smalldata/splice/splice_icf100_preds.csv");
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
    } finally {
      Scope.exit();
    }
  }

  private static class SVMTrainer extends H2O.RemoteRunnable<SVMTrainer> {
    private final SVMModel.SVMParameters _parms;
    private SVMModel _model;
    private SVMTrainer(SVMModel.SVMParameters parms) {
      _parms = parms;
    }
    @Override
    public void run() {
      _model = new SVM(_parms).trainModel().get();
    }
    private SVMModel train() {
      return H2O.runOnLeaderNode(this)._model;
    }
  }
  
  @Test
  public void testProstate() {
    try {
      Scope.enter();
      Frame train = parse_test_file("./smalldata/logreg/prostate_train.csv")
              .toCategoricalCol("CAPSULE");
      Scope.track(train);

      Frame test = parse_test_file("./smalldata/logreg/prostate_test.csv")
              .toCategoricalCol("CAPSULE");
      Scope.track(test);

      SVMModel.SVMParameters parms = new SVMModel.SVMParameters();
      parms._train = train._key;
      parms._response_column = "CAPSULE";
      parms._gamma = 0.1;
      parms._hyper_parm = 2;

      SVM svm = new SVM(parms);

      SVMModel model = svm.trainModel().get();
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
    SVMModel.SVMModelOutput regular = trainOnProstate(false);
    SVMModel.SVMModelOutput encoded = trainOnProstate(true);

    assertEquals(encoded._training_metrics._MSE, regular._training_metrics._MSE, 0);
    assertEquals(encoded._validation_metrics._MSE, regular._validation_metrics._MSE, 0);
  }

  private SVMModel.SVMModelOutput trainOnProstate(boolean encode) {
    try {
      Scope.enter();
      Frame train;
      Frame valid;
      {
        Frame fr = parse_test_file("./smalldata/logreg/prostate.csv")
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
      
      SVMModel.SVMParameters parms = new SVMModel.SVMParameters();
      parms._train = train._key;
      parms._valid = valid._key;
      parms._response_column = "CAPSULE";
      parms._ignored_columns = new String[]{"ID"};
      parms._gamma = 0.4;
      parms._hyper_parm = 2;
      
      SVM svm = new SVM(parms);

      SVMModel model = svm.trainModel().get();
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
      Frame fr = parse_test_file("./smalldata/svm_test/svmguide1.svm")
              .toCategoricalCol("C1");
      Scope.track(fr);

      SVMModel.SVMParameters parms = new SVMModel.SVMParameters();
      parms._train = fr._key;
      parms._response_column = "C1";
      parms._gamma = 0.1;

      SVM svm = new SVM(parms);

      SVMModel model = svm.trainModel().get();
      assertNotNull(model);
      Scope.track_generic(model);

      Frame test = parse_test_file("./smalldata/svm_test/svmguide1_test.svm")
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
      Frame fr = parse_test_file("./smalldata/svm_test/svmguide3scale.svm");
      Scope.track(fr);

      SVMModel.SVMParameters parms = new SVMModel.SVMParameters();
      parms._train = fr._key;
      parms._response_column = "C1";
      parms._gamma = 0.125;
      parms._hyper_parm = 1;

      SVM svm = new SVM(parms);

      SVMModel model = svm.trainModel().get();
      assertNotNull(model);
      Scope.track_generic(model);

      Frame test = parse_test_file("./smalldata/svm_test/svmguide3scale_test.svm");
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
  
  private static void checkCM(SVMModel model, Frame frame, Vec actuals, Vec predicted) {
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
      ConfusionMatrix expectedCM = ConfusionMatrixTest.buildCM(actuals, predicted);
      ConfusionMatrix actualCM = ModelMetricsBinomial.getFromDKV(model, frame).cm();
      System.out.println(actualCM.table().toString());
      ConfusionMatrixTest.assertCMEqual(domain, expectedCM._cm, actualCM);
    } finally {
      Scope.exit();
    }
  }
  
}
