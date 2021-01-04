package hex.glm;

import hex.StringPair;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.util.ArrayUtils;

import java.util.Arrays;

import static org.junit.Assert.*;

public class GLMPlugValuesTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void testNumeric() {
    Scope.enter();
    try {
      // has missing
      Frame fr = new TestFrameBuilder()
              .withColNames("x", "y", "z")
              .withDataForCol(0, ard(1.0d, Double.NaN))
              .withDataForCol(1, ard(Double.NaN, 2.0d))
              .withDataForCol(2, ard(2.0d, 8.0d))
              .build();
      // missing values manually substituted with the corresponding plug values
      Frame fr2 = new TestFrameBuilder()
              .withColNames("x", "y", "z")
              .withDataForCol(0, ard(1.0d, 4.0d))
              .withDataForCol(1, ard(0.5d, 2.0d))
              .withDataForCol(2, ard(2.0d, 8.0d))
              .build();
      
      Frame plugValues = oneRowFrame(new String[]{"x", "y"}, new double[]{4.0d, 0.5d});

      GLMModel.GLMParameters params = new GLMModel.GLMParameters();
      params._response_column = "z";
      params._family = GLMModel.GLMParameters.Family.gaussian;
      params._standardize = false;
      params._train = fr._key;
      params._ignore_const_cols = false;
      params._intercept = false;
      params._seed = 42;
      
      GLMModel.GLMParameters params2 = (GLMModel.GLMParameters) params.clone();
      params2._train = fr2._key;
      
      params._missing_values_handling = GLMModel.GLMParameters.MissingValuesHandling.PlugValues;
      params._plug_values = plugValues._key;
      GLMModel model = new GLM(params).trainModel().get();
      Scope.track_generic(model);

      GLMModel model2 = new GLM(params2).trainModel().get();
      Scope.track_generic(model2);

      assertEquals(model2.coefficients(), model.coefficients());

      Frame preds = Scope.track(model.score(fr));
      model.testJavaScoring(fr, preds, 1e-8, 1e-15, 1.0);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testCategorical() {
    Scope.enter();
    try {
      // no missing
      Frame fr2 = new TestFrameBuilder()
              .withColNames("x", "y")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b"))
              .withDataForCol(1, ard(1.0d, 2.0d))
              .build();
      // introduce na but keep the domain the same
      Frame fr = fr2.deepCopy(Key.make().toString());
      fr.vec(0).setNA(1);
      Scope.track(fr);
      DKV.put(fr);

      Frame plugValues = oneRowFrame(new String[]{"x"}, new double[]{}, "b");

      GLMModel.GLMParameters params = new GLMModel.GLMParameters();
      params._response_column = "y";
      params._family = GLMModel.GLMParameters.Family.gaussian;
      params._standardize = false;
      params._train = fr._key;
      params._ignore_const_cols = false;
      params._intercept = false;
      params._seed = 42;

      GLMModel.GLMParameters params2 = (GLMModel.GLMParameters) params.clone();
      params2._train = fr2._key;

      params._missing_values_handling = GLMModel.GLMParameters.MissingValuesHandling.PlugValues;
      params._plug_values = plugValues._key;

      GLMModel model = new GLM(params).trainModel().get();
      Scope.track_generic(model);

      GLMModel model2 = new GLM(params2).trainModel().get();
      Scope.track_generic(model2);

      assertEquals(model2.coefficients(), model.coefficients());

      Frame preds = Scope.track(model.score(fr));
      model.testJavaScoring(fr, preds, 1e-8, 1e-15, 1.0);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testNumericInteraction() {
    Scope.enter();
    try {
      // has missing in one of the columns; we will have an interaction between x & y
      Frame fr = new TestFrameBuilder()
              .withColNames("x", "y", "z")
              .withDataForCol(0, ard(1.0d, 4.0d))
              .withDataForCol(1, ard(Double.NaN, 2.0d))
              .withDataForCol(2, ard(2.0d, 8.0d))
              .build();
      // missing values manually substituted with the corresponding plug values; manually created x_y interaction column
      Frame fr2 = new TestFrameBuilder()
              .withColNames("x_y", "x", "y", "z")
              .withDataForCol(0, ard(0.5d, 8.0d))
              .withDataForCol(1, ard(1.0d, 4.0d))
              .withDataForCol(2, ard(0.5d, 2.0d))
              .withDataForCol(3, ard(2.0d, 8.0d))
              .build();

      Frame plugValues = oneRowFrame(new String[]{"x_y", "x", "y"}, new double[]{0.5, 4.0d, 0.5d});

      GLMModel.GLMParameters params = new GLMModel.GLMParameters();
      params._response_column = "z";
      params._family = GLMModel.GLMParameters.Family.gaussian;
      params._standardize = false;
      params._train = fr._key;
      params._ignore_const_cols = false;
      params._intercept = false;
      params._seed = 42;

      GLMModel.GLMParameters params2 = (GLMModel.GLMParameters) params.clone();
      params2._train = fr2._key;

      params._missing_values_handling = GLMModel.GLMParameters.MissingValuesHandling.PlugValues;
      params._plug_values = plugValues._key;
      params._interaction_pairs = new StringPair[]{new StringPair("x", "y")};

      GLMModel model = new GLM(params).trainModel().get();
      Scope.track_generic(model);

      GLMModel model2 = new GLM(params2).trainModel().get();
      Scope.track_generic(model2);

      // make sure the interaction column actually does have a non-zero coefficient
      assertNotEquals(0, model.coefficients().get("x_y"));
      // both models should be the same
      assertEquals(model2.coefficients(), model.coefficients());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testCatCatInteraction_smoke() {
    Scope.enter();
    try {
      // has missing in one of the columns; we will have an interaction between x & y
      Frame fr = new TestFrameBuilder()
              .withColNames("n", "x", "y", "z")
              .withVecTypes(Vec.T_NUM, Vec.T_CAT, Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar(0, 1, 0, 1))
              .withDataForCol(1, ar("a", "b", "a", "b"))
              .withDataForCol(2, ar("A", "B", "B", "A"))
              .withDataForCol(3, ard(2.0d, 8.0d, 4.0, 1.0))
              .build();

      Frame plugValues = oneRowFrame(new String[]{"n", "x_y", "x", "y"}, new double[]{0}, "a_A", "a", "B");

      GLMModel.GLMParameters params = new GLMModel.GLMParameters();
      params._response_column = "z";
      params._family = GLMModel.GLMParameters.Family.gaussian;
      params._standardize = false;
      params._train = fr._key;
      params._ignore_const_cols = false;
      params._intercept = false;
      params._seed = 42;

      params._missing_values_handling = GLMModel.GLMParameters.MissingValuesHandling.PlugValues;
      params._plug_values = plugValues._key;
      params._interaction_pairs = new StringPair[]{new StringPair("x", "y")};

      // just check it doesn't crash
      GLMModel model = new GLM(params).trainModel().get();
      Scope.track_generic(model);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testNumCatInteraction_smoke() {
    Scope.enter();
    try {
      // has missing in one of the columns; we will have an interaction between x & y
      Frame fr = new TestFrameBuilder()
              .withColNames("x", "y", "z")
              .withVecTypes(Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ard(0.0d, Double.NaN, 0.0d, 1.0d))
              .withDataForCol(1, ar("a", "b", "a", "b"))
              .withDataForCol(2, ard(2.0d, 8.0d, 4.0, 1.0))
              .build();

      Frame plugValues = oneRowFrame(new String[]{"x", "x_y.a", "x_y.b", "y"}, new double[]{0, 1, 2}, "b");

      GLMModel.GLMParameters params = new GLMModel.GLMParameters();
      params._response_column = "z";
      params._family = GLMModel.GLMParameters.Family.gaussian;
      params._standardize = false;
      params._train = fr._key;
      params._ignore_const_cols = false;
      params._intercept = false;
      params._seed = 42;

      params._missing_values_handling = GLMModel.GLMParameters.MissingValuesHandling.PlugValues;
      params._plug_values = plugValues._key;
      params._interaction_pairs = new StringPair[]{new StringPair("x", "y")};

      // just check it doesn't crash
      GLMModel model = new GLM(params).trainModel().get();
      Scope.track_generic(model);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testPlugValues_zeros() {
    Scope.enter();
    try {
      Frame fr = parseTestFile("smalldata/junit/cars.csv");
      Scope.track(fr);
      fr.remove("name");
      DKV.put(fr);

      // check that we actually do have some NAs in the dataset
      assertTrue(fr.vec("economy (mpg)").naCnt() > 0);
      
      GLMModel.GLMParameters params = new GLMModel.GLMParameters(
              GLMModel.GLMParameters.Family.poisson,
              GLMModel.GLMParameters.Family.poisson.defaultLink, 
              new double[]{0}, new double[]{0},0,0);
      params._response_column = "power (hp)";
      params._train = fr._key;
      params._lambda = new double[]{0};
      params._alpha = new double[]{0};
      params._missing_values_handling = GLMModel.GLMParameters.MissingValuesHandling.MeanImputation;
      params._seed = 42;

      GLMModel.GLMParameters params_means = (GLMModel.GLMParameters) params.clone();
      GLMModel.GLMParameters params_zeros = (GLMModel.GLMParameters) params.clone();

      GLMModel model = new GLM(params).trainModel().get();
      Scope.track_generic(model);

      Frame predictors = fr.clone();
      predictors.remove(params._response_column);
      Frame plugValues = oneRowFrame(predictors.names(), predictors.means());
      params_means._plug_values = plugValues._key;
      params_means._missing_values_handling = GLMModel.GLMParameters.MissingValuesHandling.PlugValues;

      // this doesn't check much - only demonstrates that setting means doesn't produce different results
      GLMModel model_means = new GLM(params_means).trainModel().get();
      Scope.track_generic(model_means);

      assertArrayEquals(model.beta(), model_means.beta(), 0);
      assertArrayEquals(model.dinfo()._numNAFill, model_means.dinfo()._numNAFill, 0);

      Frame plugValues_zeros = oneRowFrame(predictors.names(), new double[predictors.numCols()]);
      params_zeros._plug_values = plugValues_zeros._key;
      params_zeros._missing_values_handling = GLMModel.GLMParameters.MissingValuesHandling.PlugValues;

      GLMModel model_zeros = new GLM(params_zeros).trainModel().get();
      Scope.track_generic(model_zeros);
      
      // NA fill should be properly populated
      assertArrayEquals(model_zeros.dinfo()._numNAFill, new double[predictors.numCols()], 0);
      assertNotEquals(model.coefficients().get("economy (mpg)"), model_zeros.coefficients().get("economy (mpg)"));
    } finally {
      Scope.exit();
    }
  }

  private static Frame oneRowFrame(String[] names, double[] values, String... svalues) {
    TestFrameBuilder builder = new TestFrameBuilder().withColNames(names);
    byte[] numTypes = new byte[values.length];
    Arrays.fill(numTypes, Vec.T_NUM);
    byte[] catTypes = new byte[svalues.length];
    Arrays.fill(catTypes, Vec.T_CAT);
    builder.withVecTypes(ArrayUtils.append(numTypes, catTypes));
    for (int i = 0; i < values.length; i++)
      builder.withDataForCol(i, new double[]{values[i]});
    for (int i = 0; i < svalues.length; i++)
      builder.withDataForCol(i + values.length, new String[]{svalues[i]});
    return builder.build();
  }
  
}
