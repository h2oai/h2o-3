package hex.glm;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;

import static hex.glm.GLMModel.GLMParameters.Family.binomial;
import static hex.glm.GLMModel.GLMParameters.Family.gaussian;
import static water.fvec.Vec.T_NUM;
import static water.fvec.Vec.T_STR;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class GLMEqualZeroConstraints  extends TestUtil {
  Frame _trainG;
  Frame _trainB;
  Map<String, Double> _gaussianCoef;
  Map<String, Double> _binomialCoef;
  GLMModel _glmG;
  GLMModel _glmB;
  String[] _gCoeffNames;
  String[] _bCoeffNames;
  Random _obj = new Random(123);
  
  
  @Before
  public void setup() {
    Scope.enter();
    importFrameNBuildModel();

  }
  
  public void importFrameNBuildModel() {
    // build Gaussian models, coefficients
    _trainG = parseAndTrackTestFile("smalldata/glm_test/gaussian_1enum_7num_10KRows.csv");
    GLMModel.GLMParameters params = new GLMModel.GLMParameters(gaussian);
    params._standardize=true;
    params._response_column = "response";
    params._lambda = new double[]{0.0};
    params._train = _trainG._key;
   _glmG = new GLM(params).trainModel().get();
    Scope.track_generic(_glmG);
    _gaussianCoef = _glmG.coefficients();
    _gCoeffNames = _glmG._output.coefficientNames();
    // build binomial models, coefficients
    _trainB = parseAndTrackTestFile("smalldata/glm_test/binomial_1enum_7num_10KRows.csv");
    params = new GLMModel.GLMParameters(binomial);
    params._standardize=true;
    params._response_column = "response";
    params._lambda = new double[]{0.0};
    params._train = _trainB._key;
    _glmB = new GLM(params).trainModel().get();
    Scope.track_generic(_glmB);
    _binomialCoef = _glmB.coefficients();
    _bCoeffNames = _glmB._output.coefficientNames();
  }
  
  

  @After
  public void teardown() {
    Scope.exit();
  }

  // just one equality constraint: 0.5*C6-1.5*C8-(-2.4815080925360693) == 0
  @Test
  public void testEqualityConstraintsG1() {
    Scope.enter();
    try {
      Frame oneEqualityConstraint = new TestFrameBuilder().withColNames("name", "values", "types", "constraint_numbers")
              .withVecTypes(T_STR, T_NUM, T_STR, T_NUM)
              .withDataForCol(0, new String[]{_gCoeffNames[9], _gCoeffNames[10], "constant"})
              .withDataForCol(1, new double[]{0.5, -1.5, -(0.5 * _gaussianCoef.get("C6") - 1.5 * _gaussianCoef.get("C8"))})
              .withDataForCol(2, new String[]{"equal", "equal", "equal"})
              .withDataForCol(3, new int[]{0, 0, 0}).build();
      Scope.track(oneEqualityConstraint);
      double[] beta = IntStream.range(0, _gCoeffNames.length).mapToDouble(x->_obj.nextGaussian()).toArray(); // random coefficient values
    } finally {
      Scope.exit();
    }
            
    System.out.println("Wow");
    System.out.println("Wow");
  }

}
