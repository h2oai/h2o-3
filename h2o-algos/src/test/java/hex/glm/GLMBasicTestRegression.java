package hex.glm;

import hex.ModelMetricsBinomialGLM;
import hex.ModelMetricsRegressionGLM;
import hex.glm.GLMModel.GLMParameters;
import hex.glm.GLMModel.GLMParameters.Family;
import hex.glm.GLMModel.GLMParameters.Solver;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.parser.ParseDataset;

import java.io.File;
import java.util.HashMap;

import water.fvec.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by tomasnykodym on 6/4/15.
 */
public class GLMBasicTestRegression extends TestUtil {
  static Frame _canCarTrain;
  static Frame _earinf;
  static Frame _weighted;
  static Frame _upsampled;
  static Vec _merit, _class;

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
    File f = find_test_file_static("smalldata/glm_test/cancar_logIn.csv");
    assert f.exists();
    NFSFileVec nfs = NFSFileVec.make(f);
    Key outputKey = Key.make("prostate_cat_train.hex");
    _canCarTrain = ParseDataset.parse(outputKey, nfs._key);
    _canCarTrain.add("Merit", (_merit = _canCarTrain.remove("Merit")).toEnum());
    _canCarTrain.add("Class",(_class = _canCarTrain.remove("Class")).toEnum());

    DKV.put(_canCarTrain._key, _canCarTrain);
    f = find_test_file_static("smalldata/glm_test/earinf.txt");
    assert f.exists();
    nfs = NFSFileVec.make(f);
    outputKey = Key.make("earinf.hex");
    _earinf = ParseDataset.parse(outputKey, nfs._key);
    DKV.put(_earinf._key,_earinf);

    f = find_test_file_static("smalldata/glm_test/weighted.csv");
    assert f.exists();
    nfs = NFSFileVec.make(f);
    outputKey = Key.make("weighted.hex");
    _weighted = ParseDataset.parse(outputKey, nfs._key);
    DKV.put(_weighted._key,_weighted);

