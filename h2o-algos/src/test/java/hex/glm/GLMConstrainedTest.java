package hex.glm;

import Jama.Matrix;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.ArrayUtils;
import water.util.IcedHashMap;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static hex.glm.ComputationState.GramGrad.dropCols;
import static hex.glm.ComputationState.GramGrad.findZeroCols;
import static hex.glm.ComputationState.calGram;
import static hex.glm.ConstrainedGLMUtils.*;
import static hex.glm.GLMModel.GLMParameters.Family.gaussian;
import static hex.glm.GLMModel.GLMParameters.Solver.IRLSM;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;
import static water.fvec.Vec.T_NUM;
import static water.fvec.Vec.T_STR;
import static water.util.ArrayUtils.copy2DArray;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class GLMConstrainedTest extends TestUtil {
  public static final double EPS = 1e-6;
  Frame _betaConstraint1;
  Frame _betaConstraint2;
  Frame _linearConstraint1;
  Frame _linearConstraint2;
  Frame _linearConstraint3;
  Frame _linearConstraint4;
  List<String> _coeffNames1;
  String[][] _betaConstraintNames1Equal;
  double[][] _betaConstraintValStandard1Equal;
  double[][] _betaConstraintVal1Equal;
  String[][] _betaConstraintNames1Less;
  double[][] _betaConstraintValStandard1Less;
  double[][] _betaConstraintVal1Less;
  String[][] _equalityNames1;
  double[][] _equalityValuesStandard1;
  double[][] _equalityValues1;
  String[][] _lessThanNames1;
  double[][] _lessThanValuesStandard1;
  double[][] _lessThanValues1;
  String[][] _equalityNames2;
  double[][] _equalityValues2;
  double[][] _equalityValuesStandard2;
  String[][] _lessThanNames2;
  double[][] _lessThanValues2;
  double[][] _lessThanValuesStandard2;
  int[] _catCol = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
  ConstrainedGLMUtils.LinearConstraints[] _equalityConstr;
  ConstrainedGLMUtils.LinearConstraints[] _lessThanConstr;
  ConstrainedGLMUtils.ConstraintsDerivatives[] _cdEqual;
  ConstrainedGLMUtils.ConstraintsDerivatives[] _cdLess;
  ConstrainedGLMUtils.ConstraintsGram[] _cGEqual;
  ConstrainedGLMUtils.ConstraintsGram[] _cGLess;
  List<String> _coeffsDG;
  double[] _lambdaE;
  double[] _lambdaL;
  double[][] _equalGramContr;
  double[][] _lessGramContr;
  double[] _equalGradContr;
  double[] _lessGradContr;
  double _ck = 10;
  double[] _beta;
  Random _obj = new Random(123);
  
  
  @Before
  public void setup() {
    Scope.enter();
    Frame train = parseAndTrackTestFile("smalldata/glm_test/gaussian_20cols_10000Rows.csv");
    _coeffNames1 = transformFrameCreateCoefNames(train);
    generateConstraint1FrameNAnswer(train);
    generateConstraint2FrameNAnswer(train);
    generateConstraint3FrameNAnswer();
    generateConstraint4FrameNAnswer();
    generateConstraints();
    generateDerivativeAnswer();
    generateGramAnswer();
  }

  /**
   * We have fake predictors C1, C2, C3, C4, C5, C6, C7, C8, C9, C10, C11 and constraints of 
   * a). 0.4*C1 + 0.2*C2+10 <= 0, b) C1-0.3*C3 == 0, c). 10*C3-4*C4 -100 <= 0, d) 11*C4 - 2.4*C5 == 0, 
   * e) -1.5*C6-2.8*C7 <= 0; f) -9.3*C10+1.3*C11+0.4 == 0.
   *    
   *  The coefficient list is C1, C2, C3, C4, C5, C6, C7, C8, C9, C10, C11 with C1 at index 0, C2 at index 1 and ...
   */
  public void generateConstraints() {
    _beta = IntStream.range(0, _coeffNames1.size()).mapToDouble(x->_obj.nextGaussian()).toArray();
    _coeffsDG = new ArrayList<>(Arrays.asList("C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10", "C11", "C12"));
    _equalityConstr = new ConstrainedGLMUtils.LinearConstraints[3];
    _lessThanConstr = new ConstrainedGLMUtils.LinearConstraints[3];
    _equalityConstr[0] = makeOneConstraint(new String[]{"C1", "C3"}, new double[]{1, -0.3}, true);
    _equalityConstr[1] = makeOneConstraint(new String[]{"C4", "C5"}, new double[]{11, -2.4}, true);
    _equalityConstr[2] = makeOneConstraint(new String[]{"C10", "C11", "Constant"}, new double[]{-9.3, 1.3, 0.4}, true);
    
    _lessThanConstr[0] = makeOneConstraint(new String[]{"C1", "C2", "Constant"}, new double[]{0.4, 0.2, 10}, false);
    _lessThanConstr[1] = makeOneConstraint(new String[]{"C3", "C4", "Constant"}, new double[]{10, -4, -100}, false);
    _lessThanConstr[2] = makeOneConstraint(new String[]{"C6", "C7"}, new double[]{-1.5, -2.8}, false);
  }
  
  public ConstrainedGLMUtils.LinearConstraints makeOneConstraint(String[] coeffNames, double[] constrVal, boolean equalConstraints) {
    ConstrainedGLMUtils.LinearConstraints constraint = new ConstrainedGLMUtils.LinearConstraints();
    int val = 0;
    int numCoeffs = coeffNames.length;
    for (int index=0; index<numCoeffs; index++) {
      constraint._constraints.put(coeffNames[index], constrVal[index]);
      if ("Constant".equals(coeffNames[index])) {
        val += constrVal[index];
      } else {
        int coefInd = _coeffNames1.indexOf(coeffNames[index]);
        if (coefInd >= 0)
          val += _beta[coefInd]*constrVal[index];
      }
    }
    constraint._constraintsVal = val;
    if (equalConstraints)
      constraint._active = constraint._constraintsVal != 0;
    else
      constraint._active = val > 0;
    return constraint;
  }

  /***
   * We are testing the correct implementation of derivatives generation from constrainted GLM.  We have fake
   * predictors C1, C2, C3, C4, C5, C6, C7, C8, C9, C10, C11 and constraints of 
   * a). 0.4*C1 + 0.2*C2+10 <= 0, 
   * b) C1-0.3*C3 == 0,
   * c). 10*C3-4*C4 -100 <= 0, 
   * d) 11*C4 - 2.4*C5 == 0, 
   * e) -1.5*C6-2.8*C7 <= 0; 
   * f) -9.3*C10+1.3*C11 == 0.
   *
   * The coefficient list is C1, C2, C3, C4, C5, C6, C7, C8, C9, C10, C11 with C1 at index 0, C2 at index 1 and ...
   *
   * In this case, the correct answer should be:
   * Equality constraints derivatives:
   * (0, 1), (2, -0.3), 
   * (3, 11), (4, -2.4), 
   * (9, -9,3), (10, 1.3)
   *
   * less than equal to constraints derivatives:
   * (0, 0.4), (1, 0.2)
   * (2, 10), (3, -4)
   * (5, -1.5), (6, -2.8)
   */
  public void generateDerivativeAnswer() {
    int coefSize = _coeffNames1.size()+1; // +1 for intercept
    _equalGradContr = new double[coefSize];
    _lessGradContr = new double[coefSize];
    // generate gradient contributions from tranpose(lambda)*h(beta)
    _lambdaE = IntStream.range(0,3).mapToDouble(x-> _obj.nextGaussian()).toArray();
    _cdEqual = new ConstrainedGLMUtils.ConstraintsDerivatives[3];
    _cdEqual[0] = genOneCDerivative(new int[]{0, 2}, new double[]{1, -0.3}, true);
    _equalGradContr[0] += 1*_lambdaE[0];
    _equalGradContr[2] += -0.3*_lambdaE[0];
    _cdEqual[1] = genOneCDerivative(new int[]{3, 4}, new double[]{11, -2.4}, true);
    _equalGradContr[3] += 11*_lambdaE[1];
    _equalGradContr[4] += -2.4*_lambdaE[1];
    _cdEqual[2] = genOneCDerivative(new int[]{9,10}, new double[]{-9.3, 1.3}, true);
    _equalGradContr[9] += -9.3*_lambdaE[2];
    _equalGradContr[10] += 1.3*_lambdaE[2];
    
    _lambdaL = IntStream.range(0,3).mapToDouble(x->_obj.nextGaussian()).toArray();
    _cdLess = new ConstrainedGLMUtils.ConstraintsDerivatives[3];
    _cdLess[0] = genOneCDerivative(new int[]{0,1}, new double[]{0.4, 0.2}, _lessThanConstr[0]._active);
    if (_lessThanConstr[0]._active) {
      _lessGradContr[0] += 0.4 * _lambdaL[0];
      _lessGradContr[1] += 0.2 * _lambdaL[0];
    }
    _cdLess[1] = genOneCDerivative(new int[]{2,3}, new double[]{10, -4}, _lessThanConstr[1]._active);
    if (_lessThanConstr[1]._active) {
      _lessGradContr[2] += 10 * _lambdaL[1];
      _lessGradContr[3] += -4 * _lambdaL[1];
    }
    _cdLess[2] =  genOneCDerivative(new int[]{5,6}, new double[]{-1.5, -2.8}, _lessThanConstr[2]._active);
    if (_lessThanConstr[2]._active) {
      _lessGradContr[5] += -1.5 * _lambdaL[2];
      _lessGradContr[6] += -2.8 * _lambdaL[2];
    }
    
    // generate gradient contributions from penalty: Ck/2*transpose(h(beta))*h(beta)
    _equalGradContr[0] += _ck*_cdEqual[0]._constraintsDerivative.get(0)*_equalityConstr[0]._constraintsVal;
    _equalGradContr[2] += _ck*_cdEqual[0]._constraintsDerivative.get(2)*_equalityConstr[0]._constraintsVal;
    _equalGradContr[3] += _ck*_cdEqual[1]._constraintsDerivative.get(3)*_equalityConstr[1]._constraintsVal;
    _equalGradContr[4] += _ck*_cdEqual[1]._constraintsDerivative.get(4)*_equalityConstr[1]._constraintsVal;
    _equalGradContr[9] += _ck*_cdEqual[2]._constraintsDerivative.get(9)*_equalityConstr[2]._constraintsVal;
    _equalGradContr[10] += _ck*_cdEqual[2]._constraintsDerivative.get(10)*_equalityConstr[2]._constraintsVal;

    if (_lessThanConstr[0]._active) {
      _lessGradContr[0] += _ck*_cdLess[0]._constraintsDerivative.get(0)*_lessThanConstr[0]._constraintsVal;
      _lessGradContr[1] += _ck*_cdLess[0]._constraintsDerivative.get(1)*_lessThanConstr[0]._constraintsVal;
    }
    if (_lessThanConstr[1]._active) {
      _lessGradContr[2] += _ck*_cdLess[1]._constraintsDerivative.get(2)*_lessThanConstr[1]._constraintsVal;
      _lessGradContr[3] += _ck*_cdLess[1]._constraintsDerivative.get(3)*_lessThanConstr[1]._constraintsVal;
    }
    if (_lessThanConstr[2]._active) {
      _lessGradContr[5] += _ck*_cdLess[2]._constraintsDerivative.get(5)*_lessThanConstr[2]._constraintsVal;
      _lessGradContr[6] += _ck*_cdLess[2]._constraintsDerivative.get(6)*_lessThanConstr[2]._constraintsVal;
    }
  }


  /***
   * This generates the contribution to the gram from Linear constraints.  
   * Equality constraints Gram contribution:
   * ((0,0), 1), ((0,2), -0.3), ((2,2), 0.09)
   * ((3,3), 121), ((3,4), -26.4), ((4,4), 5.76)
   * ((9,9), 86.49), ((9,10), 12.09), ((10,10), 1.69)
   * 
   * Less than:
   * ((0,0), 0.16), ((0,1), 0.08), ((1,1), 0.04)
   * ((2,2),100), ((2,3), -40), ((3,3), 16)
   * ((5,5), 2.25), ((5,6), 4.2), ((6,6), 7.84)
   */
  public void generateGramAnswer() {
    int coefSize = _coeffNames1.size()+1;
    _equalGramContr = new double[coefSize][coefSize];
    _lessGramContr = new double[coefSize][coefSize];
    _cGEqual = new ConstrainedGLMUtils.ConstraintsGram[3];
    _cGEqual[0] = genOneGram(new ConstrainedGLMUtils.CoefIndices[]{new ConstrainedGLMUtils.CoefIndices(0,0),
            new ConstrainedGLMUtils.CoefIndices(0,2), new ConstrainedGLMUtils.CoefIndices(2,2)}, 
            new double[]{1, -0.3, 0.09}, true);
    _equalGramContr[0][0] += 1;
    _equalGramContr[0][2] += -0.3;
    _equalGramContr[2][0] += -0.3;
    _equalGramContr[2][2] += 0.09;
    _cGEqual[1] = genOneGram(new ConstrainedGLMUtils.CoefIndices[]{new ConstrainedGLMUtils.CoefIndices(3,3),
            new ConstrainedGLMUtils.CoefIndices(3,4), new ConstrainedGLMUtils.CoefIndices(4,4)}, 
            new double[]{121, -26.4, 5.76}, true);
    _equalGramContr[3][3] += 121;
    _equalGramContr[3][4] += -26.4;
    _equalGramContr[4][3] += -26.4;
    _equalGramContr[4][4] += 5.76;
    _cGEqual[2] = genOneGram(new ConstrainedGLMUtils.CoefIndices[]{new ConstrainedGLMUtils.CoefIndices(9,9),
            new ConstrainedGLMUtils.CoefIndices(9,10), new ConstrainedGLMUtils.CoefIndices(10,10)}, 
            new double[]{86.49, -12.09, 1.69}, true);
    _equalGramContr[9][9] += 86.49;
    _equalGramContr[9][10] += -12.09;
    _equalGramContr[10][9] += -12.09;
    _equalGramContr[10][10] += 1.69;
    _cGLess = new ConstrainedGLMUtils.ConstraintsGram[3];
    _cGLess[0] = genOneGram(new ConstrainedGLMUtils.CoefIndices[]{new ConstrainedGLMUtils.CoefIndices(0,0),
            new ConstrainedGLMUtils.CoefIndices(0,1), new ConstrainedGLMUtils.CoefIndices(1,1)}, 
            new double[]{0.16,0.08,0.04}, _lessThanConstr[0]._active);
    if (_lessThanConstr[0]._active) {
      _lessGramContr[0][0] += 0.16;
      _lessGramContr[0][1] += 0.08;
      _lessGramContr[1][0] += 0.08;
      _lessGramContr[1][1] += 0.04;
    }
    _cGLess[1] = genOneGram(new ConstrainedGLMUtils.CoefIndices[]{new ConstrainedGLMUtils.CoefIndices(2,2),
            new ConstrainedGLMUtils.CoefIndices(2,3), new ConstrainedGLMUtils.CoefIndices(3,3)}, 
            new double[]{100,-40,16}, _lessThanConstr[1]._active);
    if (_lessThanConstr[1]._active) {
      _lessGramContr[2][2] += 100;
      _lessGramContr[2][3] += -40;
      _lessGramContr[3][2] += -40;
      _lessGramContr[3][3] += 16;
    }
    _cGLess[2] = genOneGram(new ConstrainedGLMUtils.CoefIndices[]{new ConstrainedGLMUtils.CoefIndices(5,5),
            new ConstrainedGLMUtils.CoefIndices(5,6), new ConstrainedGLMUtils.CoefIndices(6,6)}, 
            new double[]{2.25,4.2,7.84}, _lessThanConstr[2]._active);
    if (_lessThanConstr[2]._active) {
      _lessGramContr[5][5] += 2.25;
      _lessGramContr[5][6] += 4.2;
      _lessGramContr[6][5] += 4.2;
      _lessGramContr[6][6] += 7.84;
    }
  }
  
  public ConstrainedGLMUtils.ConstraintsGram genOneGram(ConstrainedGLMUtils.CoefIndices[] coefIndices, double[] vals, boolean active) {
    ConstrainedGLMUtils.ConstraintsGram oneG = new ConstrainedGLMUtils.ConstraintsGram();
    int numV = vals.length;
    for (int index=0; index<numV; index++)
      oneG._coefIndicesValue.put(coefIndices[index], vals[index]);
    oneG._active = active;
    return oneG;
  }
  
  public ConstrainedGLMUtils.ConstraintsDerivatives genOneCDerivative(int[] indices, double[] vals, boolean active) {
    ConstrainedGLMUtils.ConstraintsDerivatives oneD = new ConstrainedGLMUtils.ConstraintsDerivatives(active);
    int numItem = indices.length;
    for (int index=0; index<numItem; index++)
      oneD._constraintsDerivative.put(indices[index], vals[index]);
    return oneD;
  }

  public void generateConstraint4FrameNAnswer() {
    int coefLen = _coeffNames1.size();
    _betaConstraint2 =
            new TestFrameBuilder()
                    .withColNames("names", "lower_bounds", "upper_bounds")
                    .withVecTypes(T_STR, T_NUM, T_NUM)
                    .withDataForCol(0, new String[] {_coeffNames1.get(0), _coeffNames1.get(1),
                            _coeffNames1.get(coefLen-4), _coeffNames1.get(coefLen-3), _coeffNames1.get(coefLen-2)})
                    .withDataForCol(1, new double [] {1.0, -1.0, Double.NEGATIVE_INFINITY, 0.0, 0.1})
                    .withDataForCol(2, new double[] {10.0, Double.POSITIVE_INFINITY, 8.0, 2.0, 0.1}).build();
    Scope.track(_betaConstraint2);
    _linearConstraint4 = new TestFrameBuilder()
            .withColNames("names", "values", "types", "constraint_numbers")
            .withVecTypes(T_STR, T_NUM, T_STR, T_NUM)
            .withDataForCol(0, new String[] {_coeffNames1.get(0), _coeffNames1.get(1), _coeffNames1.get(3),
                    "constant", _coeffNames1.get(8), _coeffNames1.get(23), _coeffNames1.get(24), _coeffNames1.get(25),
                    _coeffNames1.get(19), _coeffNames1.get(20), _coeffNames1.get(21), _coeffNames1.get(22), "constant",
                    _coeffNames1.get(4), _coeffNames1.get(5), _coeffNames1.get(6), "constant", _coeffNames1.get(6),
                    _coeffNames1.get(33), _coeffNames1.get(7), _coeffNames1.get(24), _coeffNames1.get(25), "constant",
                    _coeffNames1.get(1), _coeffNames1.get(coefLen-3), "constant", _coeffNames1.get(0), _coeffNames1.get(1), "constant"})
            .withDataForCol(1, new double [] {-0.3, 0.5, 1.0, -3.0, 3, -4, 0.5, 0.1, -0.2, 2.0, -0.1, -0.4,
                    0.8, 0.1, -0.5, 0.7, -1.1, 2.0, 0.5, -0.3, 0.5, -1.5, -0.3, -1.0, 1.0, -9.0,-1, -1, 0})
            .withDataForCol(2, new String[] {"lessthanequal", "lessthanequal", "lessthanequal", "lessthanequal",
                    "lessthanequal", "lessthanequal", "lessthanequal", "equal", "equal", "lessthanequal",
                    "lessthanequal", "lessthanequal", "lessthanequal", "equal", "equal", "equal", "equal", "equal",
                    "equal", "equal", "equal", "equal", "equal", "lessthanequal", "lessthanequal", "lessthanequal", 
                    "lessthanequal", "lessthanequal", "lessthanequal"})
            .withDataForCol(3, new int[]{0, 0, 0, 0, 1, 1, 1, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 6, 6,
                    6, 7, 7 ,7, 8, 8, 8}).build();
    Scope.track(_linearConstraint4);
  }

  public void generateConstraint2FrameNAnswer(Frame train) {
    // linear constraints:
    // -0.3*beta0+0.5*beta1+1*beta3-3<=0.0;
    // 3*beta8-4*beta33+0.5*beta34 <= 0.0;
    // 0.1*beta34-0.2*beta35 = 0.0;
    // 2*beta30-0.1*beta31-0.4*beta32+0.8 <= 0.0
    // 0.1*beta4 -0.5*beta5 +0.7*beta6 -1.1 = 0.0;
    // 2*beta6 +0.5beta28 -0.3*beta7 = 0.0;
    // 0.5*beta33 -1.5*beta35 -0.3 = 0.0;
    _linearConstraint2 = new TestFrameBuilder()
            .withColNames("names", "values", "types", "constraint_numbers")
            .withVecTypes(T_STR, T_NUM, T_STR, T_NUM)
            .withDataForCol(0, new String[] {_coeffNames1.get(0), _coeffNames1.get(1), _coeffNames1.get(3),
                    "constant",
                    _coeffNames1.get(8), _coeffNames1.get(33), _coeffNames1.get(34), 
                    _coeffNames1.get(34), _coeffNames1.get(35),
                    _coeffNames1.get(30), _coeffNames1.get(31), _coeffNames1.get(32), "constant",
                    _coeffNames1.get(4), _coeffNames1.get(5), _coeffNames1.get(6), "constant", 
                    _coeffNames1.get(6), _coeffNames1.get(28), _coeffNames1.get(7), 
                    _coeffNames1.get(33), _coeffNames1.get(35), "constant"})
            .withDataForCol(1, new double [] {-0.3, 0.5, 1.0, -3.0,
                    3, -4, 0.5, 
                    0.1, -0.2, 
                    2.0, -0.1, -0.4, 0.8, 
                    0.1, -0.5, 0.7, -1.1, 
                    2.0, 0.5, -0.3, 
                    0.5, -1.5, -0.3})
            .withDataForCol(2, new String[] {"lessthanequal", "lessthanequal", "lessthanequal", "lessthanequal",
                    "lessthanequal", "lessthanequal", "lessthanequal", 
                    "equal", "equal", 
                    "lessthanequal", "lessthanequal", "lessthanequal", "lessthanequal", 
                    "equal", "equal", "equal", "equal", 
                    "equal", "equal", "equal", 
                    "equal", "equal", "equal"})
            .withDataForCol(3, new int[]{0, 0, 0, 0, 
                    1, 1, 1, 
                    2, 2, 
                    3, 3, 3, 3, 
                    4, 4, 4, 4, 
                    5, 5, 5, 
                    6, 6, 6}).build();
    Scope.track(_linearConstraint2);
    _equalityNames2 = new String[][]{{_coeffNames1.get(34), _coeffNames1.get(35), "constant"},
            {_coeffNames1.get(4), _coeffNames1.get(5), _coeffNames1.get(6), "constant"},
            {_coeffNames1.get(6), _coeffNames1.get(28), _coeffNames1.get(7), "constant"},
            {_coeffNames1.get(33), _coeffNames1.get(35), "constant"}};
    _equalityValues2 = new double[][]{{0.1, -0.2, 0.0}, {0.1, -0.5, 0.7, -1.1}, {2, 0.5, -0.3, 0.0},
            {0.5, -1.5, -0.3}};
    _equalityValuesStandard2 = new double[][]{{0.1/train.vec(_coeffNames1.get(34)).sigma(), -0.2/train.vec(_coeffNames1.get(35)).sigma(), 0.0},
            {0.1, -0.5, 0.7, -1.1},
            {2, 0.5/train.vec(_coeffNames1.get(28)).sigma(), -0.3, 0.0},
            {0.5/train.vec(_coeffNames1.get(33)).sigma(), -1.5/train.vec(_coeffNames1.get(35)).sigma(), -0.3}};
    _lessThanNames2 = new String[][]{{_coeffNames1.get(0), _coeffNames1.get(1), _coeffNames1.get(3), "constant"}, 
            {_coeffNames1.get(8), _coeffNames1.get(33), _coeffNames1.get(34), "constant"},
            {_coeffNames1.get(30), _coeffNames1.get(31), _coeffNames1.get(32), "constant",}};
    _lessThanValues2 = new double[][]{{-0.3, 0.5, 1, -3}, {3, -4, 0.5, 0.0}, {2, -0.1, -0.4, 0.8}};
    _lessThanValuesStandard2 = new double[][]{{-0.3, 0.5, 1, -3},
            {3, -4/train.vec(_coeffNames1.get(33)).sigma(), 0.5/train.vec(_coeffNames1.get(34)).sigma(), 0.0},
            {2/train.vec(_coeffNames1.get(30)).sigma(), -0.1/train.vec(_coeffNames1.get(31)).sigma(),
                    -0.4/train.vec(_coeffNames1.get(32)).sigma(), 0.8}};
  }

  public void generateConstraint3FrameNAnswer() {
    // Constraints in the linear_constraints, 
    // a. -0.3*beta_0+0.5*beta_1+1*beta_3-3 <= 0; 
    // b. 3*beta_8-4*beta_26+0.5*beta_27 <= 0, 
    // c.0.1*beta_28-0.2*beta_29==0, 
    // d. 2*beta_30-0.1*beta_31-0.4*beta_32+0.8 <= 0;
    // e. 0.1*beta_4-0.5*beta_5+0.7*beta_6-1.1 == 0; 
    // f. 2*beta_6+0.5*beta_33-0.3*beta_7 == 0 
    // g. 0.5*beta_26-1.5*beta_28-0.3 == 0  
    // h. 4*beta_30-0.2*beta_31-0.8*beta_32+1.6 <= 0; redundant to constraint d
    // i. 1.5*beta_26-4.5*beta_28-0.9 == 0; redundant to constraint g
    _linearConstraint3 = new TestFrameBuilder()
            .withColNames("names", "values", "types", "constraint_numbers")
            .withVecTypes(T_STR, T_NUM, T_STR, T_NUM)
            .withDataForCol(0, new String[] {_coeffNames1.get(0), _coeffNames1.get(1), _coeffNames1.get(3), "constant", 
                    _coeffNames1.get(8), _coeffNames1.get(26), _coeffNames1.get(27), 
                    _coeffNames1.get(28), _coeffNames1.get(29), 
                    _coeffNames1.get(30), _coeffNames1.get(31), _coeffNames1.get(32), "constant",
                    _coeffNames1.get(4), _coeffNames1.get(5), _coeffNames1.get(6), "constant",
                    _coeffNames1.get(6), _coeffNames1.get(33), _coeffNames1.get(7), 
                    _coeffNames1.get(26), _coeffNames1.get(28), "constant",
                    _coeffNames1.get(30), _coeffNames1.get(31), _coeffNames1.get(32), "constant",
                    _coeffNames1.get(26), _coeffNames1.get(28), "constant"})
            .withDataForCol(1, new double [] {-0.3, 0.5, 1.0, -3.0, 
                    3, -4, 0.5, 
                    0.1, -0.2, 
                    2.0, -0.1, -0.4, 0.8, 
                    0.1, -0.5, 0.7, -1.1,
                    2.0, 0.5, -0.3, 
                    0.5, -1.5, -0.3, 
                    4, -0.2, -0.8, 1.6, 
                    1.5, -4.5, -0.9})
            .withDataForCol(2, new String[] {"lessthanequal", "lessthanequal", "lessthanequal", "lessthanequal",
                    "lessthanequal", "lessthanequal", "lessthanequal", 
                    "equal", "equal", 
                    "lessthanequal", "lessthanequal", "lessthanequal", "lessthanequal", 
                    "equal", "equal", "equal", "equal", 
                    "equal", "equal", "equal", 
                    "equal", "equal", "equal", 
                    "lessthanequal", "lessthanequal", "lessthanequal", "lessthanequal", 
                    "equal", "equal", "equal"})
            .withDataForCol(3, new int[]{0, 0, 0, 0, 
                    1, 1, 1, 
                    2, 2, 
                    3, 3, 3, 3, 
                    4, 4, 4, 4, 
                    5, 5, 5, 
                    6, 6, 6, 
                    7, 7, 7, 7, 
                    8, 8, 8}).build();
    Scope.track(_linearConstraint2);
  }
  
  public void generateConstraint1FrameNAnswer(Frame train) {
    int coefLen = _coeffNames1.size()-1;
    // there are 4 constraints in the beta constraints: 1.0 <= beta0 <= 10.0, -1.0 <= beta1, betacoefLen <= 8.0,
    //  0.1 == betacoefLen-2 == 0.1.  This will be translated into the following standard
    // form: 1.0-beta0 <= 0; beta0-10.0 <= 0, -1.0 -beta1 <= 0, betacoefLen-8.0 <= 0,  betacoefLen-2-0.1 == 0.
    _betaConstraint1 =
            new TestFrameBuilder()
                    .withColNames("names", "lower_bounds", "upper_bounds")
                    .withVecTypes(T_STR, T_NUM, T_NUM)
                    .withDataForCol(0, new String[] {_coeffNames1.get(0), _coeffNames1.get(1),
                            _coeffNames1.get(coefLen-4), _coeffNames1.get(coefLen-3)})
                    .withDataForCol(1, new double [] {1.0, -1.0, Double.NEGATIVE_INFINITY, 0.1})
                    .withDataForCol(2, new double[] {10.0, Double.POSITIVE_INFINITY, 8.0, 0.1}).build();
    Scope.track(_betaConstraint1);
    // there are two constraints in the linear_constraints, the first one is 2*beta_0+0.5*beta_5 -1<= 0, the second 
    // one is 0.5*beta_36-1.5*beta_38 == 0
    _linearConstraint1 = new TestFrameBuilder()
            .withColNames("names", "values", "types", "constraint_numbers")
            .withVecTypes(T_STR, T_NUM, T_STR, T_NUM)
            .withDataForCol(0, new String[] {_coeffNames1.get(0), _coeffNames1.get(5), "constant",
                    _coeffNames1.get(34), _coeffNames1.get(35)})
            .withDataForCol(1, new double [] {2, 0.5, -1, 0.5, -1.5})
            .withDataForCol(2, new String[] {"lessthanequal", "lessthanequal", "lessthanequal", "equal", "equal"})
            .withDataForCol(3, new int[]{0,0,0,1,1}).build();
    Scope.track(_linearConstraint1);
    // form correct constraints names and values:
    _betaConstraintNames1Equal = new String[][]{{_coeffNames1.get(coefLen-3), "constant"}};
    _betaConstraintValStandard1Equal = new double[][]{{1,-0.1*train.vec(_coeffNames1.get(coefLen-3)).sigma()}};
    _betaConstraintVal1Equal = new double[][]{{1,-0.1}};
    _betaConstraintNames1Less = new String[][]{{_coeffNames1.get(0), "constant"}, {_coeffNames1.get(0), "constant"},
            {_coeffNames1.get(1), "constant"}, {_coeffNames1.get(coefLen-4), "constant"}};
    _betaConstraintValStandard1Less = new double[][]{{-1,1}, {1,-10}, {-1,-1},
            {1.0,-8*train.vec(_coeffNames1.get(coefLen-4)).sigma()}};
    _betaConstraintVal1Less = new double[][]{{-1,1}, {1,-10}, {-1,-1}, {1,-8}};
    _equalityNames1 = new String[][]{{_coeffNames1.get(34), _coeffNames1.get(35), "constant"}};
    _equalityValuesStandard1 = new double[][]{{0.5/train.vec(_coeffNames1.get(34)).sigma(),
            -1.5/train.vec(_coeffNames1.get(35)).sigma(), 0.0}};
    _equalityValues1 = new double[][]{{0.5, -1.5, 0.0}};

    _lessThanNames1 = new String[][]{{_coeffNames1.get(0), _coeffNames1.get(5), "constant"}};
    _lessThanValuesStandard1 = new double[][]{{2, 0.5, -1}}; // no normalization, enum cols
    _lessThanValues1 = new double[][]{{2, 0.5, -1}};
  }
  
  public List<String> transformFrameCreateCoefNames(Frame train) {
    for (int colInd : _catCol)
      train.replace((colInd), train.vec(colInd).toCategoricalVec()).remove();
    DKV.put(train);
    GLMModel.GLMParameters params = new GLMModel.GLMParameters(gaussian);
    params._standardize=true;
    params._response_column = "C21";
    params._max_iterations = 0;
    params._lambda = new double[]{0.0};
    params._train = train._key;
    GLMModel glm = new GLM(params).trainModel().get();
    Scope.track_generic(glm);
    return Stream.of(glm._output._coefficient_names).collect(Collectors.toList());
  }
  
  @After
  public void teardown() {
    Scope.exit();
  }


  
  public void assertCorrectDerivatives(ConstrainedGLMUtils.ConstraintsDerivatives[] actual, 
                                       ConstrainedGLMUtils.ConstraintsDerivatives[] expected) {
    int numV = actual.length;
    assertTrue("expected array length: " + expected.length + " and actual array length: " + actual.length 
             + ".  They are different", actual.length == numV);
    for (int index=0; index<numV; index++) {
      ConstrainedGLMUtils.ConstraintsDerivatives cd1 = actual[index];
      ConstrainedGLMUtils.ConstraintsDerivatives cd2 = expected[index];
      assertTrue("Expected HashMap length: " + cd2._constraintsDerivative.size() + 
              ". Actual HashMap length: " + cd1._constraintsDerivative.size(), 
              cd2._constraintsDerivative.size()==cd1._constraintsDerivative.size());
      assertTrue(cd1._constraintsDerivative.entrySet().stream().allMatch(e -> Math.abs(e.getValue()-cd2._constraintsDerivative.get(e.getKey()))<1e-6));
    }
  }
  
  // test and make sure the contributions from constraints to the gradient calculations are correct.  This one does not
  // take into account the contribution from the penalty terms.
  @Test
  public void testConstraintsDerivativesSum() {
    GLM.GLMGradientInfo ginfo = new GLM.GLMGradientInfo(0,0, new double[_equalGradContr.length]);
    addConstraintGradient(_lambdaE, _cdEqual, ginfo);
    addPenaltyGradient(_cdEqual, _equalityConstr, ginfo, _ck);
    checkArrays(_equalGradContr, ginfo._gradient, 1e-6);
    ginfo = new GLM.GLMGradientInfo(0,0, new double[_lessGradContr.length]);
    addConstraintGradient(_lambdaL, _cdLess, ginfo);
    addPenaltyGradient(_cdLess, _lessThanConstr, ginfo, _ck);
    checkArrays(_lessGradContr, ginfo._gradient, 1e-6);
  }
  
  // This test will form a double[][] matrix which should capture the contributions from all the constraints.
  @Test
  public void testGramConstraintsSum() {
    double[][] equalGramC = sumGramConstribution(_cGEqual, _coeffNames1.size()+1);
    double[][] lessGramC = sumGramConstribution(_cGLess, _coeffNames1.size()+1);
    checkDoubleArrays(equalGramC, _equalGramContr, 1e-6);
    checkDoubleArrays(lessGramC, _lessGramContr, 1e-6);
  }

  // make sure constraints (the penalty part) contributions to the Gram matrix is calculated correctly.  The final 
  // result should be an array of ConstraintsGram.
  @Test
  public void testConstraintsGram() {
    ConstrainedGLMUtils.ConstraintsGram[] gramEqual = calGram(_cdEqual);
    ConstrainedGLMUtils.ConstraintsGram[] gramLess = calGram( _cdLess);
    assertCorrectGrams(gramEqual, _cGEqual);
    assertCorrectGrams(gramLess, _cGLess);
  }
  
  public void assertCorrectGrams(ConstrainedGLMUtils.ConstraintsGram[] actual, ConstrainedGLMUtils.ConstraintsGram[] expected) {
    int numV = actual.length;
    assertTrue("expected array length: " + expected.length + " and actual array length: " + actual.length
            + ".  They are different", actual.length == numV);
    for (int index=0; index<numV; index++) {
      ConstrainedGLMUtils.ConstraintsGram cd1 = actual[index];
      ConstrainedGLMUtils.ConstraintsGram cd2 = expected[index];
      assertTrue("Expected HashMap length: " + cd2._coefIndicesValue.size() +
                      ". Actual HashMap length: " + cd1._coefIndicesValue.size(),
              cd2._coefIndicesValue.size()==cd1._coefIndicesValue.size());
      assertCorrectGramMaps(cd2._coefIndicesValue, cd1._coefIndicesValue);
    }
  }
  
  public void assertCorrectGramMaps(IcedHashMap<ConstrainedGLMUtils.CoefIndices, Double> actual, 
                                    IcedHashMap<ConstrainedGLMUtils.CoefIndices, Double> expected) {
    List<CoefIndices> actualKey = actual.keySet().stream().collect(Collectors.toList());
    List<CoefIndices> expectedKey = expected.keySet().stream().collect(Collectors.toList());
    int numK = actualKey.size();
    assertTrue(numK == expectedKey.size());

    for (CoefIndices oneKey : actualKey) {
      int index = expectedKey.indexOf(oneKey);
      assertTrue(index >= 0);
      double expectedValue = expected.get(expectedKey.get(index));
      assertTrue("Expected value: " + actual.get(oneKey) + ", Actual value: " + expectedValue, 
              Math.abs(actual.get(oneKey) - expectedValue) < 1e-6);
    }
  }

  // linear constraints with two duplicated constraints
  @Test
  public void testDuplicateLinearConstraints() {
    Scope.enter();
    try {
      Frame train = parseAndTrackTestFile("smalldata/glm_test/gaussian_20cols_10000Rows.csv");
      transformFrameCreateCoefNames(train);
      GLMModel.GLMParameters params = new GLMModel.GLMParameters(gaussian);
      params._standardize = false;
      params._response_column = "C21";
      params._solver = IRLSM;
      params._train = train._key;
      params._max_iterations = 1;
      params._expose_constraints = true;
      params._linear_constraints = _linearConstraint3._key;
      params._lambda = new double[]{0};
      GLMModel glm2 = new GLM(params).trainModel().get();
      Scope.track_generic(glm2);
      assert 1==2 : "Should have thrown an error due to duplicated constraints.";
    } catch(IllegalArgumentException ex) {
      assert ex.getMessage().contains("redundant linear constraints:") : "Wrong error message.  Error should be about" +
              " redundant linear constraints";
    } finally {
      Scope.exit();
    }
  }

  // make sure duplicated bete or linear constraints specified are caught.
  @Test
  public void testDuplicateBetaLinearConstraints() {
    Scope.enter();
    try {
      Frame train = parseAndTrackTestFile("smalldata/glm_test/gaussian_20cols_10000Rows.csv");
      transformFrameCreateCoefNames(train);
      GLMModel.GLMParameters params = new GLMModel.GLMParameters(gaussian);
      params._standardize = true;
      params._response_column = "C21";
      params._solver = IRLSM;
      params._train = train._key;
      params._max_iterations = 1;
      params._expose_constraints = true;
      params._linear_constraints = _linearConstraint4._key;
      params._beta_constraints = _betaConstraint2._key;
      params._lambda = new double[]{0};
      GLMModel glm2 = new GLM(params).trainModel().get();
      Scope.track_generic(glm2);
      assert 1==2 : "Should have thrown an error due to duplicated constraints.";
    } catch(IllegalArgumentException ex) {
      assert ex.getMessage().contains("redundant linear constraints") : "Wrong error message.  Error should be about" +
              " redundant linear constraints";
    } finally {
      Scope.exit();
    }
  }

 
  // make sure correct constraint matrix is formed after extracting constraints from linear constraints
  @Test
  public void testLinearConstraintMatrix() {
    Scope.enter();
    try {
      Frame train = parseAndTrackTestFile("smalldata/glm_test/gaussian_20cols_10000Rows.csv");
      transformFrameCreateCoefNames(train);
      GLMModel.GLMParameters params = new GLMModel.GLMParameters(gaussian);
      params._standardize = false;
      params._response_column = "C21";
      params._solver = IRLSM;
      params._train = train._key;
      params._max_iterations = 1;
      params._expose_constraints = true;
      params._linear_constraints = _linearConstraint2._key;
      params._lambda = new double[]{0};
      GLMModel glm2 = new GLM(params).trainModel().get();
      Scope.track_generic(glm2);
      // check that constraint matrix is extracted correctly
      List<String> constraintNames = Arrays.stream(glm2._output._constraintCoefficientNames).collect(Collectors.toList());
      double[][] initConstraintMatrix = glm2._output._initConstraintMatrix;
      // check rows from beta constraints
      int rowIndex = 0;
      int[] compare = new int[]{1, 1, 1, 1};
      assertCorrectConstraintMatrix(constraintNames, initConstraintMatrix, _equalityNames2, _equalityValues2, 
              rowIndex, compare);
      // check row from linear constraints with lessThanEqualTo
      rowIndex += ArrayUtils.sum(compare);
      compare = new int[]{1, 1, 1};
      assertCorrectConstraintMatrix(constraintNames, initConstraintMatrix, _lessThanNames2, _lessThanValues2,
              rowIndex, compare);
    } finally {
      Scope.exit();
    }
  }
  
  // make sure correct constraint matrix is formed after extracting constraints from beta constraints and linear
  // constraints
  @Test
  public void testBetaLinearConstraintMatrix() {
    Scope.enter();
    try {
      Frame train = parseAndTrackTestFile("smalldata/glm_test/gaussian_20cols_10000Rows.csv");
      transformFrameCreateCoefNames(train);
      GLMModel.GLMParameters params = new GLMModel.GLMParameters(gaussian);
      params._standardize = true;
      params._response_column = "C21";
      params._solver = IRLSM;
      params._train = train._key;
      // build the beta_constraints
      Frame beta_constraints = _betaConstraint1;
      Frame linear_constraints = _linearConstraint1;
      params._max_iterations = 1;
      params._expose_constraints = true;
      params._beta_constraints = beta_constraints._key;
      params._linear_constraints = linear_constraints._key;
      params._lambda = new double[]{0};
      GLMModel glm2 = new GLM(params).trainModel().get();
      Scope.track_generic(glm2);
      // check that constraint matrix is extracted correctly
      List<String> constraintNames = Arrays.stream(glm2._output._constraintCoefficientNames).collect(Collectors.toList());
      double[][] initConstraintMatrix = glm2._output._initConstraintMatrix;
      // check rows from beta constraints
      int rowIndex = 0;
      int[] compare = new int[]{1};
      assertCorrectConstraintMatrix(constraintNames, initConstraintMatrix, _betaConstraintNames1Equal, 
              _betaConstraintValStandard1Equal, rowIndex, compare);
      // check rows from linear constraints with equality
      rowIndex += ArrayUtils.sum(compare);
      compare = new int[]{1, 0, 1, 1};
      assertCorrectConstraintMatrix(constraintNames, initConstraintMatrix, _betaConstraintNames1Less,
              _betaConstraintValStandard1Less, rowIndex, compare);
      // check rows from linear constraints with equality
      rowIndex += ArrayUtils.sum(compare);
      compare = new int[]{1};
      assertCorrectConstraintMatrix(constraintNames, initConstraintMatrix, _equalityNames1, _equalityValuesStandard1,
              rowIndex, compare);
      // check row from linear contraints with lessThanEqualTo
      rowIndex += ArrayUtils.sum(compare);
      compare = new int[]{1};
      assertCorrectConstraintMatrix(constraintNames, initConstraintMatrix, _lessThanNames1, _lessThanValuesStandard1,
              rowIndex, compare);
    } finally {
      Scope.exit();
    }
  }
  
  public void assertCorrectConstraintMatrix(List<String> constraintNames, double[][] constraintMatrix,
                                            String[][] origNames, double[][] originalValues, int rowStart, 
                                            int[] compare) {
    int numConstraints = origNames.length;
    int count = 0;
    for (int index=0; index<numConstraints; index++) {
      if (compare[index] > 0) {
        int rowIndex = count + rowStart;
        String[] constNames = origNames[index];
        double[] constValues = originalValues[index];
        int numNames = constNames.length;
        for (int index2 = 0; index2 < numNames; index2++) {
          int cNamesIndex = constraintNames.indexOf(constNames[index2]);
          assertTrue("Expected value: " + constValues[index2] + " for constraint " + constNames[index2] + " but actual: "
                  + constraintMatrix[rowIndex][cNamesIndex], Math.abs(constraintMatrix[rowIndex][cNamesIndex] - constValues[index2]) < EPS);
        }
        count++;
      }
    }
  }
  
  // make sure we can get coefficient names without building a GLM model.  We compare the coefficient names
  // obtained without building a model and with building a model.  They should be the same.
  @Test
  public void testCoefficientNames() {
    Scope.enter();
    try {
      Frame train = parseAndTrackTestFile("smalldata/glm_test/gaussian_20cols_10000Rows.csv");
      transformFrameCreateCoefNames(train);
      GLMModel.GLMParameters params = new GLMModel.GLMParameters(gaussian);
      params._standardize=true;
      params._response_column = "C21";
      params._max_iterations = 0;
      params._train = train._key;
      params._lambda = new double[]{0};
      GLMModel glm = new GLM(params).trainModel().get();
      Scope.track_generic(glm);
      List<String> coefNames = Stream.of(glm._output._coefficient_names).collect(Collectors.toList()); ;
  
      params._max_iterations = 1;
      GLMModel glm2 = new GLM(params).trainModel().get();
      Scope.track_generic(glm2);
      String[] coeffNames2 = glm2.coefficients().keySet().toArray(new String[0]);
      
      for (String oneName : coeffNames2)
        assertTrue(coefNames.contains(oneName));
    } finally {
      Scope.exit();
    }
  }

  // test constraints specified in beta_constraint and linear constraints and extracted correctly with 
  // standardization.
  @Test
  public void testConstraintsInBetaLinearStandard() {
    Scope.enter();
    try {
      Frame train = parseAndTrackTestFile("smalldata/glm_test/gaussian_20cols_10000Rows.csv");
      transformFrameCreateCoefNames(train);
      GLMModel.GLMParameters params = new GLMModel.GLMParameters(gaussian);
      params._standardize = true;
      params._response_column = "C21";
      params._solver = IRLSM;
      params._train = train._key;
      // build the beta_constraints
      Frame beta_constraints = _betaConstraint1;
      Frame linear_constraints = _linearConstraint1;
      params._max_iterations = 1;
      params._expose_constraints = true;
      params._beta_constraints = beta_constraints._key;
      params._linear_constraints = linear_constraints._key;
      params._lambda = new double[]{0};
      GLMModel glm2 = new GLM(params).trainModel().get();
      Scope.track_generic(glm2);
      // check constraints from betaConstraints are extracted properly
      ConstrainedGLMUtils.LinearConstraints[] equalConstraintstBeta = glm2._output._equalityConstraintsBeta;
      ConstrainedGLMUtils.LinearConstraints[] lessThanEqualToConstraintstBeta = glm2._output._lessThanEqualToConstraintsBeta;

      assertTrue("Expected constraint length: "+ _betaConstraintValStandard1Equal.length+" but" +
              " actual is "+equalConstraintstBeta.length,_betaConstraintNames1Equal.length == equalConstraintstBeta.length);
      assertTrue("Expected constraint length: "+ _betaConstraintNames1Less.length+" but" +
              " actual is "+lessThanEqualToConstraintstBeta.length,_betaConstraintNames1Less.length == lessThanEqualToConstraintstBeta.length);
      assertCorrectConstraintContent(_betaConstraintNames1Equal, _betaConstraintValStandard1Equal, equalConstraintstBeta);
      assertCorrectConstraintContent(_betaConstraintNames1Less, _betaConstraintValStandard1Less, lessThanEqualToConstraintstBeta);
      
      // check constraints from linear constraints are extracted properly
      // check equality constraint
      assertCorrectConstraintContent(_equalityNames1, _equalityValuesStandard1, glm2._output._equalityConstraintsLinear);
      // check lessThanEqual to constraint
      assertCorrectConstraintContent(_lessThanNames1, _lessThanValuesStandard1, glm2._output._lessThanEqualToConstraintsLinear);
    } finally {
      Scope.exit();
    }
  }

  // test constraints specified in beta_constraint and linear constraints and extracted correctly without 
  // standardization.
  @Test
  public void testConstraintsInBetaLinear() {
    Scope.enter();
    try {
      Frame train = parseAndTrackTestFile("smalldata/glm_test/gaussian_20cols_10000Rows.csv");
      transformFrameCreateCoefNames(train);
      List<String> coeffNames = _coeffNames1;
      // build the beta_constraints
      int coefLen = coeffNames.size()-1;
      Frame beta_constraints = _betaConstraint1;
      // there are two constraints in the linear_constraints, the first one is 2*beta_0+0.5*beta_5 -1<= 0, the second 
      // one is 0.5*beta_36-1.5*beta_38 == 0
      Frame linear_constraints = _linearConstraint1;
      GLMModel.GLMParameters params = new GLMModel.GLMParameters(gaussian);
      params._standardize=false;
      params._response_column = "C21";
      params._solver = IRLSM;
      params._train = train._key;
      params._max_iterations = 1;
      params._expose_constraints = true;
      params._beta_constraints = beta_constraints._key;
      params._linear_constraints = linear_constraints._key;
      params._lambda = new double[]{0};
      GLMModel glm2 = new GLM(params).trainModel().get();
      Scope.track_generic(glm2);
      ConstrainedGLMUtils.LinearConstraints[] fromBetaConstraintE = glm2._output._equalityConstraintsBeta;
      assertTrue("Expected constraint length: "+
              _betaConstraintNames1Equal.length+" but actual is "+fromBetaConstraintE.length, 
              _betaConstraintNames1Equal.length == fromBetaConstraintE.length);
      assertCorrectConstraintContent(_betaConstraintNames1Equal, _betaConstraintVal1Equal, fromBetaConstraintE);

      ConstrainedGLMUtils.LinearConstraints[] fromBetaConstraintL = glm2._output._lessThanEqualToConstraintsBeta;
      assertTrue("Expected constraint length: "+ _betaConstraintNames1Less.length + " but actual is " + 
              fromBetaConstraintL.length,  _betaConstraintNames1Less.length == fromBetaConstraintL.length);
      assertCorrectConstraintContent(_betaConstraintNames1Less, _betaConstraintVal1Less, fromBetaConstraintL);
      
      // check constraints from linear constraints are extracted properly
      // check equality constraint
      assertCorrectConstraintContent(_equalityNames1, _equalityValues1, glm2._output._equalityConstraintsLinear);
      // check lessThanEqual to constraint
      assertCorrectConstraintContent(_lessThanNames1, _lessThanValues1, glm2._output._lessThanEqualToConstraintsLinear);
    } finally {
      Scope.exit();
    }
  }
  

  // test constraints specified only in linear_constraint and extracted correctly without standardization
  @Test
  public void testConstraintsInLinear() {
    Scope.enter();
    try {
      Frame train = parseAndTrackTestFile("smalldata/glm_test/gaussian_20cols_10000Rows.csv");
      transformFrameCreateCoefNames(train);
      Frame linear_constraints = _linearConstraint2;
      
      GLMModel.GLMParameters params = new GLMModel.GLMParameters(gaussian);
      params._standardize=false;
      params._response_column = "C21";
      params._solver = IRLSM;
      params._train = train._key;
      params._max_iterations = 1;
      params._expose_constraints = true;
      params._linear_constraints = linear_constraints._key;
      params._lambda = new double[]{0};
      GLMModel glm2 = new GLM(params).trainModel().get();
      Scope.track_generic(glm2);
      // check constraints from linear constraints are extracted properly
      // check equality constraint
      assertCorrectConstraintContent(_equalityNames2, _equalityValues2, glm2._output._equalityConstraintsLinear);
      // check lessThanEqual to constraint
      assertCorrectConstraintContent(_lessThanNames2, _lessThanValues2, glm2._output._lessThanEqualToConstraintsLinear);
    } finally {
      Scope.exit();
    }
  }
  
  // this test will make sure that the find zero columns function is working
  @Test
  public void testFindDropZeroColumns() {
    Matrix initMat = Matrix.random(11, 11);
    double[][] doubleValsOrig = (initMat.plus(initMat.transpose())).getArray();
    double[][] doubleVals = new double[doubleValsOrig.length][doubleValsOrig.length];
    copy2DArray(doubleValsOrig, doubleVals);
    // no zero columns
    int[] numZeroCol = findZeroCols(doubleVals);
    assertTrue("number of zero columns is zero in this case but is not.", numZeroCol.length==0);
    // introduce one zero column
    testDropCols(doubleValsOrig, doubleVals, new int[]{8}, 8);
    // introduce two zero columns
    testDropCols(doubleValsOrig, doubleVals, new int[]{4, 8}, 4);
    // introduce three zero columns
    testDropCols(doubleValsOrig, doubleVals, new int[]{3, 4, 8}, 3);
    // introduce four zero columns
    testDropCols(doubleValsOrig, doubleVals, new int[]{3, 4, 6, 8}, 6);
    // introduce five zero columns
    testDropCols(doubleValsOrig, doubleVals, new int[]{0, 3, 4, 6, 8}, 0);
    // introduce six zero columns
    testDropCols(doubleValsOrig, doubleVals, new int[]{0, 3, 4, 6, 8, 9}, 9);
    // introduce seven zero columns
    testDropCols(doubleValsOrig, doubleVals, new int[]{0, 1, 3, 4, 6, 8, 9}, 1);
    // introduce eight zero columns
    testDropCols(doubleValsOrig, doubleVals, new int[]{0, 1, 3, 4, 6, 7, 8, 9}, 7);
    // introduce nine zero columns
    testDropCols(doubleValsOrig, doubleVals, new int[]{0, 1, 3, 4, 5, 6, 7, 8, 9}, 5);
    // introduce 10 zero columns
    testDropCols(doubleValsOrig, doubleVals, new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9}, 2);
  }
  
  public void testDropCols(double[][] valsOrig, double[][] doubleVals, int[] zeroIndices, int newZeroIndex) {
    addOneZeroCol(doubleVals, newZeroIndex);
    int[] numZeroCol = findZeroCols(doubleVals);
    assertArrayEquals(numZeroCol, zeroIndices);
    // drop zero column
    double[][] noZeroCols = dropCols(zeroIndices, valsOrig);
    assertCorrectColDrops(noZeroCols, doubleVals, zeroIndices);
  }
  
  public void assertCorrectColDrops(double[][] actual, double[][] arrayWithZeros, int[] zeroIndices) {
    assertTrue("Incorrect dropped column matrix size", 
            (actual.length+zeroIndices.length)==arrayWithZeros.length);
    Arrays.sort(zeroIndices);
    int matLen = arrayWithZeros.length;
    List<Integer> indiceList = IntStream.range(0, actual.length).boxed().collect(Collectors.toList());
    for (int val:zeroIndices)
      indiceList.add(val, -1);
    
    for (int rIndex=0; rIndex<matLen; rIndex++) {
      int actRInd = indiceList.get(rIndex);
      for (int cIndex=rIndex; cIndex<matLen; cIndex++) {
        int actCInd = indiceList.get(cIndex);
        if (actRInd >= 0 && actCInd >= 0) { // rows/cols not involve in dropped cols/rows
          assertTrue("Non-zero elements differ.", 
                  arrayWithZeros[rIndex][cIndex] == actual[actRInd][actCInd]);
          assertTrue("Non-zero elements differ.",
                  arrayWithZeros[cIndex][rIndex] == actual[actCInd][actRInd]);
        } else  {  // we are at rows/cols that are zero and should be dropped
          assertTrue("Non-zero elements differ.",
                  arrayWithZeros[rIndex][cIndex] == 0);
          assertTrue("Non-zero elements differ.",
                  arrayWithZeros[cIndex][rIndex] == 0);
        }
      }
    }
  }

  public void addOneZeroCol(double[][] vals, int zeroIndex) {
    int len = vals.length;
    for (int index = 0; index < len; index++) {
      vals[zeroIndex][index] = 0;
      vals[index][zeroIndex] = 0;
    }
  }

  // test constraints specified only in linear_constraint and extracted correctly with standardization
  @Test
  public void testConstraintsInLinearStandard() {
    Scope.enter();
    try {
      Frame train = parseAndTrackTestFile("smalldata/glm_test/gaussian_20cols_10000Rows.csv");
      transformFrameCreateCoefNames(train);
      GLMModel.GLMParameters params = new GLMModel.GLMParameters(gaussian);
      params._standardize = true;
      params._response_column = "C21";
      params._max_iterations = 0;
      params._solver = IRLSM;
      params._train = train._key;
      params._lambda = new double[]{0};
      GLMModel glm = new GLM(params).trainModel().get();
      Scope.track_generic(glm);
      // build the beta_constraints
      Frame linear_constraints = _linearConstraint2;
      params._max_iterations = 1;
      params._expose_constraints = true;
      params._linear_constraints = linear_constraints._key;
      GLMModel glm2 = new GLM(params).trainModel().get();
      Scope.track_generic(glm2);
      // check constraints from linear constraints are extracted properly
      // check equality constraint
      assertCorrectConstraintContent(_equalityNames2, _equalityValuesStandard2, glm2._output._equalityConstraintsLinear);
      // check lessThanEqual to constraint
      assertCorrectConstraintContent(_lessThanNames2, _lessThanValuesStandard2, glm2._output._lessThanEqualToConstraintsLinear);
    } finally {
      Scope.exit();
    }
  }

  public void assertCorrectConstraintContent(String[][] coefNames, double[][] value,
                                             ConstrainedGLMUtils.LinearConstraints[] consts) {
    assertTrue("array length does not match", coefNames.length == consts.length);
    int constLen = consts.length;
    for (int index=0; index<constLen; index++) {
      ConstrainedGLMUtils.LinearConstraints oneConstraint = consts[index];
      Set<String> coefKeys = oneConstraint._constraints.keySet();
      String[] coefName = coefNames[index];
      int entryLen = coefName.length;
      for (int ind = 0; ind < entryLen; ind++) {
        assertTrue(coefKeys.contains(coefName[ind]));
        assertTrue("Expected: "+value[index][ind]+
                ".  Actual: "+oneConstraint._constraints.get(coefName[ind])+" for coefficient: "+coefName[ind]+".", 
                Math.abs(value[index][ind] - oneConstraint._constraints.get(coefName[ind])) < EPS);
      }
    }
  }
}
