package hex.glm;

import hex.CreateFrame;
import hex.ModelMetricsBinomialGLM;
import hex.SplitFrame;
import hex.deeplearning.DeepLearningModel.DeepLearningParameters.MissingValuesHandling;
import hex.glm.GLMModel.GLMParameters;
import hex.glm.GLMModel.GLMParameters.Family;
import hex.glm.GLMModel.GLMParameters.Solver;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.*;
import water.util.VecUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * Created by tomasnykodym on 4/26/15.
 */
public class GLMBasicTestBinomial extends TestUtil {
  static Frame _prostateTrain; // prostate_cat_replaced
  static Frame _prostateTrainUpsampled; // prostate_cat_replaced
  static Frame _prostateTest; // prostate_cat_replaced
  static Frame _abcd; // tiny corner case dataset
  static Frame _airlinesTrain;
  static Frame _airlinesTest;
  double _tol = 1e-10;

  // test and make sure the h2opredict, pojo and mojo predict agrees with multinomial dataset that includes
  // both enum and numerical datasets
  @Test
  public void testBinomialPredMojoPojo() {
    try {
      Scope.enter();
      CreateFrame cf = new CreateFrame();
      Random generator = new Random();
      int numRows = generator.nextInt(10000)+15000+200;
      int numCols = generator.nextInt(17)+3;
      cf.rows= numRows;
      cf.cols = numCols;
      cf.factors=10;
      cf.has_response=true;
      cf.response_factors = 2;
      cf.positive_response=true;
      cf.missing_fraction = 0;
      cf.seed = System.currentTimeMillis();
      System.out.println("Createframe parameters: rows: "+numRows+" cols:"+numCols+" seed: "+cf.seed);

      Frame trainMultinomial = Scope.track(cf.execImpl().get());
      SplitFrame sf = new SplitFrame(trainMultinomial, new double[]{0.8,0.2}, new Key[] {Key.make("train.hex"), Key.make("test.hex")});
      sf.exec().get();
      Key[] ksplits = sf._destination_frames;
      Frame tr = DKV.get(ksplits[0]).get();
      Frame te = DKV.get(ksplits[1]).get();
      Scope.track(tr);
      Scope.track(te);

      GLMModel.GLMParameters paramsO = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.binomial,
              GLMModel.GLMParameters.Family.binomial.defaultLink, new double[]{0}, new double[]{0}, 0, 0);
      paramsO._train = tr._key;
      paramsO._lambda_search = false;
      paramsO._response_column = "response";
      paramsO._lambda = new double[]{0};
      paramsO._alpha = new double[]{0.001};  // l1pen
      paramsO._objective_epsilon = 1e-6;
      paramsO._beta_epsilon = 1e-4;
      paramsO._standardize = false;

      GLMModel model = new GLM(paramsO).trainModel().get();
      Scope.track_generic(model);

      Frame pred = model.score(te);
      Scope.track(pred);
      Assert.assertTrue(model.testJavaScoring(te, pred, _tol));
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testOffset() {
    GLMModel model = null;

    double [] offset_train = new double[] {
      -0.39771185,+1.20479170,-0.16374109,-0.97885903,-1.42996530,+0.83474893,+0.83474893,-0.74488827,+0.83474893,+0.86851236,
      +1.41589611,+1.41589611,-1.42996530,-0.39771185,-2.01111248,-0.39771185,-0.16374109,+0.62364452,-0.39771185,+0.60262749,
      -0.06143251,-1.42996530,-0.06143251,-0.06143251,+0.14967191,-0.06143251,-0.39771185,+0.14967191,+1.20479170,-0.39771185,
      -0.16374109,-0.06143251,-0.06143251,-1.42996530,-0.39771185,-0.39771185,-0.64257969,+1.65774729,-0.97885903,-0.39771185,
      -0.39771185,-0.39771185,-1.42996530,+1.41589611,-0.06143251,-0.06143251,-0.39771185,-0.06143251,-0.06143251,-0.39771185,
      -0.06143251,+0.14967191,-0.39771185,-1.42996530,-0.39771185,-0.64257969,-0.39771185,-0.06143251,-0.06143251,-0.06143251,
      -1.42996530,-2.01111248,-0.06143251,-0.39771185,-0.39771185,-1.42996530,-0.39771185,-1.42996530,-0.06143251,+1.41589611,
      +0.14967191,-1.42996530,-1.42996530,-0.06143251,-1.42996530,-1.42996530,-0.06143251,-1.42996530,-0.06143251,-0.39771185,
      -0.06143251,-1.42996530,-0.06143251,-0.39771185,-1.42996530,-0.06143251,-0.06143251,-0.06143251,-1.42996530,-0.39771185,
      -1.42996530,-0.43147527,-0.39771185,-0.39771185,-0.39771185,-1.42996530,-1.42996530,-0.43147527,-0.39771185,-0.39771185,
      -0.39771185,-0.39771185,-1.42996530,-1.42996530,-1.42996530,-0.39771185,+0.14967191,+1.41589611,-1.42996530,+1.41589611,
      -1.42996530,+1.41589611,-0.06143251,+0.14967191,-0.39771185,-0.97885903,-1.42996530,-0.39771185,-0.39771185,-0.39771185,
      -0.39771185,-1.42996530,-0.39771185,-0.97885903,-0.06143251,-0.06143251,+0.86851236,-0.39771185,-0.39771185,-0.06143251,
      -0.39771185,-0.39771185,-0.06143251,+0.14967191,-1.42996530,-1.42996530,-0.39771185,+1.20479170,-1.42996530,-0.39771185,
      -0.06143251,-1.42996530,-0.97885903,+0.14967191,+0.14967191,-1.42996530,-1.42996530,-0.39771185,-0.06143251,-0.43147527,
      -0.06143251,-0.39771185,-1.42996530,-0.06143251,-0.39771185,-0.39771185,-1.42996530,-0.39771185,-0.39771185,-0.06143251,
      -0.39771185,-0.39771185,+0.14967191,-0.06143251,+1.41589611,-0.06143251,-0.39771185,-0.39771185,-0.06143251,-1.42996530,
      -0.06143251,-1.42996530,-0.39771185,-0.64257969,-0.06143251,+1.20479170,-0.43147527,-0.97885903,-0.39771185,-0.39771185,
      -0.39771185,+0.14967191,-2.01111248,-1.42996530,-0.06143251,+0.83474893,-1.42996530,-1.42996530,-2.01111248,-1.42996530,
      -0.06143251,+0.86851236,+0.05524374,-0.39771185,-0.39771185,-0.39771185,+1.41589611,-1.42996530,-0.39771185,-1.42996530,
      -0.39771185,-0.39771185,-0.06143251,+0.14967191,-1.42996530,-0.39771185,-1.42996530,-1.42996530,-0.39771185,-0.39771185,
      -0.06143251,-1.42996530,-0.97885903,-1.42996530,-0.39771185,-0.06143251,-0.39771185,-0.06143251,-1.42996530,-1.42996530,
      -0.06143251,-1.42996530,-0.39771185,+0.14967191,-0.06143251,-1.42996530,-1.42996530,+0.14967191,-0.39771185,-0.39771185,
      -1.42996530,-0.06143251,-0.06143251,-1.42996530,-0.06143251,-1.42996530,+0.14967191,+1.20479170,-1.42996530,-0.06143251,
      -0.39771185,-0.39771185,-0.06143251,+0.14967191,-0.06143251,-1.42996530,-1.42996530,-1.42996530,-0.39771185,-0.39771185,
      -0.39771185,+0.86851236,-0.06143251,-0.97885903,-0.06143251,-0.64257969,+0.14967191,+0.86851236,-0.39771185,-0.39771185,
      -0.39771185,-0.64257969,-1.42996530,-0.06143251,-0.39771185,-0.39771185,-1.42996530,-1.42996530,-0.06143251,+0.14967191,
      -0.06143251,+0.86851236,-0.97885903,-1.42996530,-1.42996530,-1.42996530,-1.42996530,+0.86851236,+0.14967191,-1.42996530,
      -0.97885903,-1.42996530,-1.42996530,-0.06143251,+0.14967191,-1.42996530,-0.64257969,-2.01111248,-0.97885903,-0.39771185
    };

    double [] offset_test = new double [] {
      +1.20479170,-1.42996530,-1.42996530,-1.42996530,-0.39771185,-0.39771185,-0.39771185,-0.39771185,-0.06143251,-0.06143251,
      -0.06143251,-0.39771185,-0.39771185,-0.39771185,-0.06143251,-1.42996530,-0.39771185,+0.86851236,-0.06143251,+1.20479170,
      -1.42996530,+1.20479170,-0.06143251,-0.06143251,+1.20479170,+0.14967191,-0.39771185,-0.39771185,-0.39771185,+0.14967191,
      -0.39771185,-1.42996530,-0.97885903,-0.39771185,-2.01111248,-1.42996530,-0.39771185,-0.06143251,-0.39771185,+0.14967191,
      +0.14967191,-0.06143251,+0.14967191,-1.42996530,-0.06143251,+1.20479170,-0.06143251,-0.06143251,-0.39771185,+1.41589611,
      -0.39771185,-1.42996530,+0.14967191,-1.42996530,+0.14967191,-1.42996530,-0.06143251,-1.42996530,-0.43147527,+0.86851236,
      -0.39771185,-0.39771185,-0.06143251,-0.06143251,-0.39771185,-0.06143251,-1.42996530,-0.39771185,-0.06143251,-0.39771185,
      +0.14967191,+1.41589611,-0.39771185,-0.39771185,+1.41589611,+0.14967191,-0.64257969,-1.42996530,+0.14967191,-0.06143251,
      -1.42996530,-1.42996530,-0.39771185,-1.42996530,-1.42996530,-0.39771185,-0.39771185,+0.14967191,-0.39771185,-0.39771185
    };

    double [] pred_test = new double[] {
      +0.904121393,+0.208967788,+0.430064980,+0.063563661,+0.420390154,+0.300577441,+0.295405224,+0.629308103,+0.324441281,+0.563699642,
      +0.639184514,+0.082179963,+0.462563464,+0.344521206,+0.351577428,+0.339043527,+0.435998848,+0.977492380,+0.581711493,+0.974570868,
      +0.143071580,+0.619404446,+0.362033860,+0.570068411,+0.978069860,+0.562268311,+0.158184617,+0.608996256,+0.162259728,+0.578987913,
      +0.289325534,+0.286251414,+0.749507189,+0.469565216,+0.069466938,+0.112383575,+0.481307819,+0.398935638,+0.589102941,+0.337382932,
      +0.409333118,+0.366674225,+0.640036454,+0.263683222,+0.779866040,+0.635071654,+0.377463657,+0.518320766,+0.322693268,+0.833778660,
      +0.459703088,+0.115189180,+0.694175044,+0.132131043,+0.402412653,+0.270949939,+0.353738040,+0.256239963,+0.467322078,+0.956569336,
      +0.172230761,+0.265478787,+0.559113124,+0.248798085,+0.140841191,+0.607922656,+0.113752627,+0.289291072,+0.241123681,+0.290387448,
      +0.782068785,+0.927494110,+0.176397617,+0.263745527,+0.992043885,+0.653252457,+0.385483627,+0.222333476,+0.537344319,+0.202589973,
      +0.334941144,+0.172066050,+0.292733797,+0.001169431,+0.114393635,+0.153848294,+0.632500120,+0.387718306,+0.269126887,+0.564594040
    };

    Vec offsetVecTrain = _prostateTrain.anyVec().makeZero();
    try( Vec.Writer vw = offsetVecTrain.open() ) {
      for (int i = 0; i < offset_train.length; ++i)
        vw.set(i, offset_train[i]);
    }

    Vec offsetVecTest = _prostateTest.anyVec().makeZero();
    try( Vec.Writer vw = offsetVecTest.open() ) {
      for (int i = 0; i < offset_test.length; ++i)
        vw.set(i, offset_test[i]);
    }

    Key fKeyTrain = Key.make("prostate_with_offset_train");
    Key fKeyTest  = Key.make("prostate_with_offset_test");
    Frame fTrain = new Frame(fKeyTrain, new String[]{"offset"}, new Vec[]{offsetVecTrain});
    fTrain.add(_prostateTrain.names(), _prostateTrain.vecs());
    DKV.put(fKeyTrain,fTrain);
    Frame fTest = new Frame(fKeyTest, new String[]{"offset"}, new Vec[]{offsetVecTest});
    fTest.add(_prostateTest.names(),_prostateTest.vecs());
    DKV.put(fKeyTest,fTest);
//    Call:  glm(formula = CAPSULE ~ . - RACE - DPROS - DCAPS, family = binomial,
//      data = train, offset = offset_train)
//
//    Coefficients:
//    (Intercept)          AGE          PSA          VOL      GLEASON
//      -4.839677    -0.007815     0.023796    -0.007325     0.794385
//
//    Degrees of Freedom: 289 Total (i.e. Null);  285 Residual
//    Null Deviance:	   355.7
//    Residual Deviance: 305.1 	AIC: 315.1
    String [] cfs1 = new String [] { "Intercept",  "AGE"   ,  "PSA",    "VOL", "GLEASON"};
    double [] vals = new double [] {-4.839677,    -0.007815,    0.023796, -0.007325, 0.794385};
    GLMParameters params = new GLMParameters(Family.binomial);
    params._response_column = "CAPSULE";
    params._ignored_columns = new String[]{"ID","RACE","DPROS","DCAPS"};
    params._train = fKeyTrain;
    params._valid = fKeyTest;
    params._offset_column = "offset";
    params._lambda = new double[]{0};
    params._alpha = new double[]{0};
    params._standardize = false;
    params._objective_epsilon = 0;
    params._gradient_epsilon = 1e-6;
    params._max_iterations = 100; // not expected to reach max iterations here
    try {
      for (Solver s : new Solver[]{Solver.IRLSM}) { //{Solver.AUTO, Solver.IRLSM, Solver.L_BFGS, Solver.COORDINATE_DESCENT_NAIVE, Solver.COORDINATE_DESCENT}){
        Frame scoreTrain = null, scoreTest = null;
        try {
          params._solver = s;
          System.out.println("SOLVER = " + s);
          model = new GLM(params,Key.make("testOffset_" + s)).trainModel().get();
          HashMap<String, Double> coefs = model.coefficients();
          System.out.println("coefs = " + coefs);
          boolean CD = (s == Solver.COORDINATE_DESCENT || s == Solver.COORDINATE_DESCENT_NAIVE);
          System.out.println(" solver " + s);
          System.out.println("validation = " + model._output._training_metrics);
          for (int i = 0; i < cfs1.length; ++i)
            assertEquals(vals[i], coefs.get(cfs1[i]), CD?5e-2:1e-4);
          assertEquals(355.7, GLMTest.nullDeviance(model), 1e-1);
          assertEquals(305.1, GLMTest.residualDeviance(model), 1e-1);
          assertEquals(289,   GLMTest.nullDOF(model), 0);
          assertEquals(285,   GLMTest.resDOF(model), 0);
          assertEquals(315.1, GLMTest.aic(model), 1e-1);
          assertEquals(76.8525, GLMTest.residualDevianceTest(model),CD?1e-3:1e-4);
          // test scoring
          try {
            scoreTrain = model.score(_prostateTrain);
            assertTrue("shoul've thrown IAE", false);
          } catch (IllegalArgumentException iae) {
            assertTrue(iae.getMessage().contains("Test/Validation dataset is missing offset column"));
          }
          hex.ModelMetricsBinomialGLM mmTrain = (ModelMetricsBinomialGLM)hex.ModelMetricsBinomial.getFromDKV(model, fTrain);
          hex.AUC2 adata = mmTrain._auc;
          assertEquals(model._output._training_metrics.auc_obj()._auc, adata._auc, 1e-8);
          assertEquals(model._output._training_metrics._MSE, mmTrain._MSE, 1e-8);
          assertEquals(((ModelMetricsBinomialGLM) model._output._training_metrics)._resDev, mmTrain._resDev, 1e-8);
          scoreTrain = model.score(fTrain);
          mmTrain = (ModelMetricsBinomialGLM)hex.ModelMetricsBinomial.getFromDKV(model, fTrain);
          adata = mmTrain._auc;
          assertEquals(model._output._training_metrics.auc_obj()._auc, adata._auc, 1e-8);
          assertEquals(model._output._training_metrics._MSE, mmTrain._MSE, 1e-8);
          assertEquals(((ModelMetricsBinomialGLM) model._output._training_metrics)._resDev, mmTrain._resDev, 1e-8);
          scoreTest = model.score(fTest);
          ModelMetricsBinomialGLM mmTest = (ModelMetricsBinomialGLM)hex.ModelMetricsBinomial.getFromDKV(model, fTest);
          adata = mmTest._auc;
          assertEquals(model._output._validation_metrics.auc_obj()._auc, adata._auc, 1e-8);
          assertEquals(model._output._validation_metrics._MSE, mmTest._MSE, 1e-8);
          assertEquals(((ModelMetricsBinomialGLM) model._output._validation_metrics)._resDev, mmTest._resDev, 1e-8);
          // test the actual predictions
          Vec.Reader preds = scoreTest.vec("p1").new Reader();
          for(int i = 0; i < pred_test.length; ++i)
            assertEquals(pred_test[i],preds.at(i),CD?1e-3:1e-6);
          GLMTest.testScoring(model,fTrain);
        } finally {
          if (model != null) model.delete();
          if (scoreTrain != null) scoreTrain.delete();
          if (scoreTest != null)  scoreTest.delete();
        }
      }
    } finally {
      if (fTrain != null) {
        fTrain.remove("offset").remove();
        DKV.remove(fTrain._key);
      }
      if(fTest != null){
        fTest.remove("offset").remove();
        DKV.remove(fTest._key);
      }
    }
  }

  // test various problematic inputs to make sure fron-tend (ignoring/moving cols) works.

  @Test
  public void testCornerCases() {
//    new GLM2("GLM testing constant offset on a toy dataset.", Key.make(), modelKey, new GLM2.Source(fr, fr.vec("D"), false, false, fr.vec("E")), Family.gaussian).setRegularization(new double[]{0}, new double[]{0}).doInit().fork().get();
//    just test it does not blow up and the model is sane
//    model = DKV.get(modelKey).get();
//    assertEquals(model.coefficients().get("E"), 1, 0); // should be exactly 1
    GLMParameters parms = new GLMParameters(Family.gaussian);
    parms._response_column = "D";
    parms._offset_column = "E";
    parms._train = _abcd._key;
    parms._intercept = false;
    parms._standardize = false;
    GLMModel m = null;
    for(Solver s:new Solver[]{Solver.IRLSM,Solver.COORDINATE_DESCENT}) {
      parms._solver = s;
      try {
        m = new GLM(parms).trainModel().get();
        GLMTest.testScoring(m, _abcd);
        System.out.println(m.coefficients());
      } finally {
        if (m != null) m.delete();
      }
    }
  }

  @Test
  public void testNoInterceptWithOffset() {
    GLMModel model = null;
    double [] offset_train = new double[] {
      -0.39771185,+1.20479170,-0.16374109,-0.97885903,-1.42996530,+0.83474893,+0.83474893,-0.74488827,+0.83474893,+0.86851236,
      +1.41589611,+1.41589611,-1.42996530,-0.39771185,-2.01111248,-0.39771185,-0.16374109,+0.62364452,-0.39771185,+0.60262749,
      -0.06143251,-1.42996530,-0.06143251,-0.06143251,+0.14967191,-0.06143251,-0.39771185,+0.14967191,+1.20479170,-0.39771185,
      -0.16374109,-0.06143251,-0.06143251,-1.42996530,-0.39771185,-0.39771185,-0.64257969,+1.65774729,-0.97885903,-0.39771185,
      -0.39771185,-0.39771185,-1.42996530,+1.41589611,-0.06143251,-0.06143251,-0.39771185,-0.06143251,-0.06143251,-0.39771185,
      -0.06143251,+0.14967191,-0.39771185,-1.42996530,-0.39771185,-0.64257969,-0.39771185,-0.06143251,-0.06143251,-0.06143251,
      -1.42996530,-2.01111248,-0.06143251,-0.39771185,-0.39771185,-1.42996530,-0.39771185,-1.42996530,-0.06143251,+1.41589611,
      +0.14967191,-1.42996530,-1.42996530,-0.06143251,-1.42996530,-1.42996530,-0.06143251,-1.42996530,-0.06143251,-0.39771185,
      -0.06143251,-1.42996530,-0.06143251,-0.39771185,-1.42996530,-0.06143251,-0.06143251,-0.06143251,-1.42996530,-0.39771185,
      -1.42996530,-0.43147527,-0.39771185,-0.39771185,-0.39771185,-1.42996530,-1.42996530,-0.43147527,-0.39771185,-0.39771185,
      -0.39771185,-0.39771185,-1.42996530,-1.42996530,-1.42996530,-0.39771185,+0.14967191,+1.41589611,-1.42996530,+1.41589611,
      -1.42996530,+1.41589611,-0.06143251,+0.14967191,-0.39771185,-0.97885903,-1.42996530,-0.39771185,-0.39771185,-0.39771185,
      -0.39771185,-1.42996530,-0.39771185,-0.97885903,-0.06143251,-0.06143251,+0.86851236,-0.39771185,-0.39771185,-0.06143251,
      -0.39771185,-0.39771185,-0.06143251,+0.14967191,-1.42996530,-1.42996530,-0.39771185,+1.20479170,-1.42996530,-0.39771185,
      -0.06143251,-1.42996530,-0.97885903,+0.14967191,+0.14967191,-1.42996530,-1.42996530,-0.39771185,-0.06143251,-0.43147527,
      -0.06143251,-0.39771185,-1.42996530,-0.06143251,-0.39771185,-0.39771185,-1.42996530,-0.39771185,-0.39771185,-0.06143251,
      -0.39771185,-0.39771185,+0.14967191,-0.06143251,+1.41589611,-0.06143251,-0.39771185,-0.39771185,-0.06143251,-1.42996530,
      -0.06143251,-1.42996530,-0.39771185,-0.64257969,-0.06143251,+1.20479170,-0.43147527,-0.97885903,-0.39771185,-0.39771185,
      -0.39771185,+0.14967191,-2.01111248,-1.42996530,-0.06143251,+0.83474893,-1.42996530,-1.42996530,-2.01111248,-1.42996530,
      -0.06143251,+0.86851236,+0.05524374,-0.39771185,-0.39771185,-0.39771185,+1.41589611,-1.42996530,-0.39771185,-1.42996530,
      -0.39771185,-0.39771185,-0.06143251,+0.14967191,-1.42996530,-0.39771185,-1.42996530,-1.42996530,-0.39771185,-0.39771185,
      -0.06143251,-1.42996530,-0.97885903,-1.42996530,-0.39771185,-0.06143251,-0.39771185,-0.06143251,-1.42996530,-1.42996530,
      -0.06143251,-1.42996530,-0.39771185,+0.14967191,-0.06143251,-1.42996530,-1.42996530,+0.14967191,-0.39771185,-0.39771185,
      -1.42996530,-0.06143251,-0.06143251,-1.42996530,-0.06143251,-1.42996530,+0.14967191,+1.20479170,-1.42996530,-0.06143251,
      -0.39771185,-0.39771185,-0.06143251,+0.14967191,-0.06143251,-1.42996530,-1.42996530,-1.42996530,-0.39771185,-0.39771185,
      -0.39771185,+0.86851236,-0.06143251,-0.97885903,-0.06143251,-0.64257969,+0.14967191,+0.86851236,-0.39771185,-0.39771185,
      -0.39771185,-0.64257969,-1.42996530,-0.06143251,-0.39771185,-0.39771185,-1.42996530,-1.42996530,-0.06143251,+0.14967191,
      -0.06143251,+0.86851236,-0.97885903,-1.42996530,-1.42996530,-1.42996530,-1.42996530,+0.86851236,+0.14967191,-1.42996530,
      -0.97885903,-1.42996530,-1.42996530,-0.06143251,+0.14967191,-1.42996530,-0.64257969,-2.01111248,-0.97885903,-0.39771185
    };

    double [] offset_test = new double [] {
      +1.65774729,-0.97700971,-0.97700971,-0.97700971,+0.05524374,+0.05524374,+0.05524374,+0.05524374,+0.39152308,+0.39152308,
      +0.39152308,+0.05524374,+0.05524374,+0.05524374,+0.39152308,-0.97700971,+0.05524374,+1.32146795,+0.39152308,+1.65774729,
      -0.97700971,+1.65774729,+0.39152308,+0.39152308,+1.65774729,+0.60262749,+0.05524374,+0.05524374,+0.05524374,+0.60262749,
      +0.05524374,-0.97700971,-0.97885903,+0.05524374,-2.01111248,-0.97700971,+0.05524374,+0.39152308,+0.05524374,+0.60262749,
      +0.60262749,+0.39152308,+0.60262749,-0.97700971,+0.39152308,+1.65774729,+0.39152308,+0.39152308,+0.05524374,+1.86885170,
      +0.05524374,-0.97700971,+0.60262749,-0.97700971,+0.60262749,-0.97700971,+0.39152308,-0.97700971,-0.43147527,+1.32146795,
      +0.05524374,+0.05524374,+0.39152308,+0.39152308,+0.05524374,+0.39152308,-0.97700971,+0.05524374,+0.39152308,+0.05524374,
      +0.60262749,+1.86885170,+0.05524374,+0.05524374,+1.86885170,+0.60262749,-0.64257969,-0.97700971,+0.60262749,+0.39152308,
      -0.97700971,-0.97700971,+0.05524374,-0.97700971,-0.97700971,+0.05524374,+0.05524374,+0.60262749,+0.05524374,+0.05524374
    };

    double [] pred_test = new double[] {
      +0.88475366,+0.23100271,+0.40966315,+0.08957188,+0.47333302,+0.44622513,+0.56450046,+0.74271010,+0.45129280,+0.72359111,
      +0.67918401,+0.19882802,+0.42330391,+0.62734862,+0.38055506,+0.47286476,+0.40180469,+0.97907526,+0.61428344,+0.97109299,
      +0.30489181,+0.81303545,+0.36130639,+0.65434899,+0.98863675,+0.58301866,+0.37950467,+0.53679205,+0.30636941,+0.70320372,
      +0.45303278,+0.35011042,+0.78165074,+0.44915160,+0.09008065,+0.16789833,+0.45748862,+0.59328118,+0.75002334,+0.35170410,
      +0.57550279,+0.42038237,+0.76349569,+0.28883753,+0.84824847,+0.72396381,+0.56782477,+0.54078190,+0.51169047,+0.80828547,
      +0.52001699,+0.26202346,+0.81014557,+0.29986016,+0.62011569,+0.33034872,+0.62284802,+0.28303618,+0.38470707,+0.96444405,
      +0.36155179,+0.46368503,+0.65192144,+0.43597041,+0.30906461,+0.69259415,+0.21819579,+0.49998652,+0.57162728,+0.44255738,
      +0.80820564,+0.90616782,+0.49377901,+0.34235025,+0.99621673,+0.65768252,+0.43909050,+0.23205826,+0.71124897,+0.42908417,
      +0.47880901,+0.29185818,+0.42648317,+0.01247279,+0.18372518,+0.27281535,+0.63807876,+0.44563524,+0.32821696,+0.43636099
    };

    Vec offsetVecTrain = _prostateTrain.anyVec().makeZero();
    try( Vec.Writer vw = offsetVecTrain.open() ) {
      for (int i = 0; i < offset_train.length; ++i)
        vw.set(i, offset_train[i]);
    }

    Vec offsetVecTest = _prostateTest.anyVec().makeZero();
    try( Vec.Writer vw = offsetVecTest.open() ) {
      for (int i = 0; i < offset_test.length; ++i)
        vw.set(i, offset_test[i]);
    }

    Key fKeyTrain = Key.make("prostate_with_offset_train");
    Key fKeyTest  = Key.make("prostate_with_offset_test");
    Frame fTrain = new Frame(fKeyTrain, new String[]{"offset"}, new Vec[]{offsetVecTrain});
    fTrain.add(_prostateTrain.names(), _prostateTrain.vecs());
    DKV.put(fKeyTrain,fTrain);
    Frame fTest = new Frame(fKeyTest, new String[]{"offset"}, new Vec[]{offsetVecTest});
    fTest.add(_prostateTest.names(),_prostateTest.vecs());
    DKV.put(fKeyTest,fTest);
//    Call:  glm(formula = CAPSULE ~ . - ID - RACE - DCAPS - DPROS - 1, family = binomial,
//      data = train, offset = offset_train)
//
//    Coefficients:
//     AGE        PSA        VOL        GLEASON
//    -0.054102   0.027517  -0.008937   0.516363
//
//    Degrees of Freedom: 290 Total (i.e. Null);  286 Residual
//    Null Deviance:	    355.7
//    Residual Deviance: 313 	AIC: 321
    String [] cfs1 = new String [] { "Intercept",  "AGE"   ,  "PSA",     "VOL",    "GLEASON"};
    double [] vals = new double [] {  0,           -0.054102,  0.027517, -0.008937, 0.516363};
    GLMParameters params = new GLMParameters(Family.binomial);
    params._response_column = "CAPSULE";
    params._ignored_columns = new String[]{"ID","RACE","DPROS","DCAPS"};
    params._train = fKeyTrain;
    params._valid = fKeyTest;
    params._offset_column = "offset";
    params._lambda = new double[]{0};
    params._alpha = new double[]{0};
    params._standardize = false;
    params._objective_epsilon = 0;
    params._gradient_epsilon = 1e-6;
    params._max_iterations = 100; // not expected to reach max iterations here
    params._intercept = false;
    params._beta_epsilon = 1e-6;
    params._missing_values_handling = MissingValuesHandling.Skip;
    try {
      for (Solver s : new Solver[]{Solver.AUTO, Solver.IRLSM, Solver.L_BFGS,Solver.COORDINATE_DESCENT}) {
        Frame scoreTrain = null, scoreTest = null;
        try {
          params._solver = s;
          System.out.println("SOLVER = " + s);
          model = new GLM(params).trainModel().get();
          HashMap<String, Double> coefs = model.coefficients();
          System.out.println("coefs = " + coefs);
          boolean CD = s == Solver.COORDINATE_DESCENT;
          for (int i = 0; i < cfs1.length; ++i)
            assertEquals(vals[i], coefs.get(cfs1[i]), CD?1e-2:1e-4);
          assertEquals(355.7, GLMTest.nullDeviance(model), 1e-1);
          assertEquals(313.0, GLMTest.residualDeviance(model), 1e-1);
          assertEquals(290,   GLMTest.nullDOF(model), 0);
          assertEquals(286,   GLMTest.resDOF(model), 0);
          assertEquals(321,   GLMTest.aic(model), 1e-1);
          assertEquals(88.72363, GLMTest.residualDevianceTest(model),CD?1e-2:1e-4);
          // test scoring
          try {
            scoreTrain = model.score(_prostateTrain);
            assertTrue("shoul've thrown IAE", false);
          } catch (IllegalArgumentException iae) {
            assertTrue(iae.getMessage().contains("Test/Validation dataset is missing offset column"));
          }
          hex.ModelMetricsBinomialGLM mmTrain = (ModelMetricsBinomialGLM)hex.ModelMetricsBinomial.getFromDKV(model, fTrain);
          hex.AUC2 adata = mmTrain._auc;
          assertEquals(model._output._training_metrics.auc_obj()._auc, adata._auc, 1e-8);
          assertEquals(model._output._training_metrics._MSE, mmTrain._MSE, 1e-8);
          assertEquals(((ModelMetricsBinomialGLM) model._output._training_metrics)._resDev, mmTrain._resDev, 1e-8);
          scoreTrain = model.score(fTrain);
          mmTrain = (ModelMetricsBinomialGLM)hex.ModelMetricsBinomial.getFromDKV(model, fTrain);
          adata = mmTrain._auc;
          assertEquals(model._output._training_metrics.auc_obj()._auc, adata._auc, 1e-8);
          assertEquals(model._output._training_metrics._MSE, mmTrain._MSE, 1e-8);
          assertEquals(((ModelMetricsBinomialGLM) model._output._training_metrics)._resDev, mmTrain._resDev, 1e-8);
          scoreTest = model.score(fTest);
          ModelMetricsBinomialGLM mmTest = (ModelMetricsBinomialGLM)hex.ModelMetricsBinomial.getFromDKV(model, fTest);
          adata = mmTest._auc;
          assertEquals(model._output._validation_metrics.auc_obj()._auc, adata._auc, 1e-8);
          assertEquals(model._output._validation_metrics._MSE, mmTest._MSE, 1e-8);
          assertEquals(((ModelMetricsBinomialGLM) model._output._validation_metrics)._resDev, mmTest._resDev, 1e-8);
          GLMTest.testScoring(model,fTest);
          // test the actual predictions
          Vec.Reader preds = scoreTest.vec("p1").new Reader();
          for(int i = 0; i < pred_test.length; ++i)
            assertEquals(pred_test[i],preds.at(i), CD?1e-3:1e-6);// s == Solver.COORDINATE_DESCENT_NAIVE
        } finally {
          if (model != null) model.delete();
          if (scoreTrain != null) scoreTrain.delete();
          if (scoreTest != null) scoreTest.delete();
        }
      }
    } finally {
      if (fTrain != null) {
        fTrain.remove("offset").remove();
        DKV.remove(fTrain._key);
      }
      if(fTest != null) {
        fTest.remove("offset").remove();
        DKV.remove(fTest._key);
      }
    }
  }


  /*
  I have separated fitCOD from fitIRLSM and here is to test and made sure my changes are correct and they should
  generate the same coefficients as the old method.
   */
  @Test
  public void testCODGradients(){
    Scope.enter();
    Frame train;
    GLMParameters params = new GLMParameters(Family.binomial);
    GLMModel model = null;
    double[] goldenCoeffs = new double[] {3.315139700626461,0.9929054923448074, -1.0655426388234126,-3.7892948800495154,
            -2.0865591118999833,0.7867696413635438, -1.8615599223372965,1.0643138374753327,1.0986728686030014,
            0.10479049125777502,-1.7812358987823367,0.8647531123879351, 2.0849120863386665, -0.8774966728502775,
            -0.42153877552507385,3.2634187521383566,-1.9624237021260278,-0.34691475925538673, -1.646532127145956,
            1.6306397833575321,-3.044501939682644,0.8944464253207084,0.9895807015140112,-2.6717292838527205,
            -3.521867765191535, -2.4013802719175663, 5.1067282883832394,-2.6453709205608122, -3.1305849772174876,
            -3.431102221875896, 1.9010730022389033, -1.7904328104400145,-0.26701745669682453,-4.546721592533792,
            2.711748945952299,3.8151882842344387, -4.966584969931568,0.4072915316377201, -1.4716951033978412,
            -0.9600779293443411, -4.1033253093776505, -0.900138450590691, -3.41567157570875, 3.9532415786014323,
            -4.152487787492122,-4.816302785007451, -2.0646847130482033,4.916683882613988, -1.0828334669455186,
            -1.7535227306034435, 3.543101904113447,3.365050014714852,1.09947447201617, 3.801711118872804,
            -4.327701880800191, 2.949107493656704,1.2974956967558495,-4.766971479293396,3.608879061144071,
            -4.432383409841722, -1.945588990329554, -0.5741123903558344, 3.0082971652620296,1.2105456702290207,
            -2.0058145215980505, 4.633057967358068, 4.69177641215046,3.2313754439814084,-3.87050641561738,
            0.3902584675760716,1.2180174243872703,0.652166829687263, -2.934162573531005,1.8163438452614908,
            -1.1131945394628258,3.711779285831191,-1.2771611943142913,-3.0180677371604494, -1.0002653053027677,
            2.109019933558617,1.681095046876924,0.026980109195036545,4.515676428483863,3.4584826805338142,
            -4.884432397071569,-3.089270335492296, -0.2693643511214426,0.8903491083888826, 4.596551636071276,
            -1.9091402449943644, 0.42187489841011877,0.7507290472538346, -0.4545335921717534,-1.843531271821739,
            -10.450169230334527};

    try {
   //   train = parse_test_file("smalldata/glm_test/multinomial_3_class.csv");
      train = parse_test_file("smalldata/glm_test/binomial_1000Rows.csv");
      String[] names = train._names;
      Vec[] en = train.remove(new int[] {0,1,2,3,4,5,6});
      for (int cind = 0; cind <7; cind++) {
        train.add(names[cind], VecUtils.toCategoricalVec(en[cind]));
        Scope.track(en[cind]);
      }
      Scope.track(train);
      params._response_column = "C79";
      params._train = train._key;
      params._lambda = new double[]{4.881e-05};
      params._alpha = new double[]{0.5};
      params._objective_epsilon = 1e-6;
      params._beta_epsilon = 1e-4;
      params._max_iterations = 10; // one iteration
      params._seed = 12345; // don't think this one matters but set it anyway
      Solver s = Solver.COORDINATE_DESCENT;
      System.out.println("solver = " + s);
      params._solver = s;
      model = new GLM(params).trainModel().get();
      Scope.track_generic(model);

      compareGLMCoeffs(model._output._submodels[0].beta, goldenCoeffs, 1e-10);  // compare to original GLM
    } finally{
      Scope.exit();
    }
  }

  public void compareGLMCoeffs(double[] coeff1, double[] coeff2, double tol) {

    assertTrue(coeff1.length==coeff2.length); // assert coefficients having the same length first
    for (int index=0; index < coeff1.length; index++) {
      assert Math.abs(coeff1[index]-coeff2[index]) < tol :
              "coefficient difference "+Math.abs(coeff1[index]-coeff2[index])+" in row "+ index+" exceeded tolerance of "+tol;
    }
  }

  @Test
  public void testNoIntercept() {
    GLMModel model = null;
//    Call:  glm(formula = CAPSULE ~ . - 1 - RACE - DCAPS, family = binomial,
//      data = train)
//
//    Coefficients:
//    AGE        DPROSa    DPROSb    DPROSc    DPROSd       PSA       VOL   GLEASON
//    -0.00743  -6.46499  -5.60120  -5.18213  -5.70027   0.02753  -0.01235   0.86122
//
//    Degrees of Freedom: 290 Total (i.e. Null);  282 Residual
//    Null Deviance:	    402
//    Residual Deviance: 302.9 	AIC: 318.9
    String [] cfs1 = new String [] {"AGE",     "DPROS.a",  "DPROS.b",    "DPROS.c",  "DPROS.d",      "PSA",    "VOL",  "GLEASON"};
    double [] vals = new double [] {-0.00743,   -6.46499,  -5.60120,   -5.18213,    -5.70027,    0.02753,  -0.01235,   0.86122};
    GLMParameters params = new GLMParameters(Family.binomial);
    params._response_column = "CAPSULE";
    params._ignored_columns = new String[]{"ID","RACE","DCAPS"};
    params._train = _prostateTrain._key;
    params._valid = _prostateTest._key;
    params._lambda = new double[]{0};
    params._alpha = new double[]{0};
    params._standardize = false;
    params._intercept = false;
    params._objective_epsilon = 0;
    params._gradient_epsilon = 1e-6;
    params._missing_values_handling = MissingValuesHandling.Skip;
    params._max_iterations = 100; // not expected to reach max iterations here
    for(Solver s:new Solver[]{Solver.AUTO,Solver.IRLSM,Solver.L_BFGS, Solver.COORDINATE_DESCENT}) {
      Frame scoreTrain = null, scoreTest = null;
      try {
        params._solver = s;
        System.out.println("SOLVER = " + s);
        model = new GLM( params).trainModel().get();
        HashMap<String, Double> coefs = model.coefficients();
        System.out.println("coefs = " + coefs.toString());
        System.out.println("metrics = " + model._output._training_metrics);
        boolean CD = (s == Solver.COORDINATE_DESCENT || s == Solver.COORDINATE_DESCENT_NAIVE);
        for (int i = 0; i < cfs1.length; ++i)
          assertEquals(vals[i], coefs.get(cfs1[i]), CD? 1e-1:1e-4);
        assertEquals(402,   GLMTest.nullDeviance(model), 1e-1);
        assertEquals(302.9, GLMTest.residualDeviance(model), 1e-1);
        assertEquals(290,   GLMTest.nullDOF(model), 0);
        assertEquals(282,   GLMTest.resDOF(model), 0);
        assertEquals(318.9, GLMTest.aic(model), 1e-1);
        System.out.println("VAL METRICS: " + model._output._validation_metrics);
        // compare validation res dev matches R
        // sum(binomial()$dev.resids(y=test$CAPSULE,mu=p,wt=1))
        // [1]80.92923
        assertTrue(80.92923 >= GLMTest.residualDevianceTest(model) - 1e-2);
//      compare validation null dev against R
//      sum(binomial()$dev.resids(y=test$CAPSULE,mu=.5,wt=1))
//      [1] 124.7665
        assertEquals(124.7665, GLMTest.nullDevianceTest(model),1e-4);
        model.delete();
        // test scoring
        scoreTrain = model.score(_prostateTrain);
        hex.ModelMetricsBinomial mm = hex.ModelMetricsBinomial.getFromDKV(model, _prostateTrain);
        hex.AUC2 adata = mm._auc;
        assertEquals(model._output._training_metrics.auc_obj()._auc, adata._auc, 1e-8);
        assertEquals(model._output._training_metrics._MSE, mm._MSE, 1e-8);
        assertEquals(((ModelMetricsBinomialGLM) model._output._training_metrics)._resDev, ((ModelMetricsBinomialGLM) mm)._resDev, 1e-8);
        scoreTest = model.score(_prostateTest);
        mm = hex.ModelMetricsBinomial.getFromDKV(model, _prostateTest);
        adata = mm._auc;
        assertEquals(model._output._validation_metrics.auc_obj()._auc, adata._auc, 1e-8);
        assertEquals(model._output._validation_metrics._MSE, mm._MSE, 1e-8);
        assertEquals(((ModelMetricsBinomialGLM) model._output._validation_metrics)._resDev, ((ModelMetricsBinomialGLM) mm)._resDev, 1e-8);

      } finally {
        if (model != null) model.delete();
        if (scoreTrain != null) scoreTrain.delete();
        if(scoreTest != null) scoreTest.delete();
      }
    }
  }

  @Test
  public void testWeights() {
    System.out.println("got " + _prostateTrain.anyVec().nChunks() + " chunks");
    GLMModel model = null, modelUpsampled = null;

    // random observation weights, integers in 0 - 9 range
    double [] weights = new double[] {
      0, 6, 5, 4, 4, 8, 2, 4, 9, 5,
      2, 0, 0, 4, 0, 0, 6, 3, 6, 5,
      5, 5, 6, 0, 9, 9, 8, 6, 6, 5,
      6, 1, 0, 6, 8, 6, 9, 2, 8, 0,
      3, 0, 2, 3, 0, 2, 5, 0, 0, 3,
      7, 4, 8, 4, 1, 9, 3, 7, 1, 3,
      8, 6, 9, 5, 5, 1, 9, 5, 2, 1,
      0, 6, 4, 0, 5, 3, 1, 2, 4, 0,
      7, 9, 6, 8, 0, 2, 3, 7, 5, 8,
      3, 4, 7, 8, 1, 2, 5, 7, 3, 7,
      1, 1, 5, 7, 4, 9, 2, 6, 3, 5,
      4, 9, 8, 1, 8, 5, 3, 0, 4, 5,
      1, 2, 2, 7, 8, 3, 4, 9, 0, 1,
      3, 9, 8, 7, 0, 8, 2, 7, 1, 9,
      0, 7, 7, 5, 2, 9, 7, 6, 4, 3,
      4, 6, 9, 1, 5, 0, 7, 9, 4, 1,
      6, 8, 8, 5, 4, 2, 5, 9, 8, 1,
      9, 2, 9, 2, 3, 0, 6, 7, 3, 2,
      3, 0, 9, 5, 1, 8, 0, 2, 8, 6,
      9, 5, 1, 2, 3, 1, 3, 5, 0, 7,
      4, 0, 5, 5, 7, 9, 3, 0, 0, 0,
      1, 5, 3, 2, 8, 9, 9, 1, 6, 2,
      2, 0, 5, 5, 6, 2, 8, 8, 9, 8,
      5, 0, 1, 5, 3, 0, 2, 5, 4, 0,
      6, 5, 4, 5, 9, 7, 5, 6, 2, 2,
      6, 2, 5, 1, 5, 9, 0, 3, 0, 2,
      7, 0, 4, 7, 7, 9, 3, 7, 9, 7,
      9, 6, 2, 6, 2, 2, 9, 0, 9, 8,
      1, 2, 6, 3, 4, 1, 2, 2, 3, 0
    };
    //double [] weights = new double[290];
    //Arrays.fill(weights, 1);

    Vec offsetVecTrain = _prostateTrain.anyVec().makeZero();
    try( Vec.Writer vw = offsetVecTrain.open() ) {
      for (int i = 0; i < weights.length; ++i)
        vw.set(i, weights[i]);
    }

//    Vec offsetVecTest = _prostateTest.anyVec().makeZero();
//    vw = offsetVecTest.open();
//    for(int i = 0; i < weights.length; ++i)
//      vw.set(i,weights[i]);
//    vw.close();
    Key fKeyTrain = Key.make("prostate_with_weights_train");
//    Key fKeyTest  = Key.make("prostate_with_offset_test");
    Frame fTrain = new Frame(fKeyTrain, new String[]{"weights"}, new Vec[]{offsetVecTrain});
    fTrain.add(_prostateTrain.names(), _prostateTrain.vecs());
    DKV.put(fKeyTrain,fTrain);
//    Frame fTest = new Frame(fKeyTest, new String[]{"offset"}, new Vec[]{offsetVecTest});
//    fTest.add(_prostateTest.names(),_prostateTest.vecs());
//    DKV.put(fKeyTest,fTest);
//    Call:  glm(formula = CAPSULE ~ . - ID, family = binomial, data = train,
//      weights = w)
//
//    Coefficients:
//    (Intercept)          AGE       RACER2       RACER3       DPROSb       DPROSc
//    -6.019527    -0.027350    -0.424333    -0.869188     1.359856     1.745655
//    DPROSd       DCAPSb          PSA          VOL      GLEASON
//    1.517155     0.664479     0.034541    -0.005819     0.947644
//
//    Degrees of Freedom: 251 Total (i.e. Null);  241 Residual
//    Null Deviance:	    1673
//    Residual Deviance: 1195 	AIC: 1217
    String [] cfs1 = new String [] { "Intercept",  "AGE",     "RACE.R2",  "RACE.R3", "DPROS.b", "DPROS.c", "DPROS.d", "DCAPS.b", "PSA",     "VOL",    "GLEASON"};
    double [] vals = new double [] { -6.019527,    -0.027350, -0.424333, -0.869188, 1.359856, 1.745655, 1.517155, 0.664479, 0.034541, -0.005819, 0.947644};
    GLMParameters params = new GLMParameters(Family.binomial);
    params._response_column = "CAPSULE";
    params._ignored_columns = new String[]{"ID"};
    params._train = fKeyTrain;
//    params._valid = fKeyTest;
    params._weights_column = "weights";
    params._lambda = new double[]{0};
    params._alpha = new double[]{0};
    //params._standardize = false;
    params._objective_epsilon = 0;
    params._gradient_epsilon = 1e-6;
    params._beta_epsilon = 1e-6;
    params._max_iterations = 1000; // not expected to reach max iterations here
    params._missing_values_handling = MissingValuesHandling.Skip;
    try {
      for (Solver s : new Solver[]{Solver.AUTO, Solver.IRLSM, Solver.L_BFGS, Solver.COORDINATE_DESCENT}) {
        Frame scoreTrain = null, scoreTest = null;
        try {
          params._solver = s;
          params._train = fKeyTrain;
          params._weights_column = "weights";
          params._gradient_epsilon = 1e-8;
          params._objective_epsilon = 0;
          params._missing_values_handling = MissingValuesHandling.Skip;
          System.out.println("SOLVER = " + s);
          model = new GLM(params).trainModel().get();
          params = (GLMParameters) params.clone();
          params._train = _prostateTrainUpsampled._key;
          params._weights_column = null;
          modelUpsampled = new GLM(params).trainModel().get();
          HashMap<String, Double> coefs = model.coefficients();
          HashMap<String, Double> coefsUpsampled = modelUpsampled.coefficients();
          System.out.println("coefs = " + coefs);
          System.out.println("coefs upsampled = " + coefsUpsampled);
          System.out.println(model._output._training_metrics);
          System.out.println(modelUpsampled._output._training_metrics);
          boolean CD = (s == Solver.COORDINATE_DESCENT || s == Solver.COORDINATE_DESCENT_NAIVE);
          for (int i = 0; i < cfs1.length; ++i) {
            System.out.println("cfs = " + cfs1[i] + ": " + coefsUpsampled.get(cfs1[i]) + " =?= " + coefs.get(cfs1[i]));
            assertEquals(coefsUpsampled.get(cfs1[i]), coefs.get(cfs1[i]), s == Solver.IRLSM?1e-5:1e-4);
            assertEquals(vals[i], coefs.get(cfs1[i]), CD?1e-2:1e-4);//dec
          }
          assertEquals(GLMTest.auc(modelUpsampled),GLMTest.auc(model),1e-4);
          assertEquals(GLMTest.logloss(modelUpsampled),GLMTest.logloss(model),1e-4);
          assertEquals(GLMTest.mse(modelUpsampled),GLMTest.mse(model),1e-4);
          assertEquals(1673, GLMTest.nullDeviance(model),1);
          assertEquals(1195, GLMTest.residualDeviance(model),1);
          assertEquals(251,   GLMTest.nullDOF(model), 0);
          assertEquals(241,   GLMTest.resDOF(model), 0);
          assertEquals(1217, GLMTest.aic(model), 1);
          // mse computed in R on upsampled data
          assertEquals(0.1604573,model._output._training_metrics._MSE,1e-5);
          // auc computed in R on explicitly upsampled data
          assertEquals(0.8348088,GLMTest.auc(model),1e-4);
//          assertEquals(76.8525, GLMTest.residualDevianceTest(model),1e-4);
          // test scoring
//          try { // NO LONGER check that we get IAE if computing metrics on data with no weights (but trained with weights)
            scoreTrain = model.score(_prostateTrain);
            scoreTrain.delete();
//            assertTrue("shoul've thrown IAE", false); //TN-1 now autofills with weights 1
//          } catch (IllegalArgumentException iae) {
//            assertTrue(iae.getMessage().contains("Test/Validation dataset is missing weights column"));
//          }
          Frame f = new Frame(_prostateTrain);
          f.remove("CAPSULE");
          // test we can generate predictions with no weights (no metrics)
          scoreTrain = model.score(f);
          scoreTrain.delete();
          hex.ModelMetricsBinomialGLM mmTrain = (ModelMetricsBinomialGLM)hex.ModelMetricsBinomial.getFromDKV(model, fTrain);
          hex.AUC2 adata = mmTrain._auc;
          assertEquals(model._output._training_metrics.auc_obj()._auc, adata._auc, 1e-8);
          assertEquals(model._output._training_metrics._MSE, mmTrain._MSE, 1e-8);
          assertEquals(((ModelMetricsBinomialGLM) model._output._training_metrics)._resDev, mmTrain._resDev, 1e-8);
          scoreTrain = model.score(fTrain);
          mmTrain = (ModelMetricsBinomialGLM)hex.ModelMetricsBinomial.getFromDKV(model, fTrain);
          adata = mmTrain._auc;
          assertEquals(model._output._training_metrics.auc_obj()._auc, adata._auc, 1e-8);
          assertEquals(model._output._training_metrics._MSE, mmTrain._MSE, 1e-8);
          assertEquals(((ModelMetricsBinomialGLM) model._output._training_metrics)._resDev, mmTrain._resDev, 1e-8);

          // test we got auc

//          scoreTest = model.score(fTest);
//          ModelMetricsBinomialGLM mmTest = (ModelMetricsBinomialGLM)hex.ModelMetricsBinomial.getFromDKV(model, fTest);
//          adata = mmTest._auc;
//          assertEquals(model._output._validation_metrics.auc()._auc, adata._auc, 1e-8);
//          assertEquals(model._output._validation_metrics._MSE, mmTest._MSE, 1e-8);
//          assertEquals(((ModelMetricsBinomialGLM) model._output._validation_metrics)._resDev, mmTest._resDev, 1e-8);
//          // test the actual predictions
//          Vec preds = scoreTest.vec("p1");
//          for(int i = 0; i < pred_test.length; ++i)
//            assertEquals(pred_test[i],preds.at(i),1e-6);
        } finally {
          if (model != null) model.delete();
          if (modelUpsampled != null) modelUpsampled.delete();
          if (scoreTrain != null)
            scoreTrain.delete();
          if (scoreTest != null)
            scoreTest.delete();
        }
      }
    } finally {
      if (fTrain != null) {
        fTrain.remove("weights").remove();
        DKV.remove(fTrain._key);
      }
//      if(fTest != null)fTest.delete();
    }
  }

  @Test
  public void testNonNegative() {
    GLMModel model = null;
//   glmnet result
//    (Intercept)         AGE      RACER1      RACER2      RACER3      DPROSb
//    -7.85142421  0.00000000  0.76094020  0.87641840  0.00000000  0.93030614
//    DPROSc      DPROSd      DCAPSb         PSA         VOL     GLEASON
//    1.31814009  0.82918839  0.63285077  0.02949062  0.00000000  0.83011321
    String [] cfs1 = new String [] {"Intercept", "AGE", "DPROS.b",    "DPROS.c",     "DPROS.d",  "DCAPS.b",  "PSA",      "VOL", "GLEASON"};
    double [] vals = new double [] {-7.85142421,   0.0,    0.93030614,   1.31814009,    0.82918839, 0.63285077, 0.02949062, 0.0,    0.83011321};
    GLMParameters params = new GLMParameters(Family.binomial);
    params._response_column = "CAPSULE";
    params._ignored_columns = new String[]{"ID",};
    params._train = _prostateTrain._key;
    params._lambda = new double[]{0};
    params._alpha = new double[]{0};
    params._standardize = false;
    params._non_negative = true;
    params._intercept = true;
    params._objective_epsilon = 1e-10;
    params._gradient_epsilon = 1e-6;
    params._max_iterations = 10000; // not expected to reach max iterations here
    for(Solver s:new Solver[]{Solver.IRLSM,Solver.L_BFGS, Solver.COORDINATE_DESCENT}) {
      Frame scoreTrain = null, scoreTest = null;
      try {
        params._solver = s;
        System.out.println("SOLVER = " + s);
        model = new GLM(params).trainModel().get();
        HashMap<String, Double> coefs = model.coefficients();
        System.out.println("coefs = " + coefs.toString());
        System.out.println("metrics = " + model._output._training_metrics);
//        for (int i = 0; i < cfs1.length; ++i)
//          assertEquals(vals[i], coefs.get(cfs1[i]), Math.abs(5e-1 * vals[i]));
        assertEquals(390.3468,   GLMTest.nullDeviance(model), 1e-4);
        assertEquals(300.7231, GLMTest.residualDeviance(model), 3);
        System.out.println("VAL METRICS: " + model._output._validation_metrics);
        model.delete();
        // test scoring
        scoreTrain = model.score(_prostateTrain);
        hex.ModelMetricsBinomial mm = hex.ModelMetricsBinomial.getFromDKV(model, _prostateTrain);
        hex.AUC2 adata = mm._auc;
        assertEquals(model._output._training_metrics.auc_obj()._auc, adata._auc, 1e-8);
        assertEquals(model._output._training_metrics._MSE, mm._MSE, 1e-8);
        assertEquals(((ModelMetricsBinomialGLM) model._output._training_metrics)._resDev, ((ModelMetricsBinomialGLM) mm)._resDev, 1e-8);
      } finally {
        if (model != null) model.delete();
        if (scoreTrain != null) scoreTrain.delete();
        if(scoreTest != null) scoreTest.delete();
      }
    }
  }

  @Test
  public void testNonNegativeNoIntercept() {
    Scope.enter();
    GLMModel model = null;
//   glmnet result
//    (Intercept)         AGE      RACER1      RACER2      RACER3      DPROSb
//    0.000000000 0.000000000 0.240953925 0.000000000 0.000000000 0.000000000
//    DPROSc      DPROSd      DCAPSb         PSA         VOL     GLEASON
//    0.000000000 0.000000000 0.680406869 0.007137494 0.000000000 0.000000000
    String [] cfs1 = new String [] {"Intercept", "AGE", "DPROS.b",    "DPROS.c",     "DPROS.d",  "DCAPS.b",   "PSA",      "VOL", "GLEASON", "RACE.R1"};
    double [] vals = new double [] { 0.0,         0.0,   0.0,          0,             0.0,        0.680406869, 0.007137494, 0.0,  0.0,       0.240953925};
    GLMParameters params = new GLMParameters(Family.binomial);
    params._response_column = "CAPSULE";
    params._ignored_columns = new String[]{"ID",};
    params._train = _prostateTrain._key;
    params._lambda = new double[]{0};
    params._alpha = new double[]{0};
    params._standardize = false;
    params._non_negative = true;
    params._intercept = false;
    params._objective_epsilon = 1e-6;
    params._gradient_epsilon = 1e-5;
    params._max_iterations = 150; // not expected to reach max iterations here
    for(Solver s:new Solver[]{Solver.AUTO,Solver.IRLSM,Solver.L_BFGS, Solver.COORDINATE_DESCENT}) {
      Frame scoreTrain = null, scoreTest = null;
      try {
        params._solver = s;
        params._max_iterations = 500;
        System.out.println("SOLVER = " + s);
        model = new GLM(params).trainModel().get();
        HashMap<String, Double> coefs = model.coefficients();
        System.out.println("coefs = " + coefs.toString());
        System.out.println("metrics = " + model._output._training_metrics);
        double relTol = s == Solver.IRLSM?1e-1:1;
        for (int i = 0; i < cfs1.length; ++i)
          assertEquals(vals[i], coefs.get(cfs1[i]), relTol * (vals[i] + 1e-1));
        assertEquals(402.0254,   GLMTest.nullDeviance(model), 1e-1);
        assertEquals(394.3998, GLMTest.residualDeviance(model), s == Solver.L_BFGS?50:1);
        System.out.println("VAL METRICS: " + model._output._validation_metrics);
        model.delete();
        // test scoring
        scoreTrain = model.score(_prostateTrain);
        hex.ModelMetricsBinomial mm = hex.ModelMetricsBinomial.getFromDKV(model, _prostateTrain);
        hex.AUC2 adata = mm._auc;
        assertEquals(model._output._training_metrics.auc_obj()._auc, adata._auc, 1e-8);
        assertEquals(model._output._training_metrics._MSE, mm._MSE, 1e-8);
        assertEquals(((ModelMetricsBinomialGLM) model._output._training_metrics)._resDev, ((ModelMetricsBinomialGLM) mm)._resDev, 1e-8);
      } finally {
        if (model != null) model.delete();
        if (scoreTrain != null) scoreTrain.delete();
        if(scoreTest != null) scoreTest.delete();
      }
    }
    Scope.exit();
  }

  @Test
  public void testNoInterceptWithOffsetAndWeights() {
    Scope.enter();
    GLMModel model = null;
    double [] offset_train = new double[] {
      -0.39771185,+1.20479170,-0.16374109,-0.97885903,-1.42996530,+0.83474893,+0.83474893,-0.74488827,+0.83474893,+0.86851236,
      +1.41589611,+1.41589611,-1.42996530,-0.39771185,-2.01111248,-0.39771185,-0.16374109,+0.62364452,-0.39771185,+0.60262749,
      -0.06143251,-1.42996530,-0.06143251,-0.06143251,+0.14967191,-0.06143251,-0.39771185,+0.14967191,+1.20479170,-0.39771185,
      -0.16374109,-0.06143251,-0.06143251,-1.42996530,-0.39771185,-0.39771185,-0.64257969,+1.65774729,-0.97885903,-0.39771185,
      -0.39771185,-0.39771185,-1.42996530,+1.41589611,-0.06143251,-0.06143251,-0.39771185,-0.06143251,-0.06143251,-0.39771185,
      -0.06143251,+0.14967191,-0.39771185,-1.42996530,-0.39771185,-0.64257969,-0.39771185,-0.06143251,-0.06143251,-0.06143251,
      -1.42996530,-2.01111248,-0.06143251,-0.39771185,-0.39771185,-1.42996530,-0.39771185,-1.42996530,-0.06143251,+1.41589611,
      +0.14967191,-1.42996530,-1.42996530,-0.06143251,-1.42996530,-1.42996530,-0.06143251,-1.42996530,-0.06143251,-0.39771185,
      -0.06143251,-1.42996530,-0.06143251,-0.39771185,-1.42996530,-0.06143251,-0.06143251,-0.06143251,-1.42996530,-0.39771185,
      -1.42996530,-0.43147527,-0.39771185,-0.39771185,-0.39771185,-1.42996530,-1.42996530,-0.43147527,-0.39771185,-0.39771185,
      -0.39771185,-0.39771185,-1.42996530,-1.42996530,-1.42996530,-0.39771185,+0.14967191,+1.41589611,-1.42996530,+1.41589611,
      -1.42996530,+1.41589611,-0.06143251,+0.14967191,-0.39771185,-0.97885903,-1.42996530,-0.39771185,-0.39771185,-0.39771185,
      -0.39771185,-1.42996530,-0.39771185,-0.97885903,-0.06143251,-0.06143251,+0.86851236,-0.39771185,-0.39771185,-0.06143251,
      -0.39771185,-0.39771185,-0.06143251,+0.14967191,-1.42996530,-1.42996530,-0.39771185,+1.20479170,-1.42996530,-0.39771185,
      -0.06143251,-1.42996530,-0.97885903,+0.14967191,+0.14967191,-1.42996530,-1.42996530,-0.39771185,-0.06143251,-0.43147527,
      -0.06143251,-0.39771185,-1.42996530,-0.06143251,-0.39771185,-0.39771185,-1.42996530,-0.39771185,-0.39771185,-0.06143251,
      -0.39771185,-0.39771185,+0.14967191,-0.06143251,+1.41589611,-0.06143251,-0.39771185,-0.39771185,-0.06143251,-1.42996530,
      -0.06143251,-1.42996530,-0.39771185,-0.64257969,-0.06143251,+1.20479170,-0.43147527,-0.97885903,-0.39771185,-0.39771185,
      -0.39771185,+0.14967191,-2.01111248,-1.42996530,-0.06143251,+0.83474893,-1.42996530,-1.42996530,-2.01111248,-1.42996530,
      -0.06143251,+0.86851236,+0.05524374,-0.39771185,-0.39771185,-0.39771185,+1.41589611,-1.42996530,-0.39771185,-1.42996530,
      -0.39771185,-0.39771185,-0.06143251,+0.14967191,-1.42996530,-0.39771185,-1.42996530,-1.42996530,-0.39771185,-0.39771185,
      -0.06143251,-1.42996530,-0.97885903,-1.42996530,-0.39771185,-0.06143251,-0.39771185,-0.06143251,-1.42996530,-1.42996530,
      -0.06143251,-1.42996530,-0.39771185,+0.14967191,-0.06143251,-1.42996530,-1.42996530,+0.14967191,-0.39771185,-0.39771185,
      -1.42996530,-0.06143251,-0.06143251,-1.42996530,-0.06143251,-1.42996530,+0.14967191,+1.20479170,-1.42996530,-0.06143251,
      -0.39771185,-0.39771185,-0.06143251,+0.14967191,-0.06143251,-1.42996530,-1.42996530,-1.42996530,-0.39771185,-0.39771185,
      -0.39771185,+0.86851236,-0.06143251,-0.97885903,-0.06143251,-0.64257969,+0.14967191,+0.86851236,-0.39771185,-0.39771185,
      -0.39771185,-0.64257969,-1.42996530,-0.06143251,-0.39771185,-0.39771185,-1.42996530,-1.42996530,-0.06143251,+0.14967191,
      -0.06143251,+0.86851236,-0.97885903,-1.42996530,-1.42996530,-1.42996530,-1.42996530,+0.86851236,+0.14967191,-1.42996530,
      -0.97885903,-1.42996530,-1.42996530,-0.06143251,+0.14967191,-1.42996530,-0.64257969,-2.01111248,-0.97885903,-0.39771185
    };

    double [] offset_test = new double [] {
      +1.65774729,-0.97700971,-0.97700971,-0.97700971,+0.05524374,+0.05524374,+0.05524374,+0.05524374,+0.39152308,+0.39152308,
      +0.39152308,+0.05524374,+0.05524374,+0.05524374,+0.39152308,-0.97700971,+0.05524374,+1.32146795,+0.39152308,+1.65774729,
      -0.97700971,+1.65774729,+0.39152308,+0.39152308,+1.65774729,+0.60262749,+0.05524374,+0.05524374,+0.05524374,+0.60262749,
      +0.05524374,-0.97700971,-0.97885903,+0.05524374,-2.01111248,-0.97700971,+0.05524374,+0.39152308,+0.05524374,+0.60262749,
      +0.60262749,+0.39152308,+0.60262749,-0.97700971,+0.39152308,+1.65774729,+0.39152308,+0.39152308,+0.05524374,+1.86885170,
      +0.05524374,-0.97700971,+0.60262749,-0.97700971,+0.60262749,-0.97700971,+0.39152308,-0.97700971,-0.43147527,+1.32146795,
      +0.05524374,+0.05524374,+0.39152308,+0.39152308,+0.05524374,+0.39152308,-0.97700971,+0.05524374,+0.39152308,+0.05524374,
      +0.60262749,+1.86885170,+0.05524374,+0.05524374,+1.86885170,+0.60262749,-0.64257969,-0.97700971,+0.60262749,+0.39152308,
      -0.97700971,-0.97700971,+0.05524374,-0.97700971,-0.97700971,+0.05524374,+0.05524374,+0.60262749,+0.05524374,+0.05524374
    };

    // random observation weights, integers in 0 - 9 range
    double [] weights_train = new double[] {
      0, 6, 5, 4, 4, 8, 2, 4, 9, 5,
      2, 0, 0, 4, 0, 0, 6, 3, 6, 5,
      5, 5, 6, 0, 9, 9, 8, 6, 6, 5,
      6, 1, 0, 6, 8, 6, 9, 2, 8, 0,
      3, 0, 2, 3, 0, 2, 5, 0, 0, 3,
      7, 4, 8, 4, 1, 9, 3, 7, 1, 3,
      8, 6, 9, 5, 5, 1, 9, 5, 2, 1,
      0, 6, 4, 0, 5, 3, 1, 2, 4, 0,
      7, 9, 6, 8, 0, 2, 3, 7, 5, 8,
      3, 4, 7, 8, 1, 2, 5, 7, 3, 7,
      1, 1, 5, 7, 4, 9, 2, 6, 3, 5,
      4, 9, 8, 1, 8, 5, 3, 0, 4, 5,
      1, 2, 2, 7, 8, 3, 4, 9, 0, 1,
      3, 9, 8, 7, 0, 8, 2, 7, 1, 9,
      0, 7, 7, 5, 2, 9, 7, 6, 4, 3,
      4, 6, 9, 1, 5, 0, 7, 9, 4, 1,
      6, 8, 8, 5, 4, 2, 5, 9, 8, 1,
      9, 2, 9, 2, 3, 0, 6, 7, 3, 2,
      3, 0, 9, 5, 1, 8, 0, 2, 8, 6,
      9, 5, 1, 2, 3, 1, 3, 5, 0, 7,
      4, 0, 5, 5, 7, 9, 3, 0, 0, 0,
      1, 5, 3, 2, 8, 9, 9, 1, 6, 2,
      2, 0, 5, 5, 6, 2, 8, 8, 9, 8,
      5, 0, 1, 5, 3, 0, 2, 5, 4, 0,
      6, 5, 4, 5, 9, 7, 5, 6, 2, 2,
      6, 2, 5, 1, 5, 9, 0, 3, 0, 2,
      7, 0, 4, 7, 7, 9, 3, 7, 9, 7,
      9, 6, 2, 6, 2, 2, 9, 0, 9, 8,
      1, 2, 6, 3, 4, 1, 2, 2, 3, 0
    };



    Vec offsetVecTrain = _prostateTrain.anyVec().makeZero();
    try( Vec.Writer vw = offsetVecTrain.open() ) {
      for (int i = 0; i < offset_train.length; ++i)
        vw.set(i, offset_train[i]);
    }

    Vec weightsVecTrain = _prostateTrain.anyVec().makeZero();
    try( Vec.Writer vw = weightsVecTrain.open() ) {
      for (int i = 0; i < weights_train.length; ++i)
        vw.set(i, weights_train[i]);
    }

    Vec offsetVecTest = _prostateTest.anyVec().makeZero();
    try( Vec.Writer vw = offsetVecTest.open() ) {
      for (int i = 0; i < offset_test.length; ++i)
        vw.set(i, offset_test[i]);
    }

    Frame fTrain = new Frame(Key.<Frame>make("prostate_with_offset_train"), new String[]{"offset","weights"}, new Vec[]{offsetVecTrain, weightsVecTrain});
    fTrain.add(_prostateTrain.names(), _prostateTrain.vecs());
    DKV.put(fTrain);
    Frame fTest = new Frame(Key.<Frame>make("prostate_with_offset_test"), new String[]{"offset"}, new Vec[]{offsetVecTest});
    fTest.add(_prostateTest.names(),_prostateTest.vecs());
    DKV.put(fTest);
//    Call:  glm(formula = CAPSULE ~ . - ID - RACE - DCAPS - DPROS - 1, family = binomial,
//      data = train, weights = w, offset = offset_train)
//
//    Coefficients:
//    AGE       PSA        VOL        GLEASON
//   -0.070637  0.034939  -0.006326   0.645700
//
//    Degrees of Freedom: 252 Total (i.e. Null);  248 Residual
//    Null Deviance:	    1494
//    Residual Deviance: 1235 	AIC: 1243
    String [] cfs1 = new String [] { "Intercept",  "AGE"   ,  "PSA",     "VOL",    "GLEASON"};
    double [] vals = new double [] {  0,           -0.070637,   0.034939, -0.006326, 0.645700};
    GLMParameters params = new GLMParameters(Family.binomial);
    params._response_column = "CAPSULE";
    params._ignored_columns = new String[]{"ID","RACE","DPROS","DCAPS"};
    params._train = fTrain._key;
    params._offset_column = "offset";
    params._weights_column = "weights";
    params._lambda = new double[]{0};
    params._alpha = new double[]{0};
    params._standardize = false;
    params._objective_epsilon = 0;
    params._gradient_epsilon = 1e-6;
    params._max_iterations = 100; // not expected to reach max iterations here
    params._intercept = false;
    params._beta_epsilon = 1e-6;
    try {
      for (Solver s : new Solver[]{ Solver.IRLSM, Solver.L_BFGS, Solver.COORDINATE_DESCENT}) {
        Frame scoreTrain = null, scoreTest = null;
        try {
          params._solver = s;
          params._valid = fTest._key;
          System.out.println("SOLVER = " + s);
          try {
            model = new GLM(params,Key.<GLMModel>make("prostate_model")).trainModel().get();
          } catch(Exception iae) {
            assertTrue(iae.getMessage().contains("Test/Validation dataset is missing weights column"));
          }
          params._valid = null;
          model = new GLM(params,Key.<GLMModel>make("prostate_model")).trainModel().get();
          HashMap<String, Double> coefs = model.coefficients();
          System.out.println("coefs = " + coefs);
          boolean CD = s == Solver.COORDINATE_DESCENT;
          for (int i = 0; i < cfs1.length; ++i)
            assertEquals(vals[i], coefs.get(cfs1[i]), CD?1e-2:1e-4);
          assertEquals(1494, GLMTest.nullDeviance(model), 1);
          assertEquals(1235, GLMTest.residualDeviance(model), 1);
          assertEquals( 252, GLMTest.nullDOF(model), 0);
          assertEquals( 248, GLMTest.resDOF(model), 0);
          assertEquals(1243, GLMTest.aic(model), 1);
//          assertEquals(88.72363, GLMTest.residualDevianceTest(model),1e-4);
          // test scoring
          try {
            scoreTrain = model.score(_prostateTrain);
            assertTrue("shoul've thrown IAE", false);
          } catch (IllegalArgumentException iae) {
            assertTrue(iae.getMessage().contains("Test/Validation dataset is missing"));
          }
          hex.ModelMetricsBinomialGLM mmTrain = (ModelMetricsBinomialGLM)hex.ModelMetricsBinomial.getFromDKV(model, fTrain);
          hex.AUC2 adata = mmTrain._auc;
          assertEquals(model._output._training_metrics.auc_obj()._auc, adata._auc, 1e-8);
          assertEquals(model._output._training_metrics._MSE, mmTrain._MSE, 1e-8);
          assertEquals(((ModelMetricsBinomialGLM) model._output._training_metrics)._resDev, mmTrain._resDev, 1e-8);
          scoreTrain = model.score(fTrain);
          mmTrain = (ModelMetricsBinomialGLM)hex.ModelMetricsBinomial.getFromDKV(model, fTrain);
          adata = mmTrain._auc;
          assertEquals(model._output._training_metrics.auc_obj()._auc, adata._auc, 1e-8);
          assertEquals(model._output._training_metrics._MSE, mmTrain._MSE, 1e-8);
          assertEquals(((ModelMetricsBinomialGLM) model._output._training_metrics)._resDev, mmTrain._resDev, 1e-8);
//          scoreTest = model.score(fTest);
//          ModelMetricsBinomialGLM mmTest = (ModelMetricsBinomialGLM)hex.ModelMetricsBinomial.getFromDKV(model, fTest);
//          adata = mmTest._auc;
//          assertEquals(model._output._validation_metrics.auc()._auc, adata._auc, 1e-8);
//          assertEquals(model._output._validation_metrics._MSE, mmTest._MSE, 1e-8);
//          assertEquals(((ModelMetricsBinomialGLM) model._output._validation_metrics)._resDev, mmTest._resDev, 1e-8);
//          // test the actual predictions
//          Vec preds = scoreTest.vec("p1");
//          for(int i = 0; i < pred_test.length; ++i)
//            assertEquals(pred_test[i],preds.at(i),1e-6);
        } finally {
          if (model != null) model.delete();
          if (scoreTrain != null) scoreTrain.delete();
          if (scoreTest != null) scoreTest.delete();
        }
      }
    } finally {
      DKV.remove(fTrain._key);
      DKV.remove(fTest._key);
      Scope.exit();
    }
  }

  public double [] se_fit_train = new double []{
      0.401172291967046,0.58144796163594,0.636009788971365,0.672935448821698,0.547115090148835,0.665235162681877,0.758241306730845,0.747735508085657,0.751796285902532,0.565378030241718,0.685744102370689,0.621306180377945,0.519944507377875,0.478997472629325,0.731278678208638,0.449090108704256,0.718033414313542,0.698741524598025,0.429905574204284,1.60570239174736,0.394802848163408,0.491970512949845,0.464908726104024,0.478986096595297,0.604861976249831,0.467257404821037,0.352578173424571,0.602418758564396,0.57277376778954,0.338854288935119,0.646564894816616,0.386039733281778,0.350790141183537,0.430121413847699,0.359746329186684,0.35575658873344,0.887915851268042,1.64451300978832,0.67157307143807,0.390323761073687,0.426463915811637,0.327109422383669,0.427756361288921,1.23411221201443,0.720266178239612,0.339175045268225,0.363058235511056,0.375386666281703,0.418712997109406,0.358260047148349,0.367026049164365,0.540545845027592,0.321695851968408,0.455649993832583,0.413908494871437,0.567204076847851,0.339414752611787,0.429022767361229,0.388894756599385,0.419855084086064,0.394379571488335,0.774651521438499,0.357799085741704,0.348954111305691,0.445542019400163,0.606859104987369,0.409245626632267,0.391613846760249,0.399878275485108,0.782568242036015,0.672025431274866,0.424049727448432,0.455582621552197,0.350434682200152,0.439137464394051,0.379390067310232,0.346824606973329,0.426312194446591,0.329274027292276,0.314360003521232,0.371035930580412,0.478367940997782,0.78195408641842,0.306126907784262,0.395150321629854,0.460916850941018,0.31543461045107,0.340167842826682,0.404420614021142,0.590561505483495,0.742356987328527,0.656103926526512,0.39631762728127,0.417433365321149,0.468289042026264,0.420030528023189,0.536946854841718,0.596644268422341,0.521713575347405,0.275891726300053,0.290376530876315,0.357461112617305,0.364151346930392,0.455437101815304,0.44152679832506,0.293786118504326,0.890700927367574,0.965598547606649,0.373100285768398,0.605993355262921,0.409761599191233,0.666711732092828,0.357861414020638,0.588112507806648,0.300914744328366,0.61012311301179,0.343793786110471,0.373397030913476,0.375861834933083,0.751853715126842,0.352953332506167,0.506922444442139,0.320649472945001,0.512636843599827,0.386729035357537,0.404741978538747,0.558544039260578,0.328481949119046,0.500837401431124,0.341600367561439,0.294512712939018,0.281817575283794,0.281507668890845,0.469503316219638,0.383272112829449,0.37949831238762,0.389090073273342,0.5578131594145,0.540404681465307,0.309531451605753,0.346645177884045,0.82792805638947,0.571734166001559,0.482781566066818,0.68008470193632,0.478879694535708,0.330786340179861,0.31989831661055,0.446476824935142,0.686409562836194,0.408470955313178,0.289128186454746,0.414093785786078,0.420974321471626,0.295611195368024,0.308728679018003,0.472361955737358,0.465268389481517,0.259180730826343,0.41853466714757,0.406200786717385,0.393767828765887,0.568628561138106,0.487492924182216,0.743385002522253,0.293986415874548,0.326438040400929,0.4166357112533,0.444014615288231,0.435014011439903,0.368381656498293,0.415998579817197,0.292608089969539,0.546616308863142,0.416869566088942,0.582235056000554,1.1669167799844,0.575335127227223,0.334493094296796,0.315723323715223,0.323587035691946,0.497949211004884,0.569711885327055,0.331206808359,0.35438491666341,0.729389639936745,0.370679483500856,0.41834360476124,0.829559311796719,0.42923993715121,0.456560868873474,0.570151115859735,1.58191837183884,0.368893949076196,0.413382510485755,0.499912564158049,0.621659330686545,0.362234370028613,0.354401714619481,0.366621020029018,0.290180587460237,0.340316168115292,0.328389375366545,0.550632093739221,0.44547101126416,0.55611864563979,0.392908873728696,0.350694514064866,0.343613567846189,0.378221578830133,0.363250245213107,1.17259229194953,0.606036097794831,0.440040566866805,0.320024776025891,0.481299117151961,0.503111737215212,0.427815012855048,0.409056510540935,0.408839118796801,0.36825670031528,0.419538427976962,0.304013289292125,0.497366087211293,0.338489624889337,0.480474921668507,0.444484067275093,0.75279101323611,0.326197032086786,0.421960697579323,0.357666530387697,0.432357191448588,0.356203234247134,0.678490024218461,0.468704723819834,0.493259735546079,0.583584270146121,0.603450604508522,0.459419158142152,0.466762627214132,0.62413009183061,0.319794490173933,0.350651436914855,0.525546612129896,0.380672868338716,0.646395279575299,0.442280913632121,0.466739925323689,0.406229485883553,0.322671853980011,0.493726839593069,0.595316062708849,0.394420703782317,0.5997057807165,0.442866891357951,0.593226846394696,0.522836586753639,0.815900755733015,0.370249135654411,0.681676455947968,0.357404370918772,0.582222229711622,0.445489497475194,0.349367661483792,0.794791208556869,0.443310093133298,0.409283808967084,0.482886808815759,0.369388424288114,0.580431361937116,0.44575964305696,0.615368297283715,0.904688298159245,0.458365757172298,0.476126626938535,0.435320871076045,0.408329696984203,0.829867293389758,0.553616129076451,0.474249582146565,0.611494392681435,0.506460185129955,0.504439489367965,0.510947847991332,1.39014165933331,0.515244657894588,0.601330107522651,0.658039014833446,0.645091294150955,0.372391817419807
  };
  public static double [] se_fit_test = new double []{
      0.617076483528978,0.446584717490494,0.722900644801647,0.526268661384577,0.369920845774394,0.347993872003076,0.475010210822161,0.413967917540706,0.402172058494061,0.408272439143105,0.373385965455959,0.866537257280919,0.354226879094779,0.441615044665604,0.412566062326444,0.413063527064438,0.379592452326668,0.963174232386477,0.324035264358772,0.687167233140264,0.447351843959086,0.668108423038829,0.445666516832302,0.321058535902121,0.771244503612031,0.519198260715954,0.42014552123981,0.429232931997947,0.388760111399676,0.554526999086624,0.280920517316341,0.368158322762131,0.635035270259654,0.458556036959436,0.602464142414664,0.348300943371193,0.347154037799401,0.321074548651878,0.339776183840657,0.569093128121953,0.512766409543062,0.372304026853488,0.523285080250071,0.377598874453372,0.456923705179863,0.602585087912435,0.315789763261603,0.323353602174183,0.284047116450782,0.675844122217226,0.274312876255341,0.441911403990289,0.634460804308812,0.411363977249681,0.51906453454866,0.37551092679958,0.389586718814279,0.392785924297656,0.644720141136172,0.701402286480617,0.407268497119452,0.304104675389161,0.317034927484701,0.416833490638618,0.437091272327493,0.323511539364444,0.381409052836863,0.321415993239253,0.448165563194177,0.287199027028912,0.585640079325181,0.71482751535041,0.458704587789413,0.311184069273789,1.38352841821879,0.542909307815898,0.618052908666333,0.422424552606973,0.510097610581744,0.414235606506409,0.438747086156365,0.406598185976392,0.347759978854103,1.15394102308943,0.391151856780008,0.428696468321115,0.643307187799271,0.565422551068395,0.411306202996684,0.652896340316824
  };

  public static double [] airlines_train_se_fit = new double[]{ // first 1000 values
      0.252836855149866,0.25063249212809,0.407664446166005,0.408246293438782,0.406347347804188,0.407711004874845,0.40618029076075,0.407648542748374,0.4065248023231,0.408336323674814,0.405699466165627,0.405625202423275,0.406662046740198,0.406135832508279,0.214872084345414,0.215158790537287,0.212493692150041,0.210238180821842,0.211883028919179,0.210764548292247,0.213884374350223,0.210145922514717,0.212723740189853,0.21312221073163,0.2102602668928,0.211318420592602,0.212241394766091,0.249081845993044,0.247191995365537,0.24755391665887,0.244013612264857,0.246053865749984,0.243180004064391,0.24298499935604,0.246638198067131,0.246431670491082,0.246465876021215,0.243757986412695,0.217983911618409,0.22222191787102,0.220495949072428,0.220309860330713,0.218988480815101,0.215756728158405,0.219296440777976,0.219068288010522,0.298872227406173,0.289416840053932,0.292277211901052,0.291372601500387,0.291853736669697,0.29000703022587,0.290740142757322,0.28997745310289,0.291443076970238,0.291332138781073,0.290510440233875,0.291494100109825,0.220071543300106,0.224871341477088,0.219926957166765,0.215598072718774,0.216462750713508,0.215721914466931,0.21900069296846,0.216184949813186,0.215337081692604,0.218897007876784,0.21861741175083,0.218491318709153,0.217947661883215,0.216332217517593,0.215619299110681,0.220152032593957,0.424485971112222,0.42339288962275,0.425816872275366,0.423468456436378,0.425644636861641,0.42476524646975,0.424678922965199,0.424753675320509,0.247930291334945,0.245395395368345,0.243333336982039,0.247092006820575,0.244733454957015,0.246805421876929,0.246105222578074,0.24699970831168,0.245990814268058,0.24756253003255,0.215406966117922,0.215815254822826,0.224865548238836,0.211834400953776,0.21215212332844,0.21350024945884,0.213991102426483,0.215705917872054,0.21083538985303,0.214635205786514,0.21253319311069,0.21432376695228,0.211282616589095,0.214614690263706,0.210776264658104,0.2134789222034,0.217035277081766,0.214377123789713,0.214303714351403,0.212466033640412,0.21429598502091,0.31420345590778,0.216303831472036,0.220415242737735,0.212910427241541,0.213107863775376,0.214358960966037,0.217392708424675,0.212244856382568,0.211778121923635,0.213851648229412,0.243169854907371,0.242534136179644,0.241626961039996,0.240558554618078,0.242820294999756,0.240730481351507,0.241236595911755,0.244807287994781,0.240516147642738,0.240812748552906,0.241790398225319,0.241431236005319,0.225260756644182,0.221711382809361,0.22615240437117,0.225327965436743,0.222907703226851,0.225406191348372,0.224665596462759,0.222609454437217,0.233662288452377,0.240115950220731,0.23342656462201,0.229345765016409,0.231747670534405,0.235019880955477,0.232182706004106,0.228834470110472,0.231378086434743,0.231430190441595,0.231377525692453,0.230318678229271,0.22535551469755,0.216156350349964,0.218543990027854,0.219399326186858,0.219484631845307,0.218983325797357,0.219285724291196,0.21889538311642,0.21761585070864,0.220678529229269,0.237323214268735,0.228528477684708,0.228009533369385,0.229544625807228,0.279006325055489,0.279297173509625,0.281985111532148,0.225447439592474,0.22519723432573,0.232799013568954,0.227952419606394,0.224032791261243,0.220214623814814,0.223210936569006,0.223680722492454,0.221131658325526,0.221852832064694,0.223767511994245,0.222485390946095,0.222449747963932,0.223536604498725,0.221014609867521,0.221991628299828,0.222916400721542,0.224076207353054,0.225982208826048,0.228169147034645,0.227720122963883,0.226301700255491,0.224777222198502,0.228608665730992,0.227274818055019,0.228677293776905,0.227812287809432,0.227509160937198,0.223277938419993,0.217602085274415,0.219729239090809,0.220791696747002,0.221700342830929,0.218320258035621,0.217140895388895,0.220497166532024,0.220334507548332,0.222759761989509,0.220467134777499,0.218676734357552,0.217158533052225,0.233164683620088,0.230896437183246,0.228936309307872,0.227690811753,0.229622089519075,0.229423442945757,0.228221064632657,0.22879527531812,0.227839873475224,0.228397682892968,0.266887743912111,0.227653789569272,0.223891695554912,0.219931986560629,0.224455661290849,0.222419935886907,0.223457465510358,0.223491800476545,0.22558570093879,0.222119286219873,0.223186692294023,0.220785980760182,0.220580478958176,0.221658471430396,0.24939700655634,0.24257135881783,0.243380715158984,0.242004878267779,0.242608868297679,0.242215780495671,0.242679448261661,0.243558654305434,0.242899738009567,0.268175998097788,0.265518594852272,0.267825890693032,0.264575636949929,0.263688818067163,0.264379130539238,0.265447901913334,0.266053259062922,0.263531162073933,0.265218488777402,0.266841162957281,0.268303253635884,0.23194197589398,0.229796747427464,0.228223756588518,0.227718263436938,0.22975843120457,0.226186668782314,0.227253473127995,0.243050916838092,0.24388353569744,0.244496376806219,0.242747625874843,0.244150695860641,0.245248697460434,0.242409243766908,0.243115379503236,0.225785893037053,0.225876554316007,0.233885605440896,0.22085324162018,0.224075227204229,0.223826826957779,0.224728944725359,0.222932321046448,0.223176231028914,0.223492606938558,0.221794207854819,0.222652942061682,0.222753040276166,0.225523637321688,0.229554500662397,0.226504488988229,0.228476321293586,0.228758246885461,0.269892442919258,0.277842810116924,0.273663870586668,0.269851525804809,0.267583659255192,0.267864790830762,0.267571386533176,0.269448263361642,0.268011565287993,0.266423228456438,0.266959166685826,0.268508665306949,0.266209399851754,0.296408370163883,0.297596897514198,0.302516332211327,0.297759782567093,0.296694195606902,0.294792624325604,0.294033472585072,0.293269437347874,0.295752400136564,0.296052527685326,0.294568817234323,0.294949684970147,0.294681431164689,0.293074138938228,0.293343255825171,0.293907334472225,0.296587036450288,0.288682485371292,0.288910645480627,0.286847358342865,0.288158302971732,0.286607189112262,0.285451526203493,0.28661663202136,0.288417371298692,0.286903922068349,0.286223398322503,0.339829907673281,0.267094418378546,0.271095880339432,0.26776042181323,0.265173286062297,0.265932279468467,0.265685224010878,0.264576081964139,0.265714745555294,0.26371691209677,0.266450755814679,0.265741787954538,0.265621953061293,0.263622229081543,0.267708193223549,0.298089078208481,0.297235351411314,0.296525950818272,0.298375810181686,0.297387904006348,0.298425846780207,0.297340995548547,0.294777299281385,0.297809156658201,0.295391148799411,0.298039876879247,0.289211229747179,0.28653855999481,0.289340664558082,0.287656954143827,0.286604752006648,0.28613381995317,0.286132635825687,0.288727807834836,0.287571987020553,0.287223699825293,0.288578984284512,0.295786667637545,0.294326116134553,0.295240751994009,0.293655931798356,0.293974150832726,0.292605436646477,0.295375576452448,0.293797802197972,0.292837220384339,0.293474247987652,0.298572754320998,0.298658437814054,0.295329235179264,0.293920618508837,0.296869507661787,0.297247844315971,0.295380082390438,0.294134931064908,0.29589750491615,0.29488129150967,0.288965370266403,0.288656835057334,0.2867509818162,0.288818243215582,0.288250850911898,0.287148811605447,0.287313242503681,0.287509277861718,0.289760905241739,0.274699335308655,0.276276269625586,0.270202279947145,0.272355353862719,0.270983226889986,0.272741266117723,0.274075952743692,0.27245104171318,0.272734679058811,0.272048512447762,0.272364691714129,0.273839040272257,0.267618906514444,0.268866652682324,0.268450699385525,0.26654314658792,0.26643056197393,0.266367730088294,0.269289045511512,0.267090665885446,0.264233901738947,0.266313654380704,0.352525894076757,0.356567135507243,0.348907469274512,0.351129321999398,0.348121212383438,0.349044591893735,0.352148690392893,0.350055773957492,0.350868653791385,0.350278383563207,0.349835635212799,0.348724154339809,0.261741882534336,0.353864501161493,0.35162239273588,0.351993184469189,0.351467680126408,0.351928823051335,0.352745971297917,0.352369914146226,0.349909635161898,0.291570961095553,0.292226606253504,0.292509813435244,0.293246747288254,0.293406124602378,0.291900252232729,0.459973290714359,0.453967186699175,0.455245333023587,0.454778490749485,0.454465552503195,0.454653090108045,0.454226624605076,0.452005676401795,0.454785268210438,0.453335213842621,0.455018810905687,0.455006732545262,0.210755113936743,0.211710123239876,0.206664796285258,0.205149761469548,0.20738880963114,0.205451719524422,0.205586723346876,0.206292365200077,0.206260458745336,0.205631072054663,0.205612467214972,0.335900174194469,0.334800224996877,0.336749077644519,0.334975954877548,0.33314083960708,0.334194143793037,0.336795473550969,0.336049364479788,0.334624049472826,0.340490404256528,0.346103803331212,0.340501991647257,0.33833557681315,0.338767329503395,0.339154943684327,0.339911223960947,0.339914242243732,0.337842127742028,0.33977584272058,0.341439210763326,0.340825069412074,0.339144360741147,0.338259865084728,0.341111735513369,0.334093816157235,0.332748399965866,0.340530084217254,0.33539003644057,0.333669569305244,0.331515283253603,0.333459404665695,0.330135661672927,0.332846333473134,0.333155752511525,0.334256376383757,0.240564209442758,0.242315715506163,0.238323614809348,0.236594513445051,0.237888507780335,0.239009151020142,0.237037642455849,0.239903552029665,0.235917766534814,0.22327029026111,0.219691802605601,0.222710596439029,0.220697521071132,0.219510944037693,0.222714630081853,0.217713949383692,0.222007853844753,0.222045225329489,0.219641872583197,0.220737841423739,0.219891204071821,0.221876537697852,0.251556213179956,0.250927106411343,0.252944163294682,0.250180189912811,0.248752783312165,0.249455001303504,0.248090226473697,0.249366520910524,0.247033440794353,0.248626210502519,0.249246809712584,0.24802978036722,0.249177065020523,0.248307049152255,0.248986677875334,0.247386480860031,0.248544608430331,0.25089906522958,0.303224197722329,0.300228548119376,0.300752921536206,0.299596320092415,0.30301258257452,0.302538159641274,0.302252442825603,0.300411734250391,0.302701525986983,0.300806232941442,0.235003271063762,0.235380542293827,0.221225408337258,0.229699020610332,0.216700928082911,0.219318207938191,0.218935415343421,0.216342755648256,0.219764893503115,0.218836894865457,0.219364705978763,0.217375190318963,0.217017648960758,0.219544863458477,0.219573390497247,1.06586389274517,1.06400125094115,1.06350815144394,1.06408648609153,1.06321112133738,1.0638695860698,1.06370066895473,1.06324766482422,1.06329477683642,1.06388081841874,0.303512845559252,0.302627318872004,0.299401545442844,0.299958133981856,0.300042002377439,0.300525160145439,0.302882524041507,0.299462631473541,0.300098829866947,0.300170768041135,0.286136063408419,0.283761744325219,0.286444779517782,0.284514334888082,0.285244982118071,0.285672637462178,0.282425347988095,0.283746467492284,0.28388469972609,0.283472145428885,0.285259860545229,0.222364597596743,0.220625007641835,0.220459046320099,0.219092597389006,0.219342609202526,0.218380913470928,0.220441982777469,0.217023773374482,0.216350932235226,0.219567941284702,0.216759902811339,0.2185677967255,0.215885298011674,0.218224041109289,0.216922513246931,0.216203321631368,882.74337515245,0.273415387671945,0.278798227586272,0.2759394125476,0.272380498478822,0.270015368485882,0.272774806379686,0.268712759394423,0.26931103773881,0.272999785087148,0.271582332355913,0.27184869321692,0.270165483451917,0.270571350526427,0.271616421165486,0.23858032699798,0.238812591821882,0.238503854260402,0.234258482946458,0.236772669365934,0.235989295655109,0.236627630172427,0.240049665776609,0.236284303280419,0.237195287822871,0.236540515617935,0.237974101266854,0.205142915905102,0.206926530601776,0.205937329345905,0.203707144103545,0.203571448402152,0.206141328328055,0.203480071452158,0.204194619631971,0.201760664713792,0.202699224669016,0.201923884519803,0.239049523300159,0.239553902211778,0.237583575231676,0.235494428406817,0.234848717376001,0.238979369699601,0.236622669473648,0.234658407487356,0.236630929384564,0.234094626855449,0.237484393781571,0.234541758508146,0.237089844994084,0.235302714507988,0.234908605539985,0.241723066131341,0.239397226151668,0.236744755732223,0.236138165511054,0.238888318335754,0.23646881513384,0.235292869190921,0.235294621542299,0.284646814885051,0.268473642391999,0.236057358228925,0.224330718569127,0.216095954649342,0.218465328763744,0.215618090149479,0.217531096743521,0.218606952773548,0.217287376962401,0.341740462030342,0.877166672016699,0.87552971031279,0.87601193766177,0.875644626052163,0.876668946420929,0.876122859893092,0.20930270962338,0.204748781350747,0.206783416125098,0.20454838216768,0.20194633235196,0.205226201906459,0.202807726567386,0.20424869771024,0.206756364133566,0.204155976999874,0.208812311074394,0.450333537982221,0.448269082277586,0.460049802464149,0.456612166258215,0.491667283413933,0.457473510401988,0.48976271720608,0.457396648681113,0.454871996861623,0.457431113364066,0.458022962164728,0.456314302691146,0.456510636040146,0.209381701229852,0.204719673392222,0.208674052345079,0.205329683289392,0.202680840679063,0.207145637210889,0.205822813909434,0.206153761730749,0.203411529195733,0.207234005628262,0.204340805800465,0.204496486921112,0.227360248473996,0.228801550346503,0.225441966219828,0.222538844834254,0.225366992751225,0.219352650759988,0.224115444695644,0.221136114501115,0.223525605037242,0.218846144410543,0.221383913828925,0.223699281441039,0.221691521160372,0.222455257227755,0.570804067211078,0.571957034312029,0.569530716355332,0.568825519452776,0.570046586623116,0.569027315382316,0.569331201518259,0.567793850844391,0.641542144012746,0.641094295690704,0.641386012565607,0.641051363738854,0.640771488041662,0.641621958638627,0.64211333533466,0.64118477658921,0.641143763384105,0.641736211252752,0.641614103548304,0.243760160501202,0.242865891103082,0.241393271110197,0.238782266290971,0.24031835688431,0.242944057788489,0.240560958368351,0.238501541819196,0.238635933888525,0.299815935683308,0.297316780297534,0.298538458986317,0.295734305188682,0.295574176167015,0.295300100386121,0.296976171945018,0.296039289710156,0.295958483084325,0.296295670426523,0.297260633854628,0.295657322800987,0.237556691328359,0.238005842515785,0.620703538610822,0.620116745595553,0.622257377069677,0.619726416702635,0.619363358640029,0.619638687140571,0.619041998634481,0.618286722181999,0.618683421547553,0.618530316924854,0.618805472676405,0.618487823783378,0.618320207528616,0.223967304345884,0.228580022972143,0.221043184312786,0.219475364159598,0.221107135332864,0.224289082703251,0.220840246144903,0.223224574763777,0.221595088920185,0.247169741389628,0.238838313702609,0.239613740963465,0.236452101782645,0.238053438280435,0.235626338207467,0.238345764490542,0.236648351907144,0.235918414747591,0.237355947572076,0.235937136781419,0.313138556274103,0.314104261630144,0.317030202764698,0.315763337887457,0.312610818047248,0.316437562375294,0.31591010284356,0.315399792327471,0.314009340394521,0.316866474390287,0.31382414703905,0.278753216773365,0.225029626906435,0.229680022477657,0.223173105291831,0.221889165069666,0.220194921067434,0.223935847521453,0.223251391915262,0.22090606222407,0.224154864243913,0.222603026882476,0.289463319405408,0.330705434301746,0.328731516187845,0.331366974485769,0.325981555181915,0.327467344306238,0.328833144416611,0.327487709493261,0.326194589845394,0.327362100320835,0.329566775974879,0.326502350897369,0.329360863011799,0.327124847021084,0.326670165339788,0.367390103540668,0.365678768146118,0.364384159680186,0.366103206245457,0.364080955638102,0.365335945076962,0.364729422894592,0.365490359655605,0.365726989387104,0.365248403516304,0.363991782403001,0.36579860791295,0.36477668915283,0.365781804160665,0.274407514827385,0.270496540177428,0.270911572506363,0.269265383030508,0.273483615602279,0.270332032925138,0.2669177366235,0.26793331958133,0.271529885311755,0.270977537339471,0.315590346318397,0.315259198098516,0.311320989828955,0.312112906131041,0.312215469348108,0.312520692739474,0.313626855376149,0.312076345088335,0.315118806534281,0.275266801009546,0.273956474319515,0.27272383901823,0.273195712445649,0.273907007535641,0.273534994735642,0.273615702834859,0.273465924207967,0.274731344709393,0.274186897800417,0.273685225126156,0.274053606843984,0.3312975534463,0.328539590204473,0.322550051466586,0.323909059346031,0.321843366831372,0.321919053273222,0.322833104288851,0.321886685053849,0.32009686607037,0.322898743454994,0.322998220548042,0.373560273867921,0.369125313203407,0.367578846576174,0.368715581495917,0.367621280058675,0.368266862184068,0.368581698777235,0.368457006260295,0.368158777568557,0.368550668789483,0.368549460665964,0.513281881038008,0.513011474803994,0.512184365007218,0.510428369839886,0.512070511534224,0.511221673372959,0.511084995503801,0.511921508797899,0.511011065808793,0.511758899689516,0.511722086786092,0.511623795917401,0.510963695227891,0.511129549233303,0.511501561818427,0.510748944242552,0.511521993753277,0.285054897710466,0.281645435339901,0.282213703301947,0.284230526749681,0.282668624532459,0.283340176086574,0.281218577201816,0.282855187570427,0.286318304744205,0.281612282193543,0.283984446390407,0.283173394589279,0.600315921276255,0.600210270441864,0.598976640612782,0.60031625388026,0.599411873885669,0.599298486332654,0.599379270675241,0.600218316924989,0.599663046505501,0.600082489525507,0.599519896057942,0.599169298492398,0.348696826842428,0.347469406838721,0.348121758191074,0.347881314154229,0.346794600273894,0.349289965830591,0.346983501954423,0.349886632139,0.345404821971503,0.346353598903464,0.347374281440576,0.348100181742267,0.349427753658311,1.1146635074478,1.11309954227588,1.11372374095196,1.11264431040384,1.11232440815884,1.11242028484861,329.445678691286,329.445682682275,329.445677450554,329.445677064486,329.445676009334,329.445675608179,329.445673894925,0.632102952470836,0.630549942773296,0.629044768091583,0.629149924634197,0.628954241908287,0.628564907697931,0.629155612017181,0.627966021806681,0.628453154085955,0.628667691943652
  };
  public static double[] airlines_test_se_fit = new double[]{ // first 1000 values
      0.251450554916556,0.255143830810462,0.250750114433071,0.254609363170226,0.25355574609781,0.253569526824335,0.253198478084409,0.253731960606367,0.255434860870336,0.40959781971013,0.414970245788271,0.411232566451419,0.407837615953484,0.409445258466602,0.408555204680398,0.408022946388245,0.408766051276899,0.40696396073604,0.408459030053869,0.408423242904325,0.407740430595714,0.408731957912749,0.407761104810469,0.407855982083816,0.409312492717521,0.218553841592738,0.215186973883216,0.211638171777674,0.215312573911731,0.212714160011116,0.213895946294093,0.215989840061836,0.213830942897177,0.213298679402053,0.255519916324211,0.247802657734528,0.244901079491517,0.246588591447602,0.246729081170047,0.244249605194004,0.246133611750184,0.245604409246427,0.246788489266748,0.245627606111621,0.216945000793476,0.219405669467199,0.216512364601334,0.216779460419078,0.219018824243438,0.217899269985245,0.296503706226226,0.291915645044483,0.293506057381021,0.292370973629519,0.293737668608105,0.292741587313445,0.29362353820564,0.293114734548631,0.290220680280176,0.291145174669934,0.29372321375144,0.221880536697501,0.228741063353243,0.217841484274136,0.219858265135848,0.218042599301826,0.21924162391801,0.219010485857312,0.217755127020747,0.218542503140667,0.426641390325725,0.428852470870363,0.426058518980518,0.424577851931165,0.424321478096002,0.424729031423803,0.425830975909352,0.424216137382124,0.425182201700057,0.425177331500817,0.424486403799603,0.249420603748473,0.24762500904199,0.248728247282976,0.244426412093614,0.248308869156009,0.24689868198961,0.243507755740301,0.247009925973082,0.246592233909272,0.246992762284425,0.246933879545789,0.246949837622546,0.245579027066563,0.246642584834114,0.246887905313101,0.247018601076682,0.219194947160869,0.215867165564473,0.21616230239512,0.213242876020725,0.21318711435217,0.21545891461055,0.314727700209492,0.312908009334306,0.217219020766606,0.214154306839475,0.216736095093749,0.214844780254851,0.211553052095122,0.215362849260011,0.215279448675082,0.217868664576054,0.21513680001966,0.214714612872946,0.212000757812957,0.215471818548072,0.214055790588869,0.246196698251093,0.240963599711271,0.243886711082899,0.244427781380908,0.24144845911168,0.241355729017953,0.227322313620071,0.224712626423316,0.22328144311773,0.225858199135661,0.224247708411768,0.225398816373923,0.224019875979584,0.224024528159037,0.23579594143361,0.233378880784555,0.231712304892888,0.232322371112675,0.229591617982479,0.232161698175744,0.233773869290004,0.231655399755678,0.230878935519655,0.232856679852818,0.230577751338938,0.231428598988614,0.220678507293932,0.219301910933735,0.217864563852008,0.217046715371333,0.216642539916605,0.21572993752933,0.219240113729478,0.219415444798789,0.215969361516724,0.2192761842376,0.217857573385196,0.228622231818979,0.280668284152465,0.222865432227152,0.224650882296078,0.223061986861913,0.223089106221901,0.22376067768248,0.223035484999645,0.223579546397353,0.220917950705367,0.221993613695227,0.231636428089099,0.229887078998611,0.230961688000045,0.22992829004687,0.228527722817852,0.225672877593226,0.227552812543195,0.230007024691303,0.228187773705819,0.226733430218631,0.226320771939252,0.22785245370243,0.221668997246316,0.226345308426324,0.222023564199528,0.219159310733553,0.218732156870802,0.216963620850539,0.218307690571116,0.230963280677776,0.231775280600039,0.227067082071713,0.229249312724755,0.22917784758727,0.232420570953631,0.229528557141395,0.22602233028324,0.226788979767375,0.228643690184303,0.231083774156169,0.228849382861369,0.229983899953183,0.229656929986989,0.228788889091205,0.2273919706989,0.228521350232182,0.266910670369674,0.269184801177692,0.225000885234718,0.224925603353239,0.222613808980553,0.222855731054827,0.220736166347589,0.221529480025904,0.22283250954231,0.222953131159934,0.222975936430719,0.246518936953516,0.242387354846196,0.243935225217631,0.245900767333122,0.245507936256604,0.243825145119544,0.241432780887818,0.245874711828154,0.242008590289429,0.245669673858323,0.244863472085187,0.243450835625353,0.241816556819575,0.267113714143713,0.275700349452851,0.267563016952914,0.265054965184802,0.265829588222913,0.267862302943069,0.266308361182714,0.265653184859434,0.268665261382787,0.268104417511886,0.26835390274921,0.266719185888133,0.267156435776544,0.305671407825781,0.231876747755539,0.228711316004514,0.225990281554739,0.225366366452924,0.228800641236689,0.226209845955848,0.247116506329976,0.246553870095521,0.249992228372047,0.243404133716741,0.243406504076594,0.241654406256442,0.242584899656031,0.246373786991839,0.242808045771165,0.246237214868255,0.24404358621963,0.242899192934874,0.228499276542162,0.225577519468245,0.223310012362194,0.221561056746008,0.224454455679708,0.226183417066088,0.222233361562326,0.221813585904937,0.232418407065593,0.233294465708592,0.232103227705799,0.226890382121983,0.229046335866865,0.231008448172969,0.228799396564417,0.227092721256577,0.229450437002389,0.230872770060229,0.22844170696334,0.230857108934349,0.228456602944629,0.229594155774374,0.228934826328822,0.227348415380798,0.22881884957181,0.228468627176897,0.270801265669912,0.268281313946493,0.270581114123659,0.268598479663967,0.268290826109655,0.271234107076345,0.271245062848416,0.270182039860544,0.269653227771904,0.26787850669138,0.269896116380471,0.269636612953902,0.26945076436797,0.270875557716513,0.295614951813016,0.296027479094701,0.296724808775578,0.296981417095852,0.295920053713257,0.294467122985173,0.296812809239342,0.296368869836661,0.294512801236827,0.295879819432006,0.296520348045663,0.296517247740995,0.295522815597283,0.289967911021239,0.287580636365705,0.286251210430152,0.287763245632273,0.286414105742219,0.285295529235878,0.28900363975015,0.284919161687837,0.289574500828581,0.286053163728741,0.285352311217594,0.286789454714898,0.287357426429895,0.343102682160766,0.342437509807424,0.342549115322627,0.267947634494488,0.267068459475279,0.268183528161465,0.267646128347768,0.268537473571683,0.266638138444001,0.266200274849385,0.265501522466787,0.267103293218617,0.297948319969081,0.296195270565676,0.297245515322992,0.295470211403385,0.297665988691046,0.296279018909483,0.29753174034545,0.295960164169138,0.28876642705184,0.288206716034253,0.287966374154902,0.287276316424221,0.28772855787562,0.286499252563653,0.290345391309097,0.28797410951269,0.289589912457281,0.289722032977705,0.290892600173831,0.287927972959796,0.286423105926245,0.287848927066334,0.290780902487769,0.297086701167483,0.302221149385396,0.296354513611992,0.297155342047489,0.296042087550352,0.292649077258483,0.295717344302441,0.295716630125129,0.294439067111837,0.296246465362586,0.296481255451858,0.294062530424865,0.295940344362412,0.295462741236841,0.296040756485113,0.296026225532513,0.29615166627013,0.297078378144834,0.298082144085812,0.295836819209862,0.298757880732068,0.297248314085886,0.295481594032778,0.296096007636011,0.297948733399344,0.297913463334751,0.297069321055681,0.29433361343137,0.29786117264078,0.295938280122948,0.290439185907847,0.291936130819434,0.290080505816671,0.289320363638696,0.289755801488416,0.288649936400564,0.287177263645138,0.287542151351606,0.290589190323509,0.289180110521686,0.287422821821185,0.288894931390426,0.289975721529779,0.275721636888117,0.274878877777895,0.274813682800862,0.273380252723427,0.269678515718325,0.273503428923553,0.273173355806965,0.271116990153365,0.271852753046148,0.268966749862708,0.265123600226666,0.264300671390186,0.267124954162684,0.268574045126624,0.26508959997038,0.268407256113939,0.267499993026436,0.266439372160217,0.268099416877506,0.352969859183734,0.351758667041342,0.351060817635125,0.350232199123663,0.353146236087956,0.350094011012341,0.351511609576483,0.352400142876538,0.349656540532378,0.351706881930985,0.350789463397989,0.348721786355671,0.351444603538193,0.350983548797572,0.262059503337869,0.354301881135165,0.356186484085367,0.353075288868367,0.352278113507136,0.352681236536589,0.349256527782663,0.353752647215148,0.35075265392935,0.351262049275191,0.352039934393791,0.349517172575706,0.295126283331877,0.294179509804158,0.290976649035767,0.29283666128662,0.294975621260255,0.291350779807003,0.293804771241421,0.291031016783528,0.292678840205405,0.292506855623454,0.29151786790578,0.292041998505508,0.293093376654528,0.457588987327437,0.457034057444657,0.455986423328215,0.455825404898349,0.454557445951917,0.454449364324171,0.45712670014674,0.453704129697303,0.455650915773145,0.217549948597888,0.205375675193342,0.203344736786727,0.208070962313178,0.203874980533156,0.205021058537882,0.206639488001851,0.207800733504676,0.205058483726345,0.207374082060639,0.209580948067117,0.338806663032727,0.335005269313243,0.334852578198195,0.336609056748515,0.335893255675258,0.336152257304785,0.3351560417141,0.334784339075969,0.341594866324406,0.343509758462072,0.341136977737262,0.339459522079771,0.342127907970959,0.340969825639722,0.33999775763035,0.340449081965221,0.341969654964145,0.340367010841326,0.340726388279708,0.34041774637808,0.337908350802502,0.340982423224225,0.33165388801438,0.331868444994168,0.331002324850095,0.333559445864731,0.331766688409756,0.332988459319856,0.331136426328967,0.334078744080107,0.331089841984049,0.331397225886296,0.332362261625384,0.331482677871025,0.331710087564591,0.333412929208952,0.332372376277865,0.2497661565504,0.23733601580522,0.239058530287158,0.240188720968246,0.239204849351438,0.239518071994299,0.236247581270848,0.239961615647949,0.239461250937761,0.238057417470287,0.239870633807778,0.240472640704174,0.238554902727518,0.236027302057546,0.227047977125619,0.220095269132744,0.22104206634128,0.219115724240815,0.221833532810681,0.220918157021067,0.222409357742062,0.220316299774088,0.220052863230834,0.258010174285458,0.250055738776852,0.248237891204648,0.249140318218246,0.248934782292946,0.251847079728827,0.248542008490632,0.248280063988074,0.24986147769131,0.249299859846186,0.249216696114835,0.248683854288859,0.303732919362705,0.304533888796394,0.299632383063058,0.303815823944402,0.300215335014459,0.30298435465779,0.300974689635131,0.301408808714384,0.301365777761846,0.303349418819277,0.302487918477062,0.300449505866158,0.303039042219422,0.23536584445712,0.23839481580463,0.222954219135661,0.226023573616417,0.220948812913713,0.219498478415873,0.216993669408316,0.220122993955001,0.219039132005548,0.220638132404314,0.220432232092691,0.218602598603678,1.06557848272101,1.06565042611775,1.06507788310947,1.06491127299608,1.06432832748723,1.06465082436258,1.06561971860787,1.06432200569326,1.06465110877312,1.06531553216273,1.06456394129938,1.06509131389817,0.30359403527427,0.300700587863704,0.302593275671198,0.302854876113147,0.301147409101601,0.302327878932938,0.301976895067688,0.303279558610957,0.302335659255981,0.302540244018855,0.287994436499925,0.28735911738,0.292766551582491,0.285828595054776,0.283605681027362,0.286364745453258,0.286430435615166,0.286693185926073,0.286385782730284,0.283682888471217,0.286176575553621,0.284643671523736,0.286299200325485,0.229200906439495,0.225442866485428,0.218707894886813,0.219857988640184,0.219408971162767,0.219638203466951,0.219182355794283,0.218592424169309,0.218016194592336,0.220768658974225,882.743390251695,882.743389333143,0.275056113035706,0.272242920858658,0.271910112315071,0.274610821308273,0.271274854229862,0.272313519537606,0.271260726261977,0.270456621863933,0.273028463616308,0.273087868897159,0.271824505049423,0.272133991542698,0.271171573064047,0.272590715258189,0.238008906686917,0.237462289730546,0.237175813959442,0.238741135516143,0.235597236949267,0.237782553394932,0.236308445406473,0.237827651223718,0.237419786897129,0.237929621420011,0.234186865643439,0.236704958048293,0.461642768972209,0.456383849571228,0.456175971419142,0.207797478054088,0.216200338979424,0.200991029669875,0.204760901136362,0.205629454136532,0.202611828785366,0.205711428538086,0.205955388309447,0.203388332646795,0.238487130830595,0.246044387187959,0.236261248333875,0.23836652469369,0.236351199448352,0.234064372129564,0.236729255695141,0.236372400559613,0.234979763810222,0.236610447058251,0.241120721466816,0.239933668287245,0.238456852232389,0.239431910204414,0.23861128752149,0.237850654902517,0.237187816567153,0.235707627052876,0.238351235070375,0.237514266887143,0.237058846628566,0.239358096093542,0.239525936275321,0.238207916021558,0.235647675931753,0.28943586190888,0.286981664526195,0.285234979620746,0.268979360202218,0.235135599628872,0.221183112122826,0.228409329217834,0.219549192223569,0.21525149474041,0.217567178087281,0.218220677091685,0.218205418653573,0.217417333921945,0.217175793274603,0.219433340661891,0.218459593909981,0.218461406394415,0.217236783042752,0.334538575232884,0.33465751805693,0.334961070710681,0.336417581483254,0.880594840758641,0.879767907763144,0.879806940895756,0.879953159371115,0.879805870824535,0.879895617527423,0.879369219436114,0.879847054885382,0.878522131280107,0.877630634906146,0.217533547217906,0.210613213012129,0.206371980975158,0.204131981611717,0.205732656006151,0.207987495510789,0.205881306439434,0.206528355033711,0.205483020339302,0.206563264873371,0.203649011285597,0.207242632451458,0.207016874715295,0.204873514781451,0.448522089796469,0.449984068156882,0.458239836114132,0.458378045529208,0.491551636162791,0.457591325279626,0.458781981395859,0.458258514760917,0.460034002803654,0.262869678421789,0.217999852561977,0.206475667417874,0.207099668580664,0.205357841124287,0.205218970111678,0.207531127881164,0.207760774832569,0.20540247749296,0.20732782301324,0.225228649056166,0.221625109031974,0.225378862958511,0.221564731995689,0.223551748645631,0.223566131973531,0.224997515075977,0.222871242371175,0.22421054396774,0.571919424936377,0.571280852057296,0.572107686998347,0.570808144319614,0.571461098560526,0.571768896058961,0.572067555055919,0.569137088331298,0.571143684794808,0.570789267411035,0.57016494431206,0.644504187918454,0.646195517734575,0.645863479564495,0.644092520237565,0.643834758126461,0.64387532653142,0.643770181317351,0.643467320363835,0.64370511782873,0.644825039854481,0.644220508357334,0.644033579787564,0.643948821622521,0.242658775641656,0.242234540021716,0.241928880801991,0.240224287676369,0.238430504581427,0.241347522582,0.241945273111314,0.238266802032835,0.241740855467917,0.24180834307192,0.24174272712758,0.297935487899747,0.299150493248661,0.296849945071452,0.295070774527215,0.298004846179934,0.295651138798407,0.295145552290525,0.296489675572646,0.238083971905543,0.241051792839467,0.367616833600046,0.36648015434148,0.620338294129717,0.620523610152338,0.621532589189056,0.620130622788495,0.621498227458428,0.621104811413681,0.621136743588439,0.621078829709539,0.620535026231831,0.225618374640338,0.224502687802335,0.222310824191259,0.223913451956765,0.222792741083027,0.221441627884778,0.224342773860294,0.223278877919466,0.22224611018124,0.219124775597384,0.222828150177697,0.222521899061585,0.219824979877453,0.239942916182903,0.241118083019394,0.237712749810435,0.237314019784121,0.236340449835877,0.2380902082844,0.238188903897164,0.239044204882315,0.239170279904105,0.236483435302711,0.23648462101198,0.236867382813533,0.315563617138308,0.322876085359976,0.316888164667825,0.316509564169423,0.316137160413822,0.314317523301962,0.315515328056503,0.314559651417536,0.316552667828752,0.316885474813708,0.316244879917631,0.271452998789178,0.268489165091123,0.271259871579967,0.22646910668111,0.234449470315628,0.220937569133533,0.223244803074818,0.220213186720889,0.22314368031699,0.223417928367594,0.289995226748592,0.223731361979368,0.223914340710444,0.327047887673615,0.329031792606252,0.328178641999449,0.327438183904702,0.329349144390343,0.327842071638361,0.367757404752434,0.366366898746095,0.367899511255758,0.366861971250656,0.367245606545277,0.364526192796151,0.366256067190953,0.365859002603714,0.273067900732595,0.271933873816992,0.272168905039584,0.270825979690196,0.271254358648027,0.270269688728529,0.27074494849248,0.268987890120678,0.271557934404531,0.268658449953121,0.269201886685616,0.270580769935683,0.269842268565549,0.272690018787278,0.31377187767057,0.321300195839735,0.314807113999896,0.312138461968502,0.31372599936998,0.313978816774456,0.310742911600284,0.314642860520612,0.314097018829418,0.314653094760186,0.313394461787642,0.275980695373521,0.274993393484058,0.274457152582297,0.276521799877752,0.274277543575236,0.275683649316792,0.27526522864646,0.276685035319294,0.274867234334647,0.275659081296938,0.329731916578769,0.326141812423053,0.324988782409066,0.324126019027691,0.323003784982014,0.323405784870418,0.324172531379898,0.372707181576679,0.37320176340459,0.368047765723435,0.36863568292651,0.369559345476394,0.370649280684124,0.368630112097832,0.370536408854824,0.37131508713947,0.368907621642309,0.368161845921996,0.515101123450845,0.512355648583072,0.512342075558919,0.513707190469833,0.512172689549889,0.512888709343852,0.514855732855093,0.51273973554341,0.513145280300396,0.514120238029904,0.288156644191462,0.290293204934781,0.287003808691784,0.282159490164246,0.284337486524303,0.2842231518359,0.28135216915841,0.284931471456227,0.283550650621738,0.284169948534763,0.282809179158791,0.28223198854409,0.283158148838221,0.282844651334054,0.603518192864963,0.603541624470862,0.602189012225367,0.601355037851351,0.601014972869881,0.601382895096949,0.601684509253601,0.601462193616151,0.602324820207653,0.602137711950981,0.601657676652105,0.601460163388076,0.601870703297837,0.601855887442203,0.348822334885357,0.354599900881644,0.352252272386485,0.347781233606848,0.34712877430558,0.348146508621808,0.348413170630639,0.347056396503896,0.347657189570787,0.349005208401322,0.347194368753273,1.11614448244517,1.11742223846212,1.11550379344567,1.11545636591112,1.11529979340641,1.11545723114868,1.11530494553857,1.11510127968431,1.11554151555299,1.11508850135983,1.1150874967788,1.11538032993509,1.11519522156362,1.11557269795766,329.44568814446,329.445682885631,329.445680986748,329.445682675088
  };


  @Test
  public void testPValues(){    
//    1) NON-STANDARDIZED

//    summary(m)
//
//    Call:
//    glm(formula = CAPSULE ~ ., family = binomial, data = D)
//
//    Deviance Residuals:
//    Min       1Q   Median       3Q      Max
//    -2.0601  -0.8079  -0.4491   0.8933   2.2877
//
//    Coefficients:
//    Estimate Std. Error z value Pr(>|z|)
//    (Intercept) -7.133333   2.383945  -2.992  0.00277 **
//    ID           0.001710   0.001376   1.242  0.21420
//    AGE         -0.003268   0.022370  -0.146  0.88384
//    RACER2       0.068308   1.542397   0.044  0.96468
//    RACER3      -0.741133   1.582719  -0.468  0.63959
//    DPROSb       0.888329   0.395088   2.248  0.02455 *
//    DPROSc       1.305940   0.416197   3.138  0.00170 **
//    DPROSd       0.784403   0.542651   1.446  0.14832
//    DCAPSb       0.612371   0.517959   1.182  0.23710
//    PSA          0.030255   0.011149   2.714  0.00665 **
//    VOL         -0.009793   0.008753  -1.119  0.26320
//    GLEASON      0.851867   0.182282   4.673 2.96e-06 ***
//    ---
//      Signif. codes:  0 '***' 0.001 '**' 0.01 '*' 0.05 '.' 0.1 ' ' 1
//
//    (Dispersion parameter for binomial family taken to be 1)
//
//    Null deviance: 390.35  on 289  degrees of freedom
//    Residual deviance: 297.65  on 278  degrees of freedom
//    AIC: 321.65
//
//    Number of Fisher Scoring iterations: 5

//    sm$coefficients
//                    Estimate  Std. Error     z value     Pr(>|z|)
//    (Intercept) -7.133333499 2.383945093 -2.99223901 2.769394e-03
//    ID           0.001709562 0.001376361  1.24208800 2.142041e-01
//    AGE         -0.003268379 0.022369891 -0.14610616 8.838376e-01
//    RACER2       0.068307757 1.542397413  0.04428674 9.646758e-01
//    RACER3      -0.741133313 1.582718967 -0.46826589 6.395945e-01
//    DPROSb       0.888329484 0.395088333  2.24843259 2.454862e-02
//    DPROSc       1.305940109 0.416197382  3.13779030 1.702266e-03
//    DPROSd       0.784403119 0.542651183  1.44550154 1.483171e-01
//    DCAPSb       0.612371497 0.517959064  1.18227779 2.370955e-01
//    PSA          0.030255231 0.011148747  2.71377864 6.652060e-03
//    VOL         -0.009793481 0.008753002 -1.11887108 2.631951e-01
//    GLEASON      0.851867113 0.182282351  4.67333842 2.963429e-06
    GLMParameters params = new GLMParameters(Family.binomial);
    params._response_column = "CAPSULE";
    params._standardize = false;
    params._train = _prostateTrain._key;
    params._compute_p_values = true;
    params._objective_epsilon = 0;
    params._missing_values_handling = MissingValuesHandling.Skip;
    params._lambda = new double[]{0};
    params._beta_epsilon = 1e-4;
    GLM job0 = null;
    try {
      params._solver = Solver.L_BFGS;
      job0 = new GLM(params);
      GLMModel model = job0.trainModel().get();
      assertFalse("should've thrown, p-values only supported with IRLSM",true);
    } catch(H2OModelBuilderIllegalArgumentException t) {
    }
    boolean naive_descent_exception_thrown = false;
    try {
      params._solver = Solver.COORDINATE_DESCENT_NAIVE;
      job0 = new GLM(params);
      GLMModel model = job0.trainModel().get();
      assertFalse("should've thrown, p-values only supported with IRLSM",true);
    } catch(H2OIllegalArgumentException e) {
      naive_descent_exception_thrown = true;
    }
    assertTrue(naive_descent_exception_thrown);
    try {
      params._solver = Solver.COORDINATE_DESCENT;
      job0 = new GLM(params);
      GLMModel model = job0.trainModel().get();
      assertFalse("should've thrown, p-values only supported with IRLSM",true);
    } catch(H2OModelBuilderIllegalArgumentException t) {
    }
    params._solver = Solver.IRLSM;
    try {
      params._lambda = new double[]{1};
      job0 = new GLM(params);
      GLMModel model = job0.trainModel().get();
      assertFalse("should've thrown, p-values only supported with no regularization",true);
    } catch(H2OModelBuilderIllegalArgumentException t) {
    }
    params._lambda_search = false;
    params._lambda = new double[]{0};
    GLM job = new GLM(params);
    GLMModel model = null;
    Frame predictTrain = null;
    Frame predictTest = null;
    try {
      model = job.trainModel().get();
      String[] names_expected = new String[]{"Intercept", "ID", "AGE", "RACE.R2", "RACE.R3", "DPROS.b", "DPROS.c", "DPROS.d", "DCAPS.b", "PSA", "VOL", "GLEASON"};
      double[] stder_expected = new double[]{2.383945093, 0.001376361, 0.022369891, 1.542397413, 1.582718967, 0.395088333, 0.416197382, 0.542651183, 0.517959064, 0.011148747, 0.008753002, 0.182282351};
      double[] zvals_expected = new double[]{-2.99223901, 1.24208800, -0.14610616, 0.04428674, -0.46826589, 2.24843259, 3.13779030, 1.44550154, 1.18227779, 2.71377864, -1.11887108, 4.67333842};
      double[] pvals_expected = new double[]{2.769394e-03, 2.142041e-01, 8.838376e-01, 9.646758e-01, 6.395945e-01, 2.454862e-02, 1.702266e-03, 1.483171e-01, 2.370955e-01, 6.652060e-03, 2.631951e-01, 2.963429e-06};
      String[] names_actual = model._output.coefficientNames();
      System.out.println("names actual = " + Arrays.toString(names_actual));
      HashMap<String, Integer> coefMap = new HashMap<>();
      for (int i = 0; i < names_expected.length; ++i)
        coefMap.put(names_expected[i], i);
      double[] stder_actual = model._output.stdErr();
      double[] zvals_actual = model._output.zValues();
      double[] pvals_actual = model._output.pValues();
      for (int i = 0; i < stder_expected.length; ++i) {
        int id = coefMap.get(names_actual[i]);
        assertEquals(stder_expected[id], stder_actual[i], stder_expected[id] * 1e-4);
        assertEquals(zvals_expected[id], zvals_actual[i], Math.abs(zvals_expected[id]) * 1e-4);
        assertEquals(pvals_expected[id], pvals_actual[i], pvals_expected[id] * 1e-3);
      }
      predictTrain = model.score(_prostateTrain);
      Vec.Reader r = predictTrain.vec("StdErr").new Reader();
      for(int i = 0; i < se_fit_train.length; ++i)
        assertEquals(se_fit_train[i],r.at(i),1e-4);
      predictTest = model.score(_prostateTest);
      r = predictTest.vec("StdErr").new Reader();
      for(int i = 0; i < se_fit_test.length; ++i)
        assertEquals(se_fit_test[i],r.at(i),1e-4);
    } finally {
      if(model != null) model.delete();
      if(predictTrain != null) predictTrain.delete();
      if(predictTest != null) predictTest.delete();
    }
//    2) STANDARDIZED

//    Call:
//    glm(formula = CAPSULE ~ ., family = binomial, data = Dstd)
//
//    Deviance Residuals:
//    Min       1Q   Median       3Q      Max
//    -2.0601  -0.8079  -0.4491   0.8933   2.2877
//
//    Coefficients:
//    Estimate Std. Error z value Pr(>|z|)
//    (Intercept) -1.28045    1.56879  -0.816  0.41438
//    ID           0.19054    0.15341   1.242  0.21420
//    AGE         -0.02118    0.14498  -0.146  0.88384
//    RACER2       0.06831    1.54240   0.044  0.96468
//    RACER3      -0.74113    1.58272  -0.468  0.63959
//    DPROSb       0.88833    0.39509   2.248  0.02455 *
//      DPROSc       1.30594    0.41620   3.138  0.00170 **
//    DPROSd       0.78440    0.54265   1.446  0.14832
//    DCAPSb       0.61237    0.51796   1.182  0.23710
//    PSA          0.60917    0.22447   2.714  0.00665 **
//    VOL         -0.18130    0.16204  -1.119  0.26320
//    GLEASON      0.91751    0.19633   4.673 2.96e-06 ***
//    ---
//      Signif. codes:  0 '***' 0.001 '**' 0.01 '*' 0.05 '.' 0.1 ' ' 1
//
//    (Dispersion parameter for binomial family taken to be 1)
//
//    Null deviance: 390.35  on 289  degrees of freedom
//    Residual deviance: 297.65  on 278  degrees of freedom
//    AIC: 321.65
//
//    Number of Fisher Scoring iterations: 5

//    Estimate Std. Error     z value     Pr(>|z|)
//    (Intercept) -1.28045434  1.5687858 -0.81620723 4.143816e-01
//    ID           0.19054396  0.1534062  1.24208800 2.142041e-01
//    AGE         -0.02118315  0.1449847 -0.14610616 8.838376e-01
//    RACER2       0.06830776  1.5423974  0.04428674 9.646758e-01
//    RACER3      -0.74113331  1.5827190 -0.46826589 6.395945e-01
//    DPROSb       0.88832948  0.3950883  2.24843259 2.454862e-02
//    DPROSc       1.30594011  0.4161974  3.13779030 1.702266e-03
//    DPROSd       0.78440312  0.5426512  1.44550154 1.483171e-01
//    DCAPSb       0.61237150  0.5179591  1.18227779 2.370955e-01
//    PSA          0.60917093  0.2244733  2.71377864 6.652060e-03
//    VOL         -0.18129997  0.1620383 -1.11887108 2.631951e-01
//    GLEASON      0.91750972  0.1963285  4.67333842 2.963429e-06

    params._standardize = true;

    job = new GLM(params);
    try {
      model = job.trainModel().get();
      String[] names_expected = new String[]{"Intercept", "ID", "AGE", "RACE.R2", "RACE.R3", "DPROS.b", "DPROS.c", "DPROS.d", "DCAPS.b", "PSA", "VOL", "GLEASON"};
      // do not compare std_err here, depends on the coefficients
//      double[] stder_expected = new double[]{1.5687858,   0.1534062,   0.1449847,   1.5423974, 1.5827190,   0.3950883,   0.4161974,  0.5426512,   0.5179591,   0.2244733, 0.1620383,   0.1963285};
//      double[] zvals_expected = new double[]{-0.81620723,  1.24208800, -0.14610616 , 0.04428674, -0.46826589 , 2.24843259,  3.13779030 , 1.44550154 , 1.18227779 , 2.71377864 ,-1.11887108 , 4.67333842};
//      double[] pvals_expected = new double[]{4.143816e-01 ,2.142041e-01 ,8.838376e-01, 9.646758e-01, 6.395945e-01, 2.454862e-02, 1.702266e-03, 1.483171e-01, 2.370955e-01, 6.652060e-03 ,2.631951e-01, 2.963429e-06};

//      String[] names_expected = new String[]{"Intercept", "ID", "AGE", "RACE.R2", "RACE.R3", "DPROS.b", "DPROS.c", "DPROS.d", "DCAPS.b", "PSA", "VOL", "GLEASON"};
      double[] stder_expected = new double[]{2.383945093, 0.001376361, 0.022369891, 1.542397413, 1.582718967, 0.395088333, 0.416197382, 0.542651183, 0.517959064, 0.011148747, 0.008753002, 0.182282351};
      double[] zvals_expected = new double[]{-2.99223901, 1.24208800, -0.14610616, 0.04428674, -0.46826589, 2.24843259, 3.13779030, 1.44550154, 1.18227779, 2.71377864, -1.11887108, 4.67333842};
      double[] pvals_expected = new double[]{2.769394e-03, 2.142041e-01, 8.838376e-01, 9.646758e-01, 6.395945e-01, 2.454862e-02, 1.702266e-03, 1.483171e-01, 2.370955e-01, 6.652060e-03, 2.631951e-01, 2.963429e-06};
      String[] names_actual = model._output.coefficientNames();
      HashMap<String, Integer> coefMap = new HashMap<>();
      for (int i = 0; i < names_expected.length; ++i)
        coefMap.put(names_expected[i], i);
      double[] stder_actual = model._output.stdErr();
      double[] zvals_actual = model._output.zValues();
      double[] pvals_actual = model._output.pValues();
      for (int i = 0; i < zvals_expected.length; ++i) {
        int id = coefMap.get(names_actual[i]);
//        assertEquals(stder_expected[id], stder_actual[i], stder_expected[id] * 1e-5);
        assertEquals(zvals_expected[id], zvals_actual[i], Math.abs(zvals_expected[id]) * 1e-4);
        assertEquals(pvals_expected[id], pvals_actual[i], pvals_expected[id] * 1e-3);
      }
    } finally {
      if(model != null) model.delete();
    }
    params = new GLMParameters(Family.binomial);
    params._response_column = "IsDepDelayed";
    params._standardize = false;
    params._train = _airlinesTrain._key;
    params._compute_p_values = true;
    params._objective_epsilon = 0;
    params._remove_collinear_columns = true;
    params._missing_values_handling = MissingValuesHandling.Skip;
    params._lambda = new double[]{0};
    params._beta_epsilon = 1e-4;
    job = new GLM(params);
    model = job.trainModel().get();

    String [] names = model._output.coefficientNames();
    double [] p_values = model._output.pValues();
    for(int i = 0; i < names.length; ++i)
      System.out.println(names[i] + ": " + p_values[i]);
    System.out.println();
    System.out.println(model.generateSummary(params._train,10));
    System.out.println(model._output._training_metrics);
    Frame predict = model.score(_airlinesTrain);
    Vec.Reader r = predict.vec("StdErr").new Reader();
    int fails = 0;
    for(int i = 0; i < airlines_train_se_fit.length; ++i) {
      if(Math.abs(airlines_train_se_fit[i] - r.at(i)) > 1e-4){
        // NOTE: our vcov matrix is slightly different from R's. Does not matter for most std errs but outliers do not match.
        System.out.println("Mismatch at row " + i + ": " + airlines_train_se_fit[i] + " != " + r.at(i));
        if(airlines_train_se_fit[i] < 100 )fails++;
      }
    }

    assertEquals(0,fails);
    predict.delete();
    predict = model.score(_airlinesTest);
    r = predict.vec("StdErr").new Reader();
    fails = 0;
    for(int i = 0; i < airlines_test_se_fit.length; ++i) {
      if(Math.abs(airlines_test_se_fit[i] - r.at(i)) > 1e-4 ){
        // NOTE: our vcov matrix is slightly different from R's. Does not matter for most std errs but outliers do not match.
        System.out.println("Mismatch at row " + i + ": " + airlines_test_se_fit[i] + " != " + r.at(i));
        if(airlines_test_se_fit[i] < 100 )fails++;
      }
    }
    assertEquals(0,fails);
    predict.delete();
    model.delete();
  }

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
    _prostateTrain = parse_test_file("smalldata/glm_test/prostate_cat_train.csv");
    _prostateTest  = parse_test_file("smalldata/glm_test/prostate_cat_test.csv");
    _prostateTrainUpsampled = parse_test_file("smalldata/glm_test/prostate_cat_train_upsampled.csv");
    _abcd = parse_test_file("smalldata/glm_test/abcd.csv");
    Frame _airlines = parse_test_file("smalldata/airlines/AirlinesTrain.csv.zip");
    _airlines.remove("IsDepDelayed_REC").remove();
    Key k  = Key.make("airliens_rebalanced");
    H2O.submitTask(new RebalanceDataSet(_airlines,k,1)).join(); // need this to match the random split from R
    _airlines.delete();
    _airlines = DKV.getGet(k);
    String [] names = new String[]{"Origin", "Dest", "fDayofMonth", "fYear", "UniqueCarrier", "fDayOfWeek", "fMonth", "DepTime", "ArrTime", "Distance", "IsDepDelayed"};
    _airlines.restructure(names,_airlines.vecs(names));
    _airlinesTrain = new MRTask(){
      public void map(Chunk [] cs, NewChunk [] ncs){
        Random rnd = new Random(654321*(cs[0].cidx()+1));
        for(int i = 0; i < cs[0]._len; i++){
          if(rnd.nextDouble() > .5){
            for(int j = 0; j < cs.length; ++j)
              ncs[j].addNum(cs[j].atd(i));
          }
        }
      }
    }.doAll(_airlines.types(),_airlines).outputFrame(Key.make("airlines_train"),_airlines.names(),_airlines.domains());
    _airlinesTest = new MRTask(){
      public void map(Chunk [] cs, NewChunk [] ncs){
        Random rnd = new Random(654321*(cs[0].cidx()+1));
        for(int i = 0; i < cs[0]._len; i++){
          if(rnd.nextDouble() <= .5){
            for(int j = 0; j < cs.length; ++j)
              ncs[j].addNum(cs[j].atd(i));
          }
        }
      }
    }.doAll(_airlines.types(),_airlines).outputFrame(Key.make("airlines_test"),_airlines.names(),_airlines.domains());
    _airlines.delete();
  }

  @AfterClass
  public static void cleanUp() {
    if(_abcd != null)  _abcd.delete();
    if(_prostateTrainUpsampled != null) _prostateTrainUpsampled.delete();
    if(_prostateTest != null) _prostateTest.delete();
    if(_prostateTrain != null) _prostateTrain.delete();
    if(_airlinesTrain != null) _airlinesTrain.delete();
    if(_airlinesTest != null) _airlinesTest.delete();
  }
}
