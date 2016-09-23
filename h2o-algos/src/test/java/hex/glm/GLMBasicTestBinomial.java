package hex.glm;

import hex.ModelMetricsBinomialGLM;
import hex.deeplearning.DeepLearningModel.DeepLearningParameters.MissingValuesHandling;
import hex.glm.GLMModel.GLMParameters;
import hex.glm.GLMModel.GLMParameters.Family;
import hex.glm.GLMModel.GLMParameters.Solver;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.*;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.*;

import java.util.Arrays;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Created by tomasnykodym on 4/26/15.
 */
public class GLMBasicTestBinomial extends TestUtil {
  static Frame _prostateTrain; // prostate_cat_replaced
  static Frame _prostateTrainUpsampled; // prostate_cat_replaced
  static Frame _prostateTest; // prostate_cat_replaced
  static Frame _abcd; // tiny corner case dataset

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
            assertTrue(iae.getMessage().contains("Test/Validation dataset is missing offset vector"));
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
            assertTrue(iae.getMessage().contains("Test/Validation dataset is missing offset vector"));
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
//            assertTrue(iae.getMessage().contains("Test/Validation dataset is missing weights vector"));
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
            assertTrue(iae.getMessage().contains("Test dataset is missing weights vector"));
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
      job0 = new GLM(params);
      params._solver = Solver.L_BFGS;
      GLMModel model = job0.trainModel().get();
      assertFalse("should've thrown, p-values only supported with IRLSM",true);
    } catch(H2OModelBuilderIllegalArgumentException t) {
    }
    try {
      job0 = new GLM(params);
      params._solver = Solver.COORDINATE_DESCENT_NAIVE;
      GLMModel model = job0.trainModel().get();
      assertFalse("should've thrown, p-values only supported with IRLSM",true);
    } catch(H2OModelBuilderIllegalArgumentException t) {
    }
    try {
      job0 = new GLM(params);
      params._solver = Solver.COORDINATE_DESCENT;
      GLMModel model = job0.trainModel().get();
      assertFalse("should've thrown, p-values only supported with IRLSM",true);
    } catch(H2OModelBuilderIllegalArgumentException t) {
    }
    params._solver = Solver.IRLSM;
    try {
      job0 = new GLM(params);
      params._lambda = new double[]{1};
      GLMModel model = job0.trainModel().get();
      assertFalse("should've thrown, p-values only supported with no regularization",true);
    } catch(H2OModelBuilderIllegalArgumentException t) {
    }
    params._lambda = new double[]{0};
    try {
      params._lambda_search = true;
      GLMModel model = job0.trainModel().get();
      assertFalse("should've thrown, p-values only supported with no regularization (i.e. no lambda search)",true);
    } catch(H2OModelBuilderIllegalArgumentException t) {
    }
    params._lambda_search = false;
    GLM job = new GLM(params);
    GLMModel model = null;
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
    } finally {
      if(model != null) model.delete();
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
      double[] zvals_expected = new double[]{-0.81620723,  1.24208800, -0.14610616 , 0.04428674, -0.46826589 , 2.24843259,  3.13779030 , 1.44550154 , 1.18227779 , 2.71377864 ,-1.11887108 , 4.67333842};
      double[] pvals_expected = new double[]{4.143816e-01 ,2.142041e-01 ,8.838376e-01, 9.646758e-01, 6.395945e-01, 2.454862e-02, 1.702266e-03, 1.483171e-01, 2.370955e-01, 6.652060e-03 ,2.631951e-01, 2.963429e-06};
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
  }

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
    _prostateTrain = parse_test_file("smalldata/glm_test/prostate_cat_train.csv");
    _prostateTest  = parse_test_file("smalldata/glm_test/prostate_cat_test.csv");
    _prostateTrainUpsampled = parse_test_file("smalldata/glm_test/prostate_cat_train_upsampled.csv");
    _abcd = parse_test_file("smalldata/glm_test/abcd.csv");
  }

  @AfterClass
  public static void cleanUp() {
    if(_abcd != null)  _abcd.delete();
    if(_prostateTrainUpsampled != null) _prostateTrainUpsampled.delete();
    if(_prostateTest != null) _prostateTest.delete();
    if(_prostateTrain != null) _prostateTrain.delete();
  }
}
