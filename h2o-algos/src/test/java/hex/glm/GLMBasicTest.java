package hex.glm;

import hex.ModelMetricsBinomialGLM;
import hex.glm.GLMModel.GLMParameters;
import hex.glm.GLMModel.GLMParameters.Family;
import hex.glm.GLMModel.GLMParameters.Link;
import hex.glm.GLMModel.GLMParameters.Solver;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.*;
import water.fvec.*;
import water.parser.ParseDataset;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by tomasnykodym on 4/26/15.
 */
public class GLMBasicTest extends TestUtil {
  static Frame _prostateTrain; // prostate_cat_replaced
  static Frame _prostateTest; // prostate_cat_replaced

  @Test
  public void testOffset() {
    GLM job = null;
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
    Vec.Writer vw = offsetVecTrain.open();
    for(int i = 0; i < offset_train.length; ++i)
      vw.set(i,offset_train[i]);
    vw.close();

    Vec offsetVecTest = _prostateTest.anyVec().makeZero();
    vw = offsetVecTest.open();
    for(int i = 0; i < offset_test.length; ++i)
      vw.set(i,offset_test[i]);
    vw.close();

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
      for (Solver s : new Solver[]{Solver.AUTO, Solver.IRLSM, Solver.L_BFGS}) {
        Frame scoreTrain = null, scoreTest = null;
        try {
          params._solver = s;
          System.out.println("SOLVER = " + s);
          job = new GLM(Key.make("prostate_model"), "glm test simple poisson", params);
          model = job.trainModel().get();
          HashMap<String, Double> coefs = model.coefficients();
          System.out.println("coefs = " + coefs);
          for (int i = 0; i < cfs1.length; ++i)
            assertEquals(vals[i], coefs.get(cfs1[i]), 1e-4);
          assertEquals(355.7, GLMTest.nullDeviance(model), 1e-1);
          assertEquals(305.1, GLMTest.residualDeviance(model), 1e-1);
          assertEquals(289,   GLMTest.nullDOF(model), 0);
          assertEquals(285,   GLMTest.resDOF(model), 0);
          assertEquals(315.1, GLMTest.aic(model), 1e-1);
          assertEquals(76.8525, GLMTest.residualDevianceTest(model),1e-4);
          // test scoring
          try {
            scoreTrain = model.score(_prostateTrain);
            assertTrue("shoul've thrown IAE", false);
          } catch (IllegalArgumentException iae) {
            assertTrue(iae.getMessage().contains("Test dataset is missing offset vector"));
          }
          hex.ModelMetricsBinomialGLM mmTrain = (ModelMetricsBinomialGLM)hex.ModelMetricsBinomial.getFromDKV(model, fTrain);
          hex.AUC2 adata = mmTrain._auc;
          assertEquals(model._output._training_metrics.auc()._auc, adata._auc, 1e-8);
          assertEquals(model._output._training_metrics._MSE, mmTrain._MSE, 1e-8);
          assertEquals(((ModelMetricsBinomialGLM) model._output._training_metrics)._resDev, mmTrain._resDev, 1e-8);
          scoreTrain = model.score(fTrain);
          mmTrain = (ModelMetricsBinomialGLM)hex.ModelMetricsBinomial.getFromDKV(model, fTrain);
          adata = mmTrain._auc;
          assertEquals(model._output._training_metrics.auc()._auc, adata._auc, 1e-8);
          assertEquals(model._output._training_metrics._MSE, mmTrain._MSE, 1e-8);
          assertEquals(((ModelMetricsBinomialGLM) model._output._training_metrics)._resDev, mmTrain._resDev, 1e-8);
          scoreTest = model.score(fTest);
          ModelMetricsBinomialGLM mmTest = (ModelMetricsBinomialGLM)hex.ModelMetricsBinomial.getFromDKV(model, fTest);
          adata = mmTest._auc;
          assertEquals(model._output._validation_metrics.auc()._auc, adata._auc, 1e-8);
          assertEquals(model._output._validation_metrics._MSE, mmTest._MSE, 1e-8);
          assertEquals(((ModelMetricsBinomialGLM)model._output._validation_metrics)._resDev, mmTest._resDev, 1e-8);
          // test the actual predictions
          Vec preds = scoreTest.vec("p1");
          for(int i = 0; i < pred_test.length; ++i)
            assertEquals(pred_test[i],preds.at(i),1e-6);
        } finally {
          if (model != null) model.delete();
          if (scoreTrain != null) scoreTrain.delete();
          if (scoreTest != null) scoreTest.delete();
          if (job != null) job.remove();
        }
      }
    } finally {
      if (fTrain != null) fTrain.delete();
      if(fTest != null)fTest.delete();

    }
  }


  @Test
  public void testNoIntercept() {
    GLM job = null;
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
    params._max_iterations = 100; // not expected to reach max iterations here
    for(Solver s:new Solver[]{Solver.AUTO,Solver.IRLSM,Solver.L_BFGS}) {
      Frame scoreTrain = null, scoreTest = null;
      try {
        params._solver = s;
        System.out.println("SOLVER = " + s);
        job = new GLM(Key.make("prostate_model"), "glm test simple poisson", params);
        model = job.trainModel().get();
        HashMap<String, Double> coefs = model.coefficients();
        System.out.println("coefs = " + coefs.toString());
        System.out.println("metrics = " + model._output._training_metrics);
        for (int i = 0; i < cfs1.length; ++i)
          assertEquals(vals[i], coefs.get(cfs1[i]), 1e-4);
        assertEquals(402,   GLMTest.nullDeviance(model), 1e-1);
        assertEquals(302.9, GLMTest.residualDeviance(model), 1e-1);
        assertEquals(290,   GLMTest.nullDOF(model), 0);
        assertEquals(282,   GLMTest.resDOF(model), 0);
        assertEquals(318.9, GLMTest.aic(model), 1e-1);
        System.out.println("VAL METRICS: " + model._output._validation_metrics);
        // compare validation res dev matches R
        // sum(binomial()$dev.resids(y=test$CAPSULE,mu=p,wt=1))
        // [1]80.92923
        assertEquals(80.92923, GLMTest.residualDevianceTest(model), 1e-4);
//      compare validation null dev against R
//      sum(binomial()$dev.resids(y=test$CAPSULE,mu=.5,wt=1))
//      [1] 124.7665
        assertEquals(124.7665, GLMTest.nullDevianceTest(model),1e-4);
        model.delete();
        // test scoring
        scoreTrain = model.score(_prostateTrain);
        hex.ModelMetricsBinomial mm = hex.ModelMetricsBinomial.getFromDKV(model, _prostateTrain);
        hex.AUC2 adata = mm._auc;
        assertEquals(model._output._training_metrics.auc()._auc, adata._auc, 1e-8);
        assertEquals(model._output._training_metrics._MSE, mm._MSE, 1e-8);
        assertEquals(((ModelMetricsBinomialGLM) model._output._training_metrics)._resDev, ((ModelMetricsBinomialGLM) mm)._resDev, 1e-8);
        scoreTest = model.score(_prostateTest);
        mm = hex.ModelMetricsBinomial.getFromDKV(model, _prostateTest);
        adata = mm._auc;
        assertEquals(model._output._validation_metrics.auc()._auc, adata._auc, 1e-8);
        assertEquals(model._output._validation_metrics._MSE, mm._MSE, 1e-8);
        assertEquals(((ModelMetricsBinomialGLM) model._output._validation_metrics)._resDev, ((ModelMetricsBinomialGLM) mm)._resDev, 1e-8);

      } finally {
        if (model != null) model.delete();
        if (scoreTrain != null) scoreTrain.delete();
        if(scoreTest != null) scoreTest.delete();
        if (job != null) job.remove();
      }
    }
  }


  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
    File f = find_test_file_static("smalldata/glm_test/prostate_cat_train.csv");
    assert f.exists();

    NFSFileVec nfs = NFSFileVec.make(f);
    Key outputKey = Key.make("prostate_cat_train.hex");
    _prostateTrain = ParseDataset.parse(outputKey, nfs._key);

    f = find_test_file_static("smalldata/glm_test/prostate_cat_test.csv");
    assert f.exists();

    nfs = NFSFileVec.make(f);
    outputKey = Key.make("prostate_cat_test.hex");
    _prostateTest = ParseDataset.parse(outputKey, nfs._key);
  }

  @AfterClass
  public static void cleanUp() {
    if(_prostateTrain != null)
      _prostateTrain.delete();
    if(_prostateTest != null)
      _prostateTest.delete();
  }
}
