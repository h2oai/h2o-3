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
  static Frame _airlines;

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
    _airlines = parse_test_file("smalldata/airlines/AirlinesTrain.csv.zip");
  }


  @Test public void  testWeights() {
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
        parms._lambda = new double[]{1e-5};
        parms._alpha = null;
        parms._train = _weighted._key;
        parms._solver = s;
        parms._weights_column = "weights";
        model1 = new GLM(parms).trainModel().get();
        HashMap<String, Double> coefs1 = model1.coefficients();
        System.out.println("coefs1 = " + coefs1);
        parms._train = _upsampled._key;
        parms._weights_column = null;
        parms._lambda = new double[]{1e-5};
        parms._alpha = null;
        model2 = new GLM(parms).trainModel().get();
        HashMap<String, Double> coefs2 = model2.coefficients();
        System.out.println("coefs2 = " + coefs2);
        System.out.println("mse1 = " + model1._output._training_metrics.mse() + ", mse2 = " + model2._output._training_metrics.mse());
        System.out.println( model1._output._training_metrics);
        System.out.println( model2._output._training_metrics);
        assertEquals(model2._output._training_metrics.mse(), model1._output._training_metrics.mse(),1e-4);
      } finally {
        if(model1 != null) model1.delete();
        if(model2 != null) model2.delete();
      }
    }
  }

  @Test public void  testOffset() {
    GLMModel model1 = null, model2 = null;
    GLMParameters parms = new GLMParameters(Family.gaussian);
    parms._train = _weighted._key;
    parms._ignored_columns = new String[]{_weighted.name(0)};
    parms._response_column = _weighted.name(1);
    parms._standardize = true;
    parms._objective_epsilon = 0;
    parms._gradient_epsilon = 1e-10;
    parms._max_iterations = 1000;
    Solver s = Solver.IRLSM;
    try {
      parms._lambda = new double[]{0};
      parms._alpha = new double []{0};
      parms._train = _weighted._key;
      parms._solver = s;
      parms._offset_column = "C20";
      parms._compute_p_values = true;
      parms._standardize = false;
      model1 = new GLM(parms).trainModel().get();
      HashMap<String, Double> coefs1 = model1.coefficients();
      System.out.println("coefs1 = " + coefs1);
      /**
       * Call:
       glm(formula = C2 ~ . - C1 - C20, data = data, offset = data$C20)

       Deviance Residuals:
       Min      1Q  Median      3Q     Max
       -3.444  -0.821  -0.021   0.878   2.801

       Coefficients:
       Estimate Std. Error t value Pr(>|t|)
       (Intercept) -0.026928   0.479281  -0.056   0.9553
       C3          -0.064657   0.144517  -0.447   0.6558
       C4          -0.076132   0.163746  -0.465   0.6432
       C5           0.397962   0.161458   2.465   0.0158 *
       C6           0.119644   0.173165   0.691   0.4916
       C7          -0.124615   0.151145  -0.824   0.4121
       C8           0.142455   0.164912   0.864   0.3902
       C9           0.087358   0.158266   0.552   0.5825
       C10         -0.012873   0.155429  -0.083   0.9342
       C11          0.277392   0.181299   1.530   0.1299
       C12          0.004988   0.170290   0.029   0.9767
       C13         -0.091400   0.172910  -0.529   0.5985
       C14         -0.248876   0.177311  -1.404   0.1643
       C15          0.053598   0.167305   0.320   0.7495
       C16          0.156302   0.157823   0.990   0.3249
       C17          0.296317   0.167453   1.770   0.0806 .
       C18          0.013306   0.162185   0.082   0.9348
       C19          0.115939   0.160250   0.723   0.4715
       weights     -0.005771   0.303477  -0.019   0.9849
       *
       */

      double [] expected_coefs = new double[]{-0.064656782,-0.076131880,0.397962147,0.119644094,-0.124614842,0.142455018,0.087357855,-0.012872522,0.277392182,0.004987961,-0.091400128,-0.248875970
        ,0.053597896,0.156301780,0.296317472,0.013306398,0.115938809,-0.005771429,-0.026928297};

      double [] expected_pvals = new double[]{0.65578062,0.64322317,0.01582348,0.49158786,0.41209217,0.39023637,0.58248959,0.93419972,0.12990598,0.97670462,0.59852911,0.16425679,0.74951951,0.32494727
        ,0.08056447,0.93481349,0.47146503,0.98487376,0.95533301};
      double [] actual_coefs = model1.beta();
      double [] actual_pvals = model1._output.pValues();
      for(int i = 0; i < expected_coefs.length; ++i) {
        assertEquals(expected_coefs[i], actual_coefs[i],1e-4);
        assertEquals(expected_pvals[i], actual_pvals[i],1e-4);
      }
    } finally {
      if (model1 != null) model1.delete();
      if (model2 != null) model2.delete();
    }
  }

  @Test public void testTweedie() {
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
          model = new GLM(parms).trainModel().get();
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
          assertTrue(model.testJavaScoring(_earinf,scoreTrain,1e-8));
          hex.ModelMetricsRegressionGLM mmTrain = (ModelMetricsRegressionGLM) hex.ModelMetricsRegression.getFromDKV(model, _earinf);
          assertEquals(model._output._training_metrics._MSE, mmTrain._MSE, 1e-8);
          assertEquals(GLMTest.residualDeviance(model), mmTrain._resDev, 1e-8);
          assertEquals(GLMTest.nullDeviance(model), mmTrain._nullDev, 1e-8);
        } finally {
          if (model != null) model.delete();
          if (scoreTrain != null) scoreTrain.delete();
        }
      }
    }
  }


  @Test
  public void testPoissonWithOffset(){
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
          model = new GLM(parms).trainModel().get();
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
//      Signif. codes:  0 '***' 0.001 '**' 0.01 '*' 0.05 '.' 0.1 ' ' 1
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
    parms._objective_epsilon = 0;

    GLMModel model = null;
    try {
      model = new GLM(parms).trainModel().get();
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
//      Signif. codes:  0 '***' 0.001 '**' 0.01 '*' 0.05 '.' 0.1 ' ' 1
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

    GLMModel model = null;
    try {
      model = new GLM(parms).trainModel().get();
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
//    DPROSc       0.2297207  0.0713659   3.219  0.00144 **
//    DPROSd       0.1144974  0.0937208   1.222  0.22286
//    DCAPSb       0.1430945  0.0888124   1.611  0.10827
//    PSA          0.0047237  0.0015060   3.137  0.00189 **
//    VOL         -0.0019401  0.0013920  -1.394  0.16449
//    GLEASON      0.1438776  0.0273259   5.265 2.81e-07 ***
//    ---
//      Signif. codes:  0 '***' 0.001 '**' 0.01 '*' 0.05 '.' 0.1 ' ' 1
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
    try {
      params._solver = Solver.L_BFGS;
      new GLM(params).trainModel().get();
      assertFalse("should've thrown, p-values only supported with IRLSM",true);
    } catch(H2OModelBuilderIllegalArgumentException t) {
    }
    try {
      params._solver = Solver.COORDINATE_DESCENT_NAIVE;
      new GLM(params).trainModel().get();
      assertFalse("should've thrown, p-values only supported with IRLSM",true);
    } catch(H2OModelBuilderIllegalArgumentException t) {
    }
    try {
      params._solver = Solver.COORDINATE_DESCENT;
      new GLM(params).trainModel().get();
      assertFalse("should've thrown, p-values only supported with IRLSM",true);
    } catch(H2OModelBuilderIllegalArgumentException t) {
    }
    params._solver = Solver.IRLSM;
    GLM glm = new GLM(params);
    try {
      params._lambda = new double[]{1};
      glm.trainModel().get();
      assertFalse("should've thrown, p-values only supported with no regularization",true);
    } catch(H2OModelBuilderIllegalArgumentException t) {
    }
    params._lambda = new double[]{0};
    try {
      params._lambda_search = true;
      glm.trainModel().get();
      assertFalse("should've thrown, p-values only supported with no regularization (i.e. no lambda search)",true);
    } catch(H2OModelBuilderIllegalArgumentException t) {
    }
    params._lambda_search = false;
    GLMModel model = null;
    try {
      model = new GLM(params).trainModel().get();
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

    try {
      model = new GLM(params).trainModel().get();
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
    }

    // Airlines (has collinear columns)
    params._standardize = false;
    params._remove_collinear_columns = true;
    params._train = _airlines._key;
    params._response_column = "IsDepDelayed";
    params._ignored_columns = new String[]{"IsDepDelayed_REC"};
    try {
      model = new GLM(params).trainModel().get();
      String[] names_expected = new String[] {"Intercept","fYearf1988","fYearf1989","fYearf1990","fYearf1991","fYearf1992","fYearf1993","fYearf1994","fYearf1995","fYearf1996","fYearf1997","fYearf1998","fYearf1999","fYearf2000","fDayofMonthf10","fDayofMonthf11","fDayofMonthf12","fDayofMonthf13","fDayofMonthf14","fDayofMonthf15","fDayofMonthf16","fDayofMonthf17","fDayofMonthf18","fDayofMonthf19","fDayofMonthf2","fDayofMonthf20","fDayofMonthf21","fDayofMonthf22","fDayofMonthf23","fDayofMonthf24", "fDayofMonthf25",  "fDayofMonthf26",  "fDayofMonthf27",  "fDayofMonthf28",  "fDayofMonthf29" , "fDayofMonthf3"  ,   "fDayofMonthf30" , "fDayofMonthf31",  "fDayofMonthf4", "fDayofMonthf5", "fDayofMonthf6", "fDayofMonthf7"  ,   "fDayofMonthf8" ,  "fDayofMonthf9"  , "fDayOfWeekf2" ,   "fDayOfWeekf3"   , "fDayOfWeekf4"  ,  "fDayOfWeekf5",      "fDayOfWeekf6" ,   "fDayOfWeekf7"  ,  "DepTime",    "ArrTime",    "UniqueCarrierCO",  "UniqueCarrierDL",   "UniqueCarrierHP", "UniqueCarrierPI", "UniqueCarrierTW" ,"UniqueCarrierUA" ,"UniqueCarrierUS", "UniqueCarrierWN" ,  "OriginABQ",  "OriginACY",  "OriginALB",  "OriginATL",  "OriginAUS",  "OriginAVP",    "OriginBDL",  "OriginBGM",  "OriginBHM",  "OriginBNA",  "OriginBOS",  "OriginBTV",    "OriginBUF",  "OriginBUR",  "OriginBWI",  "OriginCAE",  "OriginCHO",  "OriginCHS",    "OriginCLE",  "OriginCLT",  "OriginCMH",  "OriginCOS",  "OriginCRW",  "OriginCVG",    "OriginDAY",  "OriginDCA",  "OriginDEN",  "OriginDFW",  "OriginDSM",  "OriginDTW",    "OriginERI",  "OriginEWR",  "OriginFLL",  "OriginGSO",  "OriginHNL",  "OriginIAD",    "OriginIAH",  "OriginICT",  "OriginIND",  "OriginISP",  "OriginJAX",  "OriginJFK",   "OriginLAS",  "OriginLAX",  "OriginLEX",  "OriginLGA",  "OriginLIH",  "OriginLYH",   "OriginMCI",  "OriginMCO",  "OriginMDT",  "OriginMDW",  "OriginMFR",  "OriginMHT",   "OriginMIA",  "OriginMKE",  "OriginMLB",  "OriginMRY",  "OriginMSP",  "OriginMSY",   "OriginMYR",  "OriginOAK",  "OriginOGG",  "OriginOMA",  "OriginORD",  "OriginORF",   "OriginPBI",  "OriginPHF",  "OriginPHL",  "OriginPHX",  "OriginPIT",  "OriginPSP",   "OriginPVD",  "OriginPWM",  "OriginRDU",  "OriginRIC",  "OriginRNO",  "OriginROA",   "OriginROC",  "OriginRSW",  "OriginSAN",  "OriginSBN",  "OriginSCK",  "OriginSDF",   "OriginSEA",  "OriginSFO",  "OriginSJC",  "OriginSJU",  "OriginSLC",  "OriginSMF",   "OriginSNA",  "OriginSRQ",  "OriginSTL",  "OriginSTX",  "OriginSWF",  "OriginSYR",   "OriginTLH",  "OriginTPA",  "OriginTRI",  "OriginTUS",  "OriginTYS",  "OriginUCA",   "DestABQ",    "DestACY",    "DestALB",    "DestATL",    "DestAVP",    "DestBDL",     "DestBGM",    "DestBNA",    "DestBOS",    "DestBTV",    "DestBUF",    "DestBUR",     "DestBWI",    "DestCAE",    "DestCAK",    "DestCHA",    "DestCHS",    "DestCLE",     "DestCLT",    "DestCMH",    "DestDAY",    "DestDCA",    "DestDEN",    "DestDFW",     "DestDTW",    "DestELM",    "DestERI",    "DestEWR",    "DestFAT",    "DestFAY",     "DestFLL",    "DestFNT",    "DestGEG",    "DestGRR",    "DestGSO",    "DestGSP",     "DestHNL",    "DestHTS",    "DestIAD",    "DestIAH",    "DestICT",    "DestIND",     "DestISP",    "DestJAX",    "DestJFK",    "DestKOA",    "DestLAS",    "DestLAX",     "DestLEX",    "DestLGA",    "DestLIH",    "DestLYH",    "DestMCI",    "DestMCO",     "DestMDT",    "DestMDW",    "DestMHT",    "DestMIA",    "DestMRY",    "DestMSY",     "DestOAJ",    "DestOAK",    "DestOGG",    "DestOMA",    "DestORD",    "DestORF",     "DestORH",    "DestPBI",    "DestPDX",    "DestPHF",    "DestPHL",    "DestPHX",     "DestPIT",    "DestPSP",    "DestPVD",    "DestRDU",    "DestRIC",    "DestRNO",     "DestROA",    "DestROC",    "DestRSW",    "DestSAN",    "DestSCK",    "DestSDF",     "DestSEA",    "DestSFO",    "DestSJC",    "DestSMF",    "DestSNA",    "DestSTL",     "DestSWF",    "DestSYR",    "DestTOL",    "DestTPA",    "DestTUS",    "DestUCA",     "Distance"};
      double[] exp_coefs = new double[] {3.383044e-01,-1.168214e-01,-4.405621e-01,-3.365341e-01,-4.925256e-01,-5.374542e-01,-4.149143e-01,-2.694969e-01,-2.991095e-01,-2.776553e-01,-2.921466e-01,-4.336252e-01
        ,-3.597812e-01,-3.812643e-01,1.024025e-02,2.549787e-02,3.877628e-02,1.650942e-02,-2.981043e-02,-1.167855e-02,1.025499e-02,-4.574083e-03,-2.502898e-02,-5.803535e-02
        ,7.679039e-02,-5.247306e-02,-5.918685e-02,-3.339667e-02,-2.885718e-02,-4.225694e-02,-7.500997e-02,-5.145179e-02,-7.093373e-02,-5.634115e-02,-3.643811e-02,1.284665e-01
        ,-8.150175e-02,-4.724434e-02,1.511024e-01,5.498057e-02,4.411630e-02,1.278961e-02,7.276038e-03,4.672048e-02,-2.128594e-02,1.629933e-02,3.721499e-02,5.933446e-02
        ,-2.303705e-02,1.141451e-02,1.258241e-04,1.271866e-05,7.155502e-02,1.444990e-01,-8.685535e-02,-2.602512e-02,4.227022e-01,2.639493e-01,2.600565e-01,5.409442e-02
        ,5.106308e-02,-1.993041e-01,5.663324e-01,2.524168e-01,-8.032071e-02,1.959854e-02,3.110741e-01,2.711911e-01,-1.480432e-01,2.711969e-02,1.298365e-01,3.051547e-01
        ,1.747017e-01,-6.282101e-03,1.542743e-01,-3.037726e-01,3.808392e-01,1.829607e-01,4.841763e-02,9.353007e-02,2.154611e-01,6.469679e-02,-1.950998e-01,7.957484e-02
        ,2.430247e-01,1.942201e-02,5.701321e-02,2.770389e-01,1.497383e-01,4.943089e-02,2.598871e-01,5.930680e-02,3.748394e-01,4.204685e-02,-3.574776e-01,2.153817e-02
        ,-1.719974e-01,4.806820e-01,2.678204e-01,4.266956e-02,6.340217e-02,-1.536324e-02,-1.294344e-02,1.985872e-01,4.831069e-01,2.726364e-01,-4.813763e-01,4.199029e-01
        ,3.054954e-01,1.784330e-01,-2.500409e-02,2.978489e-03,-9.356699e-02,1.246280e-01,2.858306e-01,-6.533971e-02,-1.403327e-01,-3.924693e-01,5.947271e-02,-7.903152e-03
        ,-2.135489e-01,-1.454085e-01,-2.049959e-01,1.704250e-01,1.826566e-01,1.896976e-01,2.541375e-01,-9.746707e-02,1.990703e-01,9.068512e-02,2.848977e-01,3.409567e-01
        ,8.689141e-02,-6.294297e-02,2.402344e-02,9.583028e-02,4.207585e-01,2.096370e-01,2.184863e-01,1.316822e-01,4.863172e-02,4.918303e-01,-7.990361e-02,-4.499847e-02
        ,6.140887e-02,7.329919e-02,-1.658663e-01,1.850334e-01,-2.165094e-01,-1.054388e-01,8.943775e-02,3.809166e-01,-9.766444e-02,2.645371e-01,-5.147078e-02,2.323637e-01
        ,-3.746418e-01,1.841517e-01,-2.121584e-01,-1.888144e-02,-8.009574e-02,1.801828e-01,1.216036e-01,4.123190e-03,-4.747419e-02,-1.001471e-01,3.611426e-02,1.427218e-01
        ,-1.154052e-01,-2.388724e-01,-8.097489e-03,-3.321890e-02,-8.470654e-02,8.609431e-03,2.278746e-02,2.959335e-01,-8.363623e-02,-1.736324e-01,2.140292e-01,-1.252043e-01
        ,2.086573e-02,7.549936e-02,-2.339204e-01,1.009014e-01,1.396302e-01,-2.180753e-01,-1.118935e-02,-3.345582e-01,-1.490167e-01,-5.455654e-03,-2.884281e-02,-7.778542e-02
        ,1.481921e-01,-9.387787e-02,2.894362e-01,-2.599589e-01,1.210906e-01,1.721670e-02,6.271491e-02,-5.077020e-01,2.524418e-01,-1.146321e-01,-3.418030e-01,-7.056448e-03
        ,-1.948121e-01,-1.716377e-01,-5.915873e-02,3.465761e-01,-3.964155e-02,9.297146e-02,6.840982e-02,-2.694979e-02,3.489802e-01,4.473631e-01,9.045849e-02,1.195621e-01
        ,8.137467e-04,-8.754947e-02,2.089706e-02,2.676953e-03,-1.381342e-01,5.200934e-02,2.208028e-01,-1.096369e-01,4.753661e-01,2.876296e-02,2.256874e-02,-9.231270e-02
        ,2.507403e-02,1.529442e-01,-2.173190e-02,-1.180872e-01,-3.305849e-02,1.091687e-01,9.174085e-02,-6.172636e-02,5.983764e-02,1.094581e-01,1.537772e-01,1.117601e-01
        ,-9.674298e-02,3.111324e-02,1.404767e-01,-4.243193e-03,9.218955e-02,2.554272e-01,-4.434348e-02,1.222306e-01,1.960349e-02,1.308767e-01,-2.830042e-03,-3.212863e-02
        ,-1.035897e-01,-2.828326e-02,-2.452788e-01,5.876054e-02,6.094385e-02,-6.242541e-02,5.535717e-05};

      double[] stder_expected = new double[]{8.262325e-02,1.960654e-02,5.784259e-02,5.211346e-02,5.351436e-02,5.364119e-02,5.377681e-02,5.361611e-02,5.480210e-02,5.916530e-02,5.924352e-02,5.947477e-02,5.684859e-02
        ,6.015367e-02,2.359873e-02,2.364261e-02,2.366028e-02,2.346965e-02,2.331776e-02,2.348358e-02,2.366537e-02,2.371736e-02,2.353753e-02,2.345702e-02,2.360676e-02,2.353096e-02
        ,2.352809e-02,2.354292e-02,2.381824e-02,2.360087e-02,2.357901e-02,2.352439e-02,2.333820e-02,2.348150e-02,2.349408e-02,2.388143e-02,2.363605e-02,2.369714e-02,2.384589e-02
        ,2.360301e-02,2.346261e-02,2.365805e-02,2.377684e-02,2.374369e-02,1.093338e-02,1.091722e-02,1.094858e-02,1.089616e-02,1.127837e-02,1.099223e-02,1.243150e-05,1.193431e-05
        ,6.185154e-02,5.842257e-02,4.797840e-02,4.082146e-02,6.764477e-02,4.904281e-02,4.661126e-02,4.949252e-02,7.194630e-02,1.080608e-01,1.000542e-01,7.206225e-02,6.866783e-02
        ,9.183712e-02,8.937756e-02,9.509039e-02,1.101394e-01,7.333840e-02,6.976195e-02,1.139758e-01,7.902871e-02,6.688118e-02,6.842836e-02,1.228471e-01,1.290408e-01,8.980176e-02
        ,6.808851e-02,7.095243e-02,6.932701e-02,7.036599e-02,1.021726e-01,7.566290e-02,7.743516e-02,7.012655e-02,6.722331e-02,7.756484e-02,2.146603e-01,8.390956e-02,1.138773e-01
        ,6.896196e-02,8.394126e-02,7.983643e-02,8.101956e-02,8.960544e-02,8.278554e-02,2.417453e-01,6.988129e-02,1.085592e-01,9.274580e-02,1.206031e-01,7.400875e-02,6.750358e-02
        ,1.107047e-01,6.957462e-02,1.139873e-01,1.340117e-01,7.976223e-02,6.979235e-02,7.837532e-02,1.285433e-01,1.334371e-01,1.198966e-01,8.332708e-02,1.229658e-01,1.149044e-01
        ,1.130423e-01,1.090638e-01,8.406530e-02,9.600642e-02,7.247142e-02,1.140837e-01,9.506082e-02,6.926602e-02,7.590418e-02,7.459985e-02,1.287070e-01,6.815592e-02,7.411458e-02
        ,6.592406e-02,9.179115e-02,7.223151e-02,7.670526e-02,7.764917e-02,7.343286e-02,1.999711e-01,1.175572e-01,7.108214e-02,7.409246e-02,6.847739e-02,2.476394e-01,1.080218e-01
        ,1.120317e-01,8.137946e-02,6.754660e-02,7.897969e-02,7.867300e-02,1.044366e-01,8.260141e-02,7.542126e-02,1.116638e-01,7.481728e-02,1.126226e-01,1.286945e-01,7.009628e-02
        ,1.346972e-01,6.941736e-02,1.228611e-01,7.884636e-02,1.089254e-01,1.178960e-01,6.487494e-02,1.141428e-01,6.337383e-02,1.044082e-01,9.881149e-02,6.748862e-02,7.802332e-02
        ,7.989152e-02,4.877654e-02,8.606809e-02,6.446482e-02,5.276630e-02,5.072148e-02,1.073048e-01,1.054882e-01,2.695275e-01,8.023848e-02,5.665850e-02,5.273383e-02,6.096450e-02
        ,7.907020e-02,5.261070e-02,5.180430e-02,1.142093e-01,5.580208e-02,2.354317e-01,2.681434e-01,5.047968e-02,1.029695e-01,7.947606e-02,6.167620e-02,1.260100e-01,1.094464e-01
        ,1.044411e-01,6.861138e-02,1.122694e-01,6.168966e-02,1.033369e-01,9.571271e-02,5.958640e-02,1.168745e-01,4.831583e-02,7.683862e-02,7.909215e-02,8.397850e-02,1.069573e-01
        ,5.494288e-02,4.744649e-02,2.133179e-01,5.407477e-02,1.070343e-01,1.207816e-01,5.898603e-02,5.647888e-02,1.076070e-01,7.977657e-02,2.690687e-01,1.077435e-01,3.279724e-01
        ,1.140342e-01,1.154527e-01,5.419787e-02,1.098867e-01,1.049436e-01,5.082173e-02,6.118521e-02,2.107675e-01,7.758130e-02,7.001571e-02,1.073186e-01,4.963340e-02,5.394587e-02
        ,4.612111e-02,7.909675e-02,7.081853e-02,7.685204e-02,1.132175e-01,6.811432e-02,1.231347e-01,7.004574e-02,1.089064e-01,5.191893e-02,2.689951e-01,3.267575e-01,1.008663e-01
        ,4.802894e-02,6.230837e-02,1.109208e-01,6.627911e-02,8.130255e-02,1.094653e-01,5.568541e-02,9.874917e-02,5.701293e-02,7.421695e-02,1.393040e-01,8.828166e-06};

      double[] zvals_expected = new double[]{4.094542787,-5.958287216,-7.616568859,-6.457719779,-9.203616729,-10.019431514,-7.715486715,-5.026416071,-5.457993778,-4.692873330,-4.931283164,-7.290910329
        ,-6.328761834,-6.338172537,0.433932435,1.078470756,1.638876645,0.703437006,-1.278442927,-0.497307097,0.433333243,-0.192857977,-1.063364622,-2.474114538
        ,3.252898021,-2.229957876,-2.515581926,-1.418544300,-1.211558333,-1.790482149,-3.181217772,-2.187168299,-3.039383181,-2.399384279,-1.550948532,5.379347430
        ,-3.448197372,-1.993672779,6.336619935,2.329387922,1.880280495,0.540602946,0.306013647,1.967700936,-1.946876164,1.492992904,3.399070000,5.445447763
        ,-2.042586155,1.038415698,10.121391544,1.065722732,1.156883305,2.473342141,-1.810301011,-0.637535148,6.248852336,5.382017137,5.579262483,1.092981554
        ,0.709738700,-1.844369286,5.660253498,3.502760590,-1.169699226,0.213405474,3.480449353,2.851929432,-1.344144285,0.369788430,1.861136098,2.677364258
        ,2.210610981,-0.093929275,2.254537133,-2.472769164,2.951309213,2.037384116,0.711098412,1.318208131,3.107896103,0.919432700,-1.909512146,1.051702242
        ,3.138428148,0.276956565,0.848116635,3.571707066,0.697559521,0.589097196,2.282167858,0.859992866,4.465496733,0.526662485,-4.412237900,0.240366792
        ,-2.077626172,1.988382134,3.832504877,0.393053401,0.683612236,-0.127386732,-0.174890682,2.941876192,4.363924639,3.918618005,-4.223069230,3.133329974
        ,3.830075787,2.556626718,-0.319030122,0.023171093,-0.701206599,1.039462023,3.430224574,-0.531365033,-1.221299087,-3.471879279,0.545302258,-0.094012061
        ,-2.224318698,-2.006425249,-1.796891224,1.792799985,2.637030567,2.499172484,3.406675572,-0.757278429,2.920806636,1.223580021,4.321604030,3.714483644
        ,1.202957243,-0.820582100,0.309384395,1.305005410,2.104096487,1.783276250,3.073716393,1.777268003,0.710186572,1.986074319,-0.739698581,-0.401658318
        ,0.754599117,1.085164836,-2.100113836,2.351929605,-2.073117895,-1.276477554,1.185842715,3.411279361,-1.305372693,2.348881190,-0.399945460,3.314921843
        ,-2.781362443,2.652819094,-1.726814586,-0.239471355,-0.735326303,1.528320564,1.874431642,0.036123072,-0.749113504,-0.959187862,0.365486433,2.114753848
        ,-1.479111228,-2.989960091,-0.166011968,-0.385960754,-1.313996380,0.163161548,0.449266356,2.757878211,-0.792848866,-0.644210444,2.667413046,-2.209806237
        ,0.395680070,1.238415063,-2.958388604,1.917886602,2.695339429,-1.909435881,-0.200518466,-1.421041172,-0.555735022,-0.108076244,-0.280110165,-0.978727707
        ,2.402743720,-0.745003303,2.644547495,-2.489047558,1.764876031,0.153351678,1.016619439,-4.913074631,2.637495147,-1.923795851,-2.924531170,-0.146048350
        ,-2.535340379,-2.170097247,-0.704450917,3.240322900,-0.721504747,1.959501429,0.320694226,-0.498380144,3.260451771,3.703901572,1.533557842,2.116934588
        ,0.007562213,-1.097433427,0.077664399,0.024845610,-0.421176265,0.456085590,1.912495640,-2.022900342,4.325966158,0.274080250,0.444076651,-1.508741999
        ,0.118965357,1.971405595,-0.310386063,-1.100341487,-0.666053319,2.023671705,1.989128987,-0.780390564,0.844943233,1.424270602,1.358245515,1.640771855
        ,-0.785668134,0.444184574,1.289884997,-0.081727285,0.342718282,0.781702452,-0.439626096,2.544935912,0.314620525,1.179911368,-0.042698859,-0.395173744
        ,-0.946324367,-0.507911593,-2.483856451,1.030652857,0.821158118,-0.448123713,6.270517743
      };

      double[] pvals_expected = new double[]{4.243779e-05,2.584251e-09,2.700448e-14,1.083124e-10,3.733573e-20,1.392306e-23,1.251677e-14,5.032991e-07,4.862783e-08,2.708701e-06,8.223295e-07,3.173337e-13,2.514741e-10
        ,2.366114e-10,6.643414e-01,2.808345e-01,1.012520e-01,4.817902e-01,2.011056e-01,6.189770e-01,6.647766e-01,8.470718e-01,2.876273e-01,1.336350e-02,1.143912e-03,2.575939e-02
        ,1.189004e-02,1.560448e-01,2.256933e-01,7.338895e-02,1.468427e-03,2.873979e-02,2.373166e-03,1.643019e-02,1.209271e-01,7.545084e-08,5.653029e-04,4.619905e-02,2.390030e-10
        ,1.984672e-02,6.008188e-02,5.887863e-01,7.595969e-01,4.911388e-02,5.156115e-02,1.354521e-01,6.772461e-04,5.217869e-08,4.110425e-02,2.990870e-01,4.958814e-24,2.865596e-01
        ,2.473315e-01,1.339242e-02,7.026154e-02,5.237824e-01,4.203831e-10,7.434137e-08,2.441273e-08,2.744128e-01,4.778730e-01,6.514157e-02,1.528620e-08,4.612949e-04,2.421336e-01
        ,8.310125e-01,5.014564e-04,4.349160e-03,1.789144e-01,7.115434e-01,6.273710e-02,7.425404e-03,2.707212e-02,9.251661e-01,2.417131e-02,1.341390e-02,3.167338e-03,4.162244e-02
        ,4.770301e-01,1.874465e-01,1.886429e-03,3.578785e-01,5.620789e-02,2.929467e-01,1.700608e-03,7.818158e-01,3.963814e-01,3.553510e-04,4.854594e-01,5.558016e-01,2.248808e-02
        ,3.898015e-01,8.024485e-06,5.984328e-01,1.027500e-05,8.100480e-01,3.775434e-02,4.678071e-02,1.271662e-04,6.942835e-01,4.942266e-01,8.986354e-01,8.611670e-01,3.265400e-03
        ,1.282792e-05,8.930334e-05,2.418793e-05,1.730416e-03,1.284274e-04,1.057531e-02,7.497064e-01,9.815140e-01,4.831808e-01,2.986003e-01,6.040895e-04,5.951707e-01,2.219847e-01
        ,5.177320e-04,5.855507e-01,9.251004e-01,2.613621e-02,4.482202e-02,7.236537e-02,7.301742e-02,8.368881e-03,1.245495e-02,6.586634e-04,4.488905e-01,3.494493e-03,2.211226e-01
        ,1.555177e-05,2.040773e-04,2.290047e-01,4.118924e-01,7.570318e-01,1.919034e-01,3.538034e-02,7.455389e-02,2.116458e-03,7.553673e-02,4.775953e-01,4.703635e-02,4.594901e-01
        ,6.879391e-01,4.504969e-01,2.778595e-01,3.572917e-02,1.868429e-02,3.817188e-02,2.017990e-01,2.356961e-01,6.476464e-04,1.917784e-01,1.883792e-02,6.892002e-01,9.180365e-04
        ,5.417320e-03,7.987482e-03,8.421375e-02,8.107421e-01,4.621479e-01,1.264461e-01,6.088301e-02,9.711845e-01,4.537961e-01,3.374737e-01,7.147515e-01,3.446114e-02,1.391236e-01
        ,2.792949e-03,8.681489e-01,6.995291e-01,1.888599e-01,8.703926e-01,6.532436e-01,5.822158e-03,4.278737e-01,5.194451e-01,7.648862e-03,2.712795e-02,6.923446e-01,2.155742e-01
        ,3.095516e-03,5.513717e-02,7.036561e-03,5.621772e-02,8.410768e-01,1.553177e-01,5.783972e-01,9.139361e-01,7.793954e-01,3.277243e-01,1.628008e-02,4.562770e-01,8.185310e-03
        ,1.281526e-02,7.759723e-02,8.781222e-01,3.093447e-01,9.024527e-07,8.357430e-03,5.439191e-02,3.452960e-03,8.838844e-01,1.124006e-02,3.000919e-02,4.811588e-01,1.195559e-03
        ,4.706060e-01,5.006557e-02,7.484449e-01,6.182207e-01,1.113888e-03,2.127814e-04,1.251516e-01,3.427559e-02,9.939663e-01,2.724629e-01,9.380957e-01,9.801783e-01,6.736301e-01
        ,6.483325e-01,5.582446e-02,4.309441e-02,1.524737e-05,7.840253e-01,6.569911e-01,1.313778e-01,9.053038e-01,4.868889e-02,7.562701e-01,2.711943e-01,5.053834e-01,4.301493e-02
        ,4.669822e-02,4.351687e-01,3.981509e-01,1.543811e-01,1.743985e-01,1.008578e-01,4.320696e-01,6.569131e-01,1.971029e-01,9.348643e-01,7.318134e-01,4.343971e-01,6.602119e-01
        ,1.093594e-02,7.530525e-01,2.380471e-01,9.659419e-01,6.927182e-01,3.439926e-01,6.115200e-01,1.300354e-02,3.027140e-01,4.115643e-01,6.540679e-01,3.659400e-10};
      double[] stder_actual = model._output.stdErr();
      double[] zvals_actual = model._output.zValues();
      double[] pvals_actual = model._output.pValues();
      String[] names_actual = model._output.coefficientNames();
      HashMap<String, Integer> coefMap = new HashMap<>();
      for (int i = 0; i < names_expected.length; ++i)
        coefMap.put(names_expected[i], i);
      double[] coefs_actual = model._output._global_beta;
      for (int i = 0; i < exp_coefs.length; ++i) {
        String s = removeDot(names_actual[i]);
        if(!coefMap.containsKey(s)) { // removed col, check we removed it too
          assertTrue(coefs_actual[i] == 0 && Double.isNaN(zvals_actual[i]));
          System.out.println("found removed col " + s);
        } else {
          int id = coefMap.get(s);
          assertEquals(exp_coefs[id], coefs_actual[i], 1e-4);
          assertEquals(stder_expected[id], stder_actual[i],Math.abs(stder_expected[id]*1e-4));
          assertEquals(zvals_expected[id], zvals_actual[i],Math.abs(zvals_expected[id]*1e-4));
          assertEquals(pvals_expected[id], pvals_actual[i],pvals_expected[id]*1e-4);
        }
      }
    } finally {
      if(model != null) model.delete();
    }

  }
  private static String removeDot(String s) {
    int id = s.indexOf(".");
    if(id ==-1) return s;
    return s.substring(0,id) + s.substring(id+1);
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
    if(_airlines != null)
      _airlines.delete();
  }
}