    f = find_test_file_static("smalldata/glm_test/upsampled.csv");
    assert f.exists();
    nfs = NFSFileVec.make(f);
    outputKey = Key.make("upsampled.hex");
    _upsampled = ParseDataset.parse(outputKey, nfs._key);
    DKV.put(_upsampled._key,_upsampled);
  }

  @Test public void  testWeights() {
    GLM job1 = null, job2 = null;
    GLMModel model1 = null, model2 = null;
    GLMParameters parms = new GLMParameters(Family.gaussian);
    parms._train = _weighted._key;
    parms._ignored_columns = new String[]{_weighted.name(0)};
    parms._response_column = _weighted.name(1);
    parms._standardize = true;
    parms._objective_epsilon = 0;
    parms._gradient_epsilon = 1e-10;
    parms._max_iterations = 1000;
    for (Solver s : GLMParameters.Solver.values()) {
//      if(s != Solver.IRLSM)continue; //fixme: does not pass for other than IRLSM now
      System.out.println("===============================================================");
      System.out.println("Solver = " + s);
      System.out.println("===============================================================");
      try {
        parms._lambda = null;
        parms._alpha = null;
        parms._train = _weighted._key;
        parms._solver = s;
        parms._weights_column = "weights";
        job1 = new GLM(Key.make("prostate_model"), "glm test", parms);
        model1 = job1.trainModel().get();
        HashMap<String, Double> coefs1 = model1.coefficients();
        System.out.println("coefs1 = " + coefs1);
        parms._train = _upsampled._key;
        parms._weights_column = null;
        parms._lambda = null;
        parms._alpha = null;
        job2 = new GLM(Key.make("prostate_model"), "glm test", parms);
        model2 = job2.trainModel().get();
        HashMap<String, Double> coefs2 = model2.coefficients();
        System.out.println("coefs2 = " + coefs2);
        System.out.println("mse1 = " + model1._output._training_metrics.mse() + ", mse2 = " + model2._output._training_metrics.mse());
        System.out.println( model1._output._training_metrics);
        System.out.println( model2._output._training_metrics);
        assertEquals(model2._output._training_metrics.mse(), model1._output._training_metrics.mse(),1e-6);
      } finally {
        if(job1 != null)job1.remove();
        if(model1 != null) model1.delete();
        if(job2 != null)job2.remove();
        if(model2 != null) model2.delete();
      }
    }
  }

  @Test public void testTweedie() {
    GLM job = null;
    GLMModel model = null;
    Frame scoreTrain = null;

    // --------------------------------------  R examples output ----------------------------------------------------------------

    //    Call:  glm(formula = Infections ~ ., family = tweedie(0), data = D)
//
//    Coefficients:
//    (Intercept)      SwimmerOccas  LocationNonBeach          Age20-24          Age25-29          SexMale
//    0.8910            0.8221                 0.7266           -0.5033           -0.2679         -0.1056
//
//    Degrees of Freedom: 286 Total (i.e. Null);  281 Residual
//    Null Deviance:	    1564
//    Residual Deviance: 1469 	AIC: NA

//    Call:  glm(formula = Infections ~ ., family = tweedie(1), data = D)
//
//    Coefficients:
//    (Intercept)      SwimmerOccas  LocationNonBeach          Age20-24          Age25-29           SexMale
//      -0.12261           0.61149           0.53454          -0.37442          -0.18973          -0.08985
//
//    Degrees of Freedom: 286 Total (i.e. Null);  281 Residual
//    Null Deviance:	    824.5
//    Residual Deviance: 755.4 	AIC: NA

//    Call:  glm(formula = Infections ~ ., family = tweedie(1.25), data = D)
//
//    Coefficients:
//    (Intercept)      SwimmerOccas  LocationNonBeach          Age20-24          Age25-29           SexMale
//    1.02964          -0.14079          -0.12200           0.08502           0.04269           0.02105
//
//    Degrees of Freedom: 286 Total (i.e. Null);  281 Residual
//    Null Deviance:	    834.2
//    Residual Deviance: 770.8 	AIC: NA

//    Call:  glm(formula = Infections ~ ., family = tweedie(1.5), data = D)
//
//    Coefficients:
//    (Intercept)      SwimmerOccas  LocationNonBeach          Age20-24          Age25-29           SexMale
//    1.05665          -0.25891          -0.22185           0.15325           0.07624           0.03908
//
//    Degrees of Freedom: 286 Total (i.e. Null);  281 Residual
//    Null Deviance:	    967
//    Residual Deviance: 908.9 	AIC: NA

//    Call:  glm(formula = Infections ~ ., family = tweedie(1.75), data = D)
//
//    Coefficients:
//    (Intercept)      SwimmerOccas  LocationNonBeach          Age20-24          Age25-29           SexMale
//    1.08076          -0.35690          -0.30154           0.20556           0.10122           0.05375
//
//    Degrees of Freedom: 286 Total (i.e. Null);  281 Residual
//    Null Deviance:	    1518
//    Residual Deviance: 1465 	AIC: NA


//    Call:  glm(formula = Infections ~ ., family = tweedie(2), data = D)
//
//    Coefficients:
//    (Intercept)      SwimmerOccas  LocationNonBeach          Age20-24          Age25-29           SexMale
//    1.10230          -0.43751          -0.36337           0.24318           0.11830           0.06467
//
//    Degrees of Freedom: 286 Total (i.e. Null);  281 Residual
//    Null Deviance:	    964.4
//    Residual Deviance: 915.7 	AIC: NA

    // ---------------------------------------------------------------------------------------------------------------------------

    String [] cfs1   = new String  []  { "Intercept", "Swimmer.Occas", "Location.NonBeach", "Age.20-24", "Age.25-29", "Sex.Male" };
    double [][] vals = new double[][] {{     0.89100,         0.82210,             0.72660,    -0.50330,    -0.26790,   -0.10560 },
                                       {    -0.12261,         0.61149,             0.53454,    -0.37442,    -0.18973,   -0.08985 },
                                       {     1.02964,        -0.14079,            -0.12200,     0.08502,     0.04269,    0.02105 },
                                       {     1.05665,        -0.25891,            -0.22185,     0.15325,     0.07624,    0.03908 },
                                       {     1.08076,        -0.35690,            -0.30154,     0.20556,     0.10122,    0.05375 },
                                       {     1.10230,        -0.43751,            -0.36337,     0.24318,     0.11830,    0.06467 },
    };
    int dof = 286, res_dof = 281;
    double [] nullDev = new double[]{1564,824.5,834.2,967.0,1518,964.4};
    double [] resDev  = new double[]{1469,755.4,770.8,908.9,1465,915.7};
    double [] varPow  = new double[]{   0,  1.0, 1.25,  1.5,1.75,  2.0};

    GLMParameters parms = new GLMParameters(Family.tweedie);
    parms._train = _earinf._key;
    parms._ignored_columns = new String[]{};
    // "response_column":"Claims","offset_column":"logInsured"
    parms._response_column = "Infections";
    parms._standardize = false;
    parms._lambda = new double[]{0};
    parms._alpha = new double[]{0};
    parms._objective_epsilon = 0;
    parms._gradient_epsilon = 1e-10;
    parms._max_iterations = 1000;
    for(int x = 0; x < varPow.length; ++x) {
      double p = varPow[x];
      parms._tweedie_variance_power = p;
      parms._tweedie_link_power = 1 - p;
      for (Solver s : /*new Solver[]{Solver.IRLSM}*/ GLMParameters.Solver.values()) {
        try {
          parms._solver = s;
          job = new GLM(Key.make("prostate_model"), "glm test simple poisson", parms);
          model = job.trainModel().get();
          HashMap<String, Double> coefs = model.coefficients();
          System.out.println("coefs = " + coefs);
          for (int i = 0; i < cfs1.length; ++i)
            assertEquals(vals[x][i], coefs.get(cfs1[i]), 1e-4);
          assertEquals(nullDev[x], (GLMTest.nullDeviance(model)), 5e-4*nullDev[x]);
          assertEquals(resDev[x],  (GLMTest.residualDeviance(model)), 5e-4*resDev[x]);
          assertEquals(dof, GLMTest.nullDOF(model), 0);
          assertEquals(res_dof, GLMTest.resDOF(model), 0);
          // test scoring
          scoreTrain = model.score(_earinf);
          hex.ModelMetricsRegressionGLM mmTrain = (ModelMetricsRegressionGLM) hex.ModelMetricsRegression.getFromDKV(model, _earinf);
          assertEquals(model._output._training_metrics._MSE, mmTrain._MSE, 1e-8);
          assertEquals(GLMTest.residualDeviance(model), mmTrain._resDev, 1e-8);
          assertEquals(GLMTest.nullDeviance(model), mmTrain._nullDev, 1e-8);
        } finally {
          if (job != null) job.remove();
          if (model != null) model.delete();
          if (scoreTrain != null) scoreTrain.delete();
        }
      }
    }
  }

  @Test
  public void testPoissonWithOffset(){
    GLM job = null;
    GLMModel model = null;
    Frame scoreTrain = null;

//    Call:  glm(formula = formula, family = poisson, data = D)
//
//    Coefficients:
//    (Intercept)       Merit1       Merit2       Merit3       Class2       Class3       Class4       Class5
//    -2.0357          -0.1378      -0.2207      -0.4930       0.2998       0.4691       0.5259       0.2156
//
//    Degrees of Freedom: 19 Total (i.e. Null);  12 Residual
//    Null Deviance:	    33850
//    Residual Deviance: 579.5 	AIC: 805.9
    String [] cfs1 = new String [] { "Intercept", "Merit.1", "Merit.2", "Merit.3", "Class.2", "Class.3", "Class.4", "Class.5"};
    double [] vals = new double [] { -2.0357,     -0.1378,  -0.2207,  -0.4930,   0.2998,   0.4691,   0.5259,    0.2156};
      GLMParameters parms = new GLMParameters(Family.poisson);
      parms._train = _canCarTrain._key;
      parms._ignored_columns = new String[]{"Insured", "Premium", "Cost"};
      // "response_column":"Claims","offset_column":"logInsured"
      parms._response_column = "Claims";
      parms._offset_column = "logInsured";
      parms._standardize = false;
      parms._lambda = new double[]{0};
      parms._alpha = new double[]{0};
      parms._objective_epsilon = 0;
      parms._gradient_epsilon = 1e-10;
      parms._max_iterations = 1000;
      for (Solver s : GLMParameters.Solver.values()) {
        try {
          parms._solver = s;
          job = new GLM(Key.make("prostate_model"), "glm test simple poisson", parms);
          model = job.trainModel().get();
          HashMap<String, Double> coefs = model.coefficients();
          System.out.println("coefs = " + coefs);
          for (int i = 0; i < cfs1.length; ++i)
            assertEquals(vals[i], coefs.get(cfs1[i]), 1e-4);
          assertEquals(33850, GLMTest.nullDeviance(model), 5);
          assertEquals(579.5, GLMTest.residualDeviance(model), 1e-4*579.5);
          assertEquals(19,   GLMTest.nullDOF(model), 0);
          assertEquals(12,   GLMTest.resDOF(model), 0);
          assertEquals(805.9, GLMTest.aic(model), 1e-4*805.9);
          // test scoring
          try {
            Frame fr = new Frame(_canCarTrain.names(),_canCarTrain.vecs());
            fr.remove(parms._offset_column);
            scoreTrain = model.score(fr);
            assertTrue("shoul've thrown IAE", false);
          } catch (IllegalArgumentException iae) {
            assertTrue(iae.getMessage().contains("Test/Validation dataset is missing offset vector"));
          }
          scoreTrain = model.score(_canCarTrain);
          hex.ModelMetricsRegressionGLM mmTrain = (ModelMetricsRegressionGLM)hex.ModelMetricsRegression.getFromDKV(model, _canCarTrain);
          assertEquals(model._output._training_metrics._MSE, mmTrain._MSE, 1e-8);
          assertEquals(GLMTest.residualDeviance(model), mmTrain._resDev, 1e-8);
          assertEquals(GLMTest.nullDeviance(model), mmTrain._nullDev, 1e-8);
        } finally {
          if(job != null)job.remove();
          if(model != null) model.delete();
          if(scoreTrain != null) scoreTrain.delete();
        }
      }
  }
  @AfterClass
  public static void cleanUp() {
    if(_canCarTrain != null)
      _canCarTrain.delete();
    if(_merit != null)
      _merit.remove();
    if(_class != null)
      _class.remove();
    if(_earinf != null)
      _earinf.delete();
    if(_weighted != null)
      _weighted.delete();
    if(_upsampled != null)
      _upsampled.delete();
  }
}
