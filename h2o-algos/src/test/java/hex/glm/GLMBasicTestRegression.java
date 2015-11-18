package hex.glm;

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
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.parser.ParseDataset;

import java.io.File;
import java.util.HashMap;

import water.fvec.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
  static Frame _prostateTrain;

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
    File f = find_test_file_static("smalldata/glm_test/cancar_logIn.csv");
    assert f.exists();
    NFSFileVec nfs = NFSFileVec.make(f);
    Key outputKey = Key.make("prostate_cat_train.hex");
    _canCarTrain = ParseDataset.parse(outputKey, nfs._key);
    _canCarTrain.add("Merit", (_merit = _canCarTrain.remove("Merit")).toCategoricalVec());
    _canCarTrain.add("Class",(_class = _canCarTrain.remove("Class")).toCategoricalVec());

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
    _prostateTrain = parse_test_file("smalldata/glm_test/prostate_cat_train.csv");
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

  @Test
  public void testPValuesTweedie() {
//    Call:
//    glm(formula = Infections ~ ., family = tweedie(var.power = 1.5),
//      data = D)
//
//    Deviance Residuals:
//    Min       1Q   Median       3Q      Max
//    -2.6355  -2.0931  -1.8183   0.5046   4.9458
//
//    Coefficients:
//    Estimate Std. Error t value Pr(>|t|)
//    (Intercept)       1.05665    0.11120   9.502  < 2e-16 ***
//    SwimmerOccas     -0.25891    0.08455  -3.062  0.00241 **
//    LocationNonBeach -0.22185    0.08393  -2.643  0.00867 **
//    Age20-24          0.15325    0.10041   1.526  0.12808
//    Age25-29          0.07624    0.10099   0.755  0.45096
//    SexMale           0.03908    0.08619   0.453  0.65058
//      ---
//      Signif. codes:  0 ‘***’ 0.001 ‘**’ 0.01 ‘*’ 0.05 ‘.’ 0.1 ‘ ’ 1
//
//    (Dispersion parameter for Tweedie family taken to be 2.896306)
//
//    Null deviance: 967.05  on 286  degrees of freedom
//    Residual deviance: 908.86  on 281  degrees of freedom
//    AIC: NA
//
//    Number of Fisher Scoring iterations: 7
    double [] sderr_exp = new double[]{ 0.11120211,       0.08454967,       0.08393315,       0.10041150,       0.10099231,      0.08618960};
    double [] zvals_exp = new double[]{ 9.5021062,       -3.0622693,       -2.6431794,       1.5262357,        0.7548661,        0.4534433};
    double [] pvals_exp = new double[]{ 9.508400e-19,     2.409514e-03,     8.674149e-03,     1.280759e-01,     4.509615e-01,     6.505795e-01 };

    GLMParameters parms = new GLMParameters(Family.tweedie);
    parms._tweedie_variance_power = 1.5;
    parms._tweedie_link_power = 1 - parms._tweedie_variance_power;
    parms._train = _earinf._key;
    parms._standardize = false;
    parms._lambda = new double[]{0};
    parms._alpha = new double[]{0};
    parms._response_column = "Infections";
    parms._compute_p_values = true;

    GLM job = new GLM(Key.make("prostate_model"), "glm test p-values", parms);
    GLMModel model = null;
    try {
      model = job.trainModel().get();
      String[] names_expected = new String[]{"Intercept", "Swimmer.Occas", "Location.NonBeach", "Age.20-24", "Age.25-29", "Sex.Male"};
      String[] names_actual = model._output.coefficientNames();
      HashMap<String, Integer> coefMap = new HashMap<>();
      for (int i = 0; i < names_expected.length; ++i)
        coefMap.put(names_expected[i], i);
      double[] stder_actual = model._output.stdErr();
      double[] zvals_actual = model._output.zValues();
      double[] pvals_actual = model._output.pValues();
      for (int i = 0; i < sderr_exp.length; ++i) {
        int id = coefMap.get(names_actual[i]);
        assertEquals(sderr_exp[id], stder_actual[i], sderr_exp[id] * 1e-3);
        assertEquals(zvals_exp[id], zvals_actual[i], Math.abs(zvals_exp[id]) * 1e-3);
        assertEquals(pvals_exp[id], pvals_actual[i], Math.max(1e-8,pvals_exp[id]) * 5e-3);
      }
    } finally {
      if(model != null) model.delete();
      if(job != null) job.remove();
    }
  }

  @Test
  public void testPValuesPoisson() {
//    Coefficients:
//    Estimate Std. Error z value Pr(>|z|)
//    (Intercept) -1.279e+00  3.481e-01  -3.673 0.000239 ***
//    Merit1      -1.498e-01  2.972e-02  -5.040 4.64e-07 ***
//    Merit2      -2.364e-01  3.859e-02  -6.127 8.96e-10 ***
//    Merit3      -3.197e-01  5.095e-02  -6.274 3.52e-10 ***
//    Class2       6.899e-02  8.006e-02   0.862 0.388785
//    Class3       2.892e-01  6.333e-02   4.566 4.97e-06 ***
//    Class4       2.708e-01  4.911e-02   5.515 3.49e-08 ***
//    Class5      -4.468e-02  1.048e-01  -0.427 0.669732
//    Insured      1.617e-06  5.069e-07   3.191 0.001420 **
//    Premium     -3.630e-05  1.087e-05  -3.339 0.000840 ***
//    Cost         2.021e-05  6.869e-06   2.943 0.003252 **
//    logInsured   9.390e-01  2.622e-02  35.806  < 2e-16 ***
//    ---
//      Signif. codes:  0 ‘***’ 0.001 ‘**’ 0.01 ‘*’ 0.05 ‘.’ 0.1 ‘ ’ 1
//
//    (Dispersion parameter for poisson family taken to be 1)
//
//    Null deviance: 961181.685  on 19  degrees of freedom
//    Residual deviance:     42.671  on  8  degrees of freedom
//    AIC: 277.08

    double [] sderr_exp = new double[]{ 3.480733e-01, 2.972063e-02, 3.858825e-02, 5.095260e-02,8.005579e-02, 6.332867e-02, 4.910690e-02, 1.047531e-01, 5.068602e-07, 1.086939e-05, 6.869142e-06 ,2.622370e-02};
    double [] zvals_exp = new double[]{ -3.6734577,  -5.0404946,  -6.1269397,  -6.2739848,   0.8618220,   4.5662083 ,  5.5148904,  -0.4265158 ,  3.1906387,  -3.3392867,   2.9428291,  35.8061272  };
    double [] pvals_exp = new double[]{ 2.392903e-04,  4.643302e-07,  8.958540e-10 , 3.519228e-10,  3.887855e-01 , 4.966252e-06,  3.489974e-08 , 6.697321e-01 , 1.419587e-03,  8.399383e-04,  3.252279e-03, 8.867127e-281};

    GLMParameters parms = new GLMParameters(Family.poisson);
    parms._train = _canCarTrain._key;
    parms._standardize = false;
    parms._lambda = new double[]{0};
    parms._alpha = new double[]{0};
    parms._response_column = "Claims";
    parms._compute_p_values = true;

    GLM job = new GLM(Key.make("prostate_model"), "glm test p-values", parms);
    GLMModel model = null;
    try {
      model = job.trainModel().get();
      String[] names_expected = new String[]{"Intercept",  "Merit.1", "Merit.2", "Merit.3", "Class.2", "Class.3", "Class.4", "Class.5","Insured","Premium", "Cost", "logInsured" };
      String[] names_actual = model._output.coefficientNames();
      HashMap<String, Integer> coefMap = new HashMap<>();
      for (int i = 0; i < names_expected.length; ++i)
        coefMap.put(names_expected[i], i);
      double[] stder_actual = model._output.stdErr();
      double[] zvals_actual = model._output.zValues();
      double[] pvals_actual = model._output.pValues();
      for (int i = 0; i < sderr_exp.length; ++i) {
        int id = coefMap.get(names_actual[i]);
        assertEquals(sderr_exp[id], stder_actual[i], sderr_exp[id] * 1e-4);
        assertEquals(zvals_exp[id], zvals_actual[i], Math.abs(zvals_exp[id]) * 1e-4);
        assertEquals(pvals_exp[id], pvals_actual[i], Math.max(1e-15,pvals_exp[id] * 1e-3));
      }
    } finally {
      if(model != null) model.delete();
      if(job != null) job.remove();
    }
  }

  @Test
  public void testPValuesGaussian(){
//    1) NON-STANDARDIZED

//    summary(m)
//
//    Call:
//    glm(formula = CAPSULE ~ ., family = gaussian, data = D)
//
//    Deviance Residuals:
//    Min       1Q   Median       3Q      Max
//    -0.8394  -0.3162  -0.1113   0.3771   0.9447
//
//    Coefficients:
//    Estimate Std. Error t value Pr(>|t|)
//    (Intercept) -0.6870832  0.4035941  -1.702  0.08980 .
//      ID         0.0003081  0.0002387   1.291  0.19791
//    AGE         -0.0006005  0.0040246  -0.149  0.88150
//    RACER2      -0.0147733  0.2511007  -0.059  0.95313
//    RACER3      -0.1456993  0.2593492  -0.562  0.57471
//    DPROSb       0.1462512  0.0657117   2.226  0.02684 *
//      DPROSc       0.2297207  0.0713659   3.219  0.00144 **
//    DPROSd       0.1144974  0.0937208   1.222  0.22286
//    DCAPSb       0.1430945  0.0888124   1.611  0.10827
//    PSA          0.0047237  0.0015060   3.137  0.00189 **
//    VOL         -0.0019401  0.0013920  -1.394  0.16449
//    GLEASON      0.1438776  0.0273259   5.265 2.81e-07 ***
//    ---
//      Signif. codes:  0 ‘***’ 0.001 ‘**’ 0.01 ‘*’ 0.05 ‘.’ 0.1 ‘ ’ 1
//
//    (Dispersion parameter for gaussian family taken to be 0.1823264)
//
//    Null deviance: 69.600  on 289  degrees of freedom
//    Residual deviance: 50.687  on 278  degrees of freedom
//    AIC: 343.16
//
//    Number of Fisher Scoring iterations: 2
    GLMParameters params = new GLMParameters(Family.gaussian);
    params._response_column = "CAPSULE";
    params._standardize = false;
    params._train = _prostateTrain._key;
    params._compute_p_values = true;
    params._lambda = new double[]{0};
    GLM job0 = null;
    try {
      job0 = new GLM(Key.make("prostate_model"), "glm test p-values", params);
      params._solver = Solver.L_BFGS;
      GLMModel model = job0.trainModel().get();
      assertFalse("should've thrown, p-values only supported with IRLSM",true);
    } catch(H2OModelBuilderIllegalArgumentException t) {
      if(job0 != null)
        job0.remove();
    }
    try {
      job0 = new GLM(Key.make("prostate_model"), "glm test p-values", params);
      params._solver = Solver.COORDINATE_DESCENT_NAIVE;
      GLMModel model = job0.trainModel().get();
      assertFalse("should've thrown, p-values only supported with IRLSM",true);
    } catch(H2OModelBuilderIllegalArgumentException t) {
      if(job0 != null)
        job0.remove();
    }
    try {
      job0 = new GLM(Key.make("prostate_model"), "glm test p-values", params);
      params._solver = Solver.COORDINATE_DESCENT;
      GLMModel model = job0.trainModel().get();
      assertFalse("should've thrown, p-values only supported with IRLSM",true);
    } catch(H2OModelBuilderIllegalArgumentException t) {
      if(job0 != null)
        job0.remove();
    }
    params._solver = Solver.IRLSM;
    try {
      job0 = new GLM(Key.make("prostate_model"), "glm test p-values", params);
      params._lambda = new double[]{1};
      GLMModel model = job0.trainModel().get();
      assertFalse("should've thrown, p-values only supported with no regularization",true);
    } catch(H2OModelBuilderIllegalArgumentException t) {
      if(job0 != null)
        job0.remove();
    }
    params._lambda = new double[]{0};
    try {
      params._lambda_search = true;
      GLMModel model = job0.trainModel().get();
      assertFalse("should've thrown, p-values only supported with no regularization (i.e. no lambda search)",true);
    } catch(H2OModelBuilderIllegalArgumentException t) {
      if(job0 != null)
        job0.remove();
    }
    params._lambda_search = false;
    GLM job = new GLM(Key.make("prostate_model"), "glm test p-values", params);
    GLMModel model = null;
    try {

      model = job.trainModel().get();
      String[] names_expected = new String[]{"Intercept", "ID", "AGE", "RACE.R2", "RACE.R3", "DPROS.b", "DPROS.c", "DPROS.d", "DCAPS.b", "PSA", "VOL", "GLEASON"};
      double[] stder_expected = new double[]{0.4035941476, 0.0002387281, 0.0040245520, 0.2511007120, 0.2593492335, 0.0657117271, 0.0713659021, 0.0937207659, 0.0888124376, 0.0015060289, 0.0013919737, 0.0273258788};
      double[] zvals_expected = new double[]{-1.70241133,  1.29061005, -0.14920829, -0.05883397, -0.56178799,  2.22564893,  3.21891333,  1.22168646, 1.61119882,  3.13650800, -1.39379859,  5.26524961 };
      double[] pvals_expected = new double[]{8.979610e-02, 1.979113e-01, 8.814975e-01, 9.531266e-01, 5.747131e-01, 2.683977e-02, 1.439295e-03, 2.228612e-01, 1.082711e-01, 1.893210e-03, 1.644916e-01, 2.805776e-07};
      String[] names_actual = model._output.coefficientNames();
      HashMap<String, Integer> coefMap = new HashMap<>();
      for (int i = 0; i < names_expected.length; ++i)
        coefMap.put(names_expected[i], i);
      double[] stder_actual = model._output.stdErr();
      double[] zvals_actual = model._output.zValues();
      double[] pvals_actual = model._output.pValues();
      for (int i = 0; i < stder_expected.length; ++i) {
        int id = coefMap.get(names_actual[i]);
        assertEquals(stder_expected[id], stder_actual[i], stder_expected[id] * 1e-5);
        assertEquals(zvals_expected[id], zvals_actual[i], Math.abs(zvals_expected[id]) * 1e-5);
        assertEquals(pvals_expected[id], pvals_actual[i], pvals_expected[id] * 1e-3);
      }
    } finally {
      if(model != null) model.delete();
      if(job != null) job.remove();
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
//      Signif. codes:  0 ‘***’ 0.001 ‘**’ 0.01 ‘*’ 0.05 ‘.’ 0.1 ‘ ’ 1
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

    job = new GLM(Key.make("prostate_model"), "glm test p-values", params);
    try {
      model = job.trainModel().get();
      String[] names_expected = new String[]{"Intercept", "ID", "AGE", "RACE.R2", "RACE.R3", "DPROS.b", "DPROS.c", "DPROS.d", "DCAPS.b", "PSA", "VOL", "GLEASON"};
      // do not compare std_err here, depends on the coefficients
//      double[] stder_expected = new double[]{1.5687858,   0.1534062,   0.1449847,   1.5423974, 1.5827190,   0.3950883,   0.4161974,  0.5426512,   0.5179591,   0.2244733, 0.1620383,   0.1963285};
      double[] zvals_expected = new double[]{1.14158283,  1.29061005, -0.14920829, -0.05883397, -0.56178799, 2.22564893,  3.21891333,  1.22168646,  1.61119882,  3.13650800, -1.39379859,  5.26524961 };
      double[] pvals_expected = new double[]{2.546098e-01, 1.979113e-01, 8.814975e-01, 9.531266e-01, 5.747131e-01, 2.683977e-02, 1.439295e-03, 2.228612e-01, 1.082711e-01, 1.893210e-03, 1.644916e-01, 2.805776e-07 };
      String[] names_actual = model._output.coefficientNames();
      HashMap<String, Integer> coefMap = new HashMap<>();
      for (int i = 0; i < names_expected.length; ++i)
        coefMap.put(names_expected[i], i);
      double[] zvals_actual = model._output.zValues();
      double[] pvals_actual = model._output.pValues();
      for (int i = 0; i < zvals_expected.length; ++i) {
        int id = coefMap.get(names_actual[i]);
        assertEquals(zvals_expected[id], zvals_actual[i], Math.abs(zvals_expected[id]) * 1e-5);
        assertEquals(pvals_expected[id], pvals_actual[i], pvals_expected[id] * 1e-3);
      }
    } finally {
      if(model != null) model.delete();
      if(job != null) job.remove();
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
    if(_prostateTrain != null)
      _prostateTrain.delete();
  }
}
