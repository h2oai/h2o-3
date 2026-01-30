package hex.hglm;

import Jama.Matrix;
import hex.SplitFrame;
import hex.glm.GLMModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static water.fvec.Vec.T_CAT;
import static water.fvec.Vec.T_NUM;
import static water.util.ArrayUtils.innerProduct;
import static water.util.ArrayUtils.outerProduct;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class HGLMBasicTest extends TestUtil {
  public static final double TOL = 1e-4;
  Frame _simpleFrame1;
  Frame _simpleFrameSortEnum1;
  Frame _simpleFrameSortEnum2;
  Frame _simpleFrameSortEnum3;
  Frame _simpleFrameSortEnum4;
  Frame _simpleFrameSortEnum5;
  Frame _simpleFrameSortEnum6;  
  
  @Before
  public void setup() {
    Scope.enter();
    _simpleFrame1 = Scope.track(getSimpleFrame1());
    _simpleFrameSortEnum1 = new Frame(_simpleFrame1.sort(new int[]{0}));
    DKV.put(_simpleFrameSortEnum1);
    Scope.track(_simpleFrameSortEnum1);
    _simpleFrameSortEnum2 = new Frame(_simpleFrame1.sort(new int[]{1}));
    Scope.track(_simpleFrameSortEnum2);
    DKV.put(_simpleFrameSortEnum2);
    _simpleFrameSortEnum3 = new Frame(_simpleFrame1.sort(new int[]{3}));
    Scope.track(_simpleFrameSortEnum3);
    DKV.put(_simpleFrameSortEnum3);
    _simpleFrameSortEnum4 = new Frame(_simpleFrame1.sort(new int[]{4}));
    Scope.track(_simpleFrameSortEnum4);
    DKV.put(_simpleFrameSortEnum4);
    _simpleFrameSortEnum5 = new Frame(_simpleFrame1.sort(new int[]{5}));
    Scope.track(_simpleFrameSortEnum5);
    DKV.put(_simpleFrameSortEnum5);
    _simpleFrameSortEnum6 = new Frame(_simpleFrame1.sort(new int[]{6}));
    Scope.track(_simpleFrameSortEnum6);
    DKV.put(_simpleFrameSortEnum6);
  }
  
  public Frame getSimpleFrame1() {
    Frame frame1 = new TestFrameBuilder()
            .withColNames("enum1", "enum2", "enum3", "enum4", "enum5", "enum6", "num1", "num2", "num3", "response")
            .withVecTypes(T_CAT, T_CAT, T_CAT, T_CAT, T_CAT, T_CAT, T_NUM, T_NUM, T_NUM, T_NUM)
            .withDataForCol(0, new String[]{"4","6","1","5","0","3","2","4","6","1","5","0","3","2","2","4","5",
                    "0","3","1","6"})
            .withDataForCol(1, new String[]{"4","2","1","3","5","0","5","4","3","0","1","2","4","0","1","5","2",
                    "3","5","4","1"})
            .withDataForCol(2, new String[]{"2","4","1","3","0","3","2","1","4","0","3","4","1","2","0","2","1",
                    "3","4","0","4"})
            .withDataForCol(3, new String[]{"1","0","3","2","2","3","0","1","1","2","3","0","3","2","1","0","1",
                    "3","2","0","0"})
            .withDataForCol(4, new String[]{"1","2","0","0","2","1","2","0","1","1","0","2","1","0","2","2","1",
                    "0","2","1","0"})
            .withDataForCol(5, new String[]{"0","1","0","1","0","1","0","0","1","0","1","0","1","0","1","0","1",
                    "0","1","0","1"})
            .withDataForCol(6, new double[]{1.8927, 0.7133, 0.08293, 0.6011, 0.2649, 0.8661, 0.8842, 0.63299,
                    0.4035, 0.8388, 0.8383, 0.0594, 0.6184, 0.5409, 0.4051, 0.6057, 0.8923, 0.5943, 0.0418, 0.6039, 0.5505})
            .withDataForCol(7, new double[]{0.8234, 1.7421, 0.6429, 0.0266, 0.1297, 0.14113, 0.9964, 0.2733,
                    0.2033, 0.202, 0.5686, 0.6647, 0.348, 0.2829, 0.3381, 0.1031, 0.0311, 0.6848, 0.4419, 0.1148, 0.4001})
            .withDataForCol(8, new double[]{0.70475, 0.3551, 1.4599, 0.3418, 0.8728, 0.74046, 0.8455, 0.7969,
                    0.78093, 0.39793, 0.73438, 0.8195, 0.556, 0.1135, 0.0814, 0.1734, 0.1343, 0.4957, 0.3189, 0.7773, 0.1559})
            .withDataForCol(9, new double[]{0.3489, 0.4978, 0.1525, 1.9239, 0.8210, 0.4121, 0.0462, 0.4824,
                    0.6821, 0.7671, 0.8811, 0.8045, 0.65, 0.4112, 0.972, 0.112, 0.6828, 0.237, 0.541, 0.6329, 0.4035})
            .build();
    return frame1;
  }
  
  @After
  public void teardown() {
    Scope.exit();
  }
  
  // The next set of tests will check and make sure we generate the correct fixed matrices and vectors and they are:
  // AfjTAfj, AfjTYj, AfjTrj, ArjTArj, ArjTYj
  
  // In this test, we choose the enum pred with the highest number of enum levels to be the cluster group
  @Test
  public void testLevel2enum1() {
    Scope.enter();
    try {
      HGLMModel.HGLMParameters params = new HGLMModel.HGLMParameters();
      params._train = _simpleFrame1._key;
      params._response_column = "response";
      params._group_column = "enum1";
      params._use_all_factor_levels = true;
      params._random_columns = new String[]{"enum6", "enum4", "enum2", "num1", "num3"};
      params._showFixedMatVecs = true;
      params._max_iterations = 0;
      HGLMModel model = new HGLM(params).trainModel().get();
      Scope.track_generic(model);
      checkWithManualResults1(model, params._response_column, _simpleFrame1, params._group_column,
              new String[]{"enum2", "enum3", "enum4", "enum5", "enum6"}, new String[]{"num1", "num2", "num3"},
              new String[]{"enum2", "enum4", "enum6"}, new String[]{"num1", "num3"});
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testLevel2enum1NoIntercept() {
    Scope.enter();
    try {
      HGLMModel.HGLMParameters params = new HGLMModel.HGLMParameters();
      params._train = _simpleFrame1._key;
      params._response_column = "response";
      params._group_column = "enum1";
      params._use_all_factor_levels = true;
      params._random_columns = new String[]{"enum6", "enum4", "enum2", "num1", "num3"};
      params._showFixedMatVecs = true;
      params._max_iterations = 0;
      params._random_intercept = true;
      HGLMModel model = new HGLM(params).trainModel().get();
      Scope.track_generic(model);
      checkWithManualResults1(model, params._response_column, _simpleFrame1, params._group_column,
              new String[]{"enum2", "enum3", "enum4", "enum5", "enum6"}, new String[]{"num1", "num2", "num3"},
              new String[]{"enum2", "enum4", "enum6"}, new String[]{"num1", "num3"});
    } finally {
      Scope.exit();
    }
  }
  
  @Test
  public void testLevel2enum1V2() {
    Scope.enter();
    try {
      HGLMModel.HGLMParameters params = new HGLMModel.HGLMParameters();
      params._train = _simpleFrame1._key;
      params._response_column = "response";
      params._group_column = "enum1";
      params._use_all_factor_levels = false;
      params._random_columns = new String[]{"enum6", "enum4", "enum2", "num1", "num3"};
      params._showFixedMatVecs = true;
      params._max_iterations = 0;
      HGLMModel model = new HGLM(params).trainModel().get();
      Scope.track_generic(model);
      checkWithManualResults1(model, params._response_column, _simpleFrame1, params._group_column,
              new String[]{"enum2", "enum3", "enum4", "enum5", "enum6"}, new String[]{"num1", "num2", "num3"},
              new String[]{"enum2", "enum4", "enum6"}, new String[]{"num1", "num3"});
    } finally {
      Scope.exit();
    }
  }

  // In this test, we choose the enum2 to be the cluster group
  @Test
  public void testLevel2enum2() {
    Scope.enter();
    try {
      HGLMModel.HGLMParameters params = new HGLMModel.HGLMParameters();
      params._train = _simpleFrame1._key;
      params._response_column = "response";
      params._group_column = "enum2";
      params._use_all_factor_levels = true;
      params._random_columns = new String[]{"enum1", "enum4", "enum6", "num3", "num2"};
      params._showFixedMatVecs = true;
      params._max_iterations = 0;
      HGLMModel model = new HGLM(params).trainModel().get();
      Scope.track_generic(model);
      checkWithManualResults1(model, params._response_column, _simpleFrame1, params._group_column,
              new String[]{"enum1", "enum3", "enum4", "enum5", "enum6"}, new String[]{"num1", "num2", "num3"}, 
              new String[]{"enum1", "enum4", "enum6"}, new String[]{"num2", "num3"});
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testLevel2enum2V2() {
    Scope.enter();
    try {
      HGLMModel.HGLMParameters params = new HGLMModel.HGLMParameters();
      params._train = _simpleFrame1._key;
      params._response_column = "response";
      params._group_column = "enum2";
      params._use_all_factor_levels = false;
      params._random_columns = new String[]{"enum1", "enum4", "enum6", "num3", "num2"};
      params._showFixedMatVecs = true;
      params._max_iterations = 0;
      HGLMModel model = new HGLM(params).trainModel().get();
      Scope.track_generic(model);
      checkWithManualResults1(model, params._response_column, _simpleFrame1, params._group_column,
              new String[]{"enum1", "enum3", "enum4", "enum5", "enum6"}, new String[]{"num1", "num2", "num3"},
              new String[]{"enum1", "enum4", "enum6"}, new String[]{"num2", "num3"});
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testLevel2enum2V2NoRandomIntercept() {
    Scope.enter();
    try {
      HGLMModel.HGLMParameters params = new HGLMModel.HGLMParameters();
      params._train = _simpleFrame1._key;
      params._response_column = "response";
      params._group_column = "enum2";
      params._use_all_factor_levels = false;
      params._random_columns = new String[]{"enum1", "enum4", "enum6", "num3", "num2"};
      params._showFixedMatVecs = true;
      params._max_iterations = 0;
      params._random_intercept = false;
      HGLMModel model = new HGLM(params).trainModel().get();
      Scope.track_generic(model);
      checkWithManualResults1(model, params._response_column, _simpleFrame1, params._group_column,
              new String[]{"enum1", "enum3", "enum4", "enum5", "enum6"}, new String[]{"num1", "num2", "num3"},
              new String[]{"enum1", "enum4", "enum6"}, new String[]{"num2", "num3"});
    } finally {
      Scope.exit();
    }
  }
  
  // In this test, we choose the enum3 to be the cluster group
  @Test
  public void testLevel2enum3() {
    Scope.enter();
    try {
      HGLMModel.HGLMParameters params = new HGLMModel.HGLMParameters();
      params._train = _simpleFrame1._key;
      params._response_column = "response";
      params._group_column = "enum3";
      params._use_all_factor_levels = true;
      params._random_columns = new String[]{"enum4", "enum1", "num1", "num2"};
      params._showFixedMatVecs = true;
      params._max_iterations = 0;
      HGLMModel model = new HGLM(params).trainModel().get();
      Scope.track_generic(model);
      checkWithManualResults1(model, params._response_column, _simpleFrame1, params._group_column,
              new String[]{"enum1", "enum2", "enum4", "enum5", "enum6"}, new String[]{"num1", "num2", "num3"},
              new String[]{"enum1", "enum4"}, new String[]{"num1", "num2"});
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testLevel2enum3V2() {
    Scope.enter();
    try {
      HGLMModel.HGLMParameters params = new HGLMModel.HGLMParameters();
      params._train = _simpleFrame1._key;
      params._response_column = "response";
      params._group_column = "enum3";
      params._use_all_factor_levels = false;
      params._random_columns = new String[]{"enum4", "enum1", "num1", "num2"};
      params._showFixedMatVecs = true;
      params._max_iterations = 0;
      HGLMModel model = new HGLM(params).trainModel().get();
      Scope.track_generic(model);
      checkWithManualResults1(model, params._response_column, _simpleFrame1, params._group_column,
              new String[]{"enum1", "enum2", "enum4", "enum5", "enum6"}, new String[]{"num1", "num2", "num3"},
              new String[]{"enum1", "enum4"}, new String[]{"num1", "num2"});
    } finally {
      Scope.exit();
    }
  }

  // In this test, we choose the enum4 to be the cluster group
  @Test
  public void testLevel2enum4() {
    Scope.enter();
    try {
      HGLMModel.HGLMParameters params = new HGLMModel.HGLMParameters();
      params._train = _simpleFrame1._key;
      params._response_column = "response";
      params._group_column = "enum4";
      params._use_all_factor_levels = true;
      params._random_columns = new String[]{"enum3", "enum1", "enum2", "enum6", "num1", "num2"};
      params._showFixedMatVecs = true;
      params._max_iterations = 0;
      HGLMModel model = new HGLM(params).trainModel().get();
      Scope.track_generic(model);
      checkWithManualResults1(model, params._response_column, _simpleFrame1, params._group_column,
              new String[]{"enum1", "enum2", "enum3", "enum5", "enum6"}, new String[]{"num1", "num2", "num3"}, 
              new String[]{"enum1", "enum2", "enum3", "enum6"}, new String[]{"num1", "num2"});
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testLevel2enum4V2() {
    Scope.enter();
    try {
      HGLMModel.HGLMParameters params = new HGLMModel.HGLMParameters();
      params._train = _simpleFrame1._key;
      params._response_column = "response";
      params._group_column = "enum4";
      params._use_all_factor_levels = false;
      params._random_columns = new String[]{"enum3", "enum1", "enum2", "enum6", "num1", "num2"};
      params._showFixedMatVecs = true;
      params._max_iterations = 0;
      HGLMModel model = new HGLM(params).trainModel().get();
      Scope.track_generic(model);
      checkWithManualResults1(model, params._response_column, _simpleFrame1, params._group_column,
              new String[]{"enum1", "enum2", "enum3", "enum5", "enum6"}, new String[]{"num1", "num2", "num3"},
              new String[]{"enum1", "enum2", "enum3", "enum6"}, new String[]{"num1", "num2"});
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testLevel2enum5() {
    Scope.enter();
    try {
      HGLMModel.HGLMParameters params = new HGLMModel.HGLMParameters();
      params._train = _simpleFrame1._key;
      params._response_column = "response";
      params._group_column = "enum5";
      params._use_all_factor_levels = true;
      params._random_columns = new String[]{"enum3", "enum1", "enum2", "enum6", "num1", "num3"};
      params._showFixedMatVecs = true;
      params._max_iterations = 0;
      HGLMModel model = new HGLM(params).trainModel().get();
      Scope.track_generic(model);
      checkWithManualResults1(model, params._response_column, _simpleFrame1, params._group_column,
              new String[]{"enum1", "enum2", "enum3", "enum4", "enum6"}, new String[]{"num1", "num2", "num3"},
              new String[]{"enum1", "enum2", "enum3", "enum6"}, new String[]{"num1", "num3"});
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testLevel2enum5V2() {
    Scope.enter();
    try {
      HGLMModel.HGLMParameters params = new HGLMModel.HGLMParameters();
      params._train = _simpleFrame1._key;
      params._response_column = "response";
      params._group_column = "enum5";
      params._use_all_factor_levels = false;
      params._random_columns = new String[]{"enum3", "enum1", "enum2", "enum6", "num1", "num3"};
      params._showFixedMatVecs = true;
      params._max_iterations = 0;
      HGLMModel model = new HGLM(params).trainModel().get();
      Scope.track_generic(model);
      checkWithManualResults1(model, params._response_column, _simpleFrame1, params._group_column,
              new String[]{"enum1", "enum2", "enum3", "enum4", "enum6"}, new String[]{"num1", "num2", "num3"},
              new String[]{"enum1", "enum2", "enum3", "enum6"}, new String[]{"num1", "num3"});
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testLevel2enum6() {
    Scope.enter();
    try {
      HGLMModel.HGLMParameters params = new HGLMModel.HGLMParameters();
      params._train = _simpleFrame1._key;
      params._response_column = "response";
      params._group_column = "enum6";
      params._use_all_factor_levels = true;
      params._random_columns = new String[]{"enum3", "enum1", "enum2", "enum5", "num1", "num3"};
      params._showFixedMatVecs = true;
      params._max_iterations = 0;
      HGLMModel model = new HGLM(params).trainModel().get();
      Scope.track_generic(model);
      checkWithManualResults1(model, params._response_column, _simpleFrame1, params._group_column,
              new String[]{"enum1", "enum2", "enum3", "enum4", "enum5"}, new String[]{"num1", "num2", "num3"},
              new String[]{"enum1", "enum2", "enum3", "enum5"}, new String[]{"num1", "num3"});
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testLevel2enum6V2() {
    Scope.enter();
    try {
      HGLMModel.HGLMParameters params = new HGLMModel.HGLMParameters();
      params._train = _simpleFrame1._key;
      params._response_column = "response";
      params._group_column = "enum6";
      params._max_iterations = 0;
      params._use_all_factor_levels = false;
      params._random_columns = new String[]{"enum3", "enum1", "enum2", "enum5", "num1", "num3"};
      params._showFixedMatVecs = true;
      HGLMModel model = new HGLM(params).trainModel().get();
      Scope.track_generic(model);
      checkWithManualResults1(model, params._response_column, _simpleFrame1, params._group_column,
              new String[]{"enum1", "enum2", "enum3", "enum4", "enum5"}, new String[]{"num1", "num2", "num3"},
              new String[]{"enum1", "enum2", "enum3", "enum5"}, new String[]{"num1", "num3"});
    } finally {
      Scope.exit();
    }
  }
  
  // todo: add check _yMinusXTimesZ and _yMinusfixPredSquare
  public void checkWithManualResults1(HGLMModel model, String response, Frame fr, String level2Name, 
                                      String[] enumFixed, String[] numFixed, String[] enumRandom, String[] numRandom) {
    double[] fixedRowValues;
    double[] randomRowValues;
    int numLevel2Vals = fr.vec(level2Name).domain().length;
    double[][][] afjTAfj = new double[numLevel2Vals][][];
    double[][][] arjTArj = new double[numLevel2Vals][][];
    double[][][] afjTArj = new double[numLevel2Vals][][];
    double[][] afjTYj = new double[numLevel2Vals][];
    double[][] arjTYj = new double[numLevel2Vals][];
    double yMinusfixPredSquare = 0;
    double[][] yMinusXTimesZ = new double[numLevel2Vals][model._output._yMinusXTimesZ[0].length];
    double[] beta = model._output._beta;

    int numRow = (int) fr.numRows();
    double responseVal;
    int unit2Level;
    double fixEffect;
    double respMinusFix;
    
    for (int rowInd = 0; rowInd < numRow; rowInd++) {
      fixedRowValues = grabRow2Arrays(enumFixed, numFixed, true, rowInd, fr, model._parms._use_all_factor_levels);
      randomRowValues = grabRow2Arrays(enumRandom, numRandom, model._parms._random_intercept, rowInd, fr, model._parms._use_all_factor_levels);
      responseVal = fr.vec(response).at(rowInd);
      unit2Level = (int) fr.vec(level2Name).at(rowInd);
      // calculate the various matrices and vectors
      formMatrix(afjTAfj, unit2Level, fixedRowValues, fixedRowValues); // calculate afjTAfj
      formMatrix(arjTArj, unit2Level, randomRowValues, randomRowValues); // calculate arjTArj
      formMatrix(afjTArj, unit2Level, fixedRowValues, randomRowValues); // calculate afjTArj
      formVector(afjTYj, unit2Level, fixedRowValues, responseVal); // calculate afjTYj
      formVector(arjTYj, unit2Level, randomRowValues, responseVal); // calculate arjTYj
      fixEffect = innerProduct(fixedRowValues, beta);
      respMinusFix = responseVal - fixEffect;
      yMinusfixPredSquare += respMinusFix*respMinusFix;
      ArrayUtils.add(yMinusXTimesZ[unit2Level], ArrayUtils.mult(randomRowValues, respMinusFix));
    }
    
    // make sure manually generated matrices/vectors and those from model._output are the same
    checkDoubleArrays(model._output._afjtyj, afjTYj, TOL);
    checkDoubleArrays(model._output._arjtyj, arjTYj, TOL);
    check3DArrays(model._output._afjtafj, afjTAfj, TOL);
    check3DArrays(model._output._afjtarj, afjTArj, TOL);
    check3DArrays(model._output._arjtarj, arjTArj, TOL);
    checkDoubleArrays(model._output._yMinusXTimesZ, yMinusXTimesZ, TOL);
    assertEquals(model._output._yMinusFixPredSquare, yMinusfixPredSquare, TOL);
  }
  
  public void formVector(double[][] matrix, int level2Unit, double[] vector, double response) {
    int len = vector.length;
    
    if (matrix[level2Unit] == null)
      matrix[level2Unit] = new double[len];
    
    for (int ind=0; ind < len; ind++)
      matrix[level2Unit][ind] += vector[ind]*response;
    
  }

  public void formZTTimesZ(double[][] zTTimesZ, int unit2LevelS, double[] randomRowValuesS, double[][] result) {
    int numRandVal = randomRowValuesS.length;
    outerProduct(result, randomRowValuesS, randomRowValuesS);
    int rowIndexStart = unit2LevelS*numRandVal;
    int colIndexStart = unit2LevelS*numRandVal;
    for (int index=0; index<numRandVal; index++) {
      for (int colInd=0; colInd<numRandVal; colInd++) {
        zTTimesZ[rowIndexStart+index][colIndexStart+colInd] += result[index][colInd];
      }
    }
  }
  
  public void formMatrix(double[][][] matrices, int level2Unit, double[] vector1, double[] vector2) {
    int numRow = vector1.length;
    int numCol = vector2.length;
    
    if (matrices[level2Unit] == null)
      matrices[level2Unit] = new double[numRow][numCol];
    
    for (int rInd = 0; rInd < numRow; rInd++)
      for (int cInd = 0; cInd < numCol; cInd++)
        matrices[level2Unit][rInd][cInd] += vector1[rInd]*vector2[cInd];
  }
  
  public double[] grabRow2Arrays(String[] enumPredNames, String[] numPredNames, boolean hasIntercept, int rowInd, 
                                 Frame fr, boolean useAllFactorLevels) {
    List<Double> rowValues = new ArrayList<>();
    int catVal;
    for (String enumName : enumPredNames) {
      Double[] enumVal = new Double[useAllFactorLevels ? fr.vec(enumName).domain().length : (fr.vec(enumName).domain().length-1)];
      Arrays.fill(enumVal, 0.0);
      catVal =  (int) fr.vec(enumName).at(rowInd);
      if (useAllFactorLevels && catVal >= 0)
        enumVal[catVal] = 1.0;
      if (!useAllFactorLevels && catVal > 0)
        enumVal[(catVal-1)] = 1.0;
      rowValues.addAll(Arrays.asList(enumVal));
    }
    for (String numName:numPredNames) {
      double val = fr.vec(numName).at(rowInd);
      rowValues.add(val);
    }
    
    if (hasIntercept)
      rowValues.add(1.0);    
    return rowValues.stream().mapToDouble(Double::doubleValue).toArray();
  }
  
  // when we specify random columns in different permutation, the fixed matrices and vectors generated should be the 
  // same.
  @Test
  public void testMatVecFormation() {
    Scope.enter();
    try {
      HGLMModel.HGLMParameters params = new HGLMModel.HGLMParameters();
      params._train = _simpleFrame1._key;
      params._response_column = "response";
      params._group_column = "enum1";
      params._use_all_factor_levels = true;
      params._random_columns = new String[]{"enum2", "enum3", "num1", "num2"};
      params._showFixedMatVecs = true;
      params._max_iterations = 0;
      HGLMModel model = new HGLM(params).trainModel().get();
      Scope.track_generic(model);
      
      params._random_columns = new String[]{"num2", "num1", "enum3", "enum2"};
      HGLMModel model2 = new HGLM(params).trainModel().get();
      Scope.track_generic(model2);
      checkDoubleArrays(model._output._afjtyj, model2._output._afjtyj, TOL);
      checkDoubleArrays(model._output._arjtyj, model2._output._arjtyj, TOL);
      check3DArrays(model._output._afjtafj, model2._output._afjtafj, TOL);
      check3DArrays(model._output._afjtarj, model2._output._afjtarj, TOL);
      check3DArrays(model._output._arjtarj, model2._output._arjtarj, TOL);
    } finally {
      Scope.exit();
    }
  }
  
  public void checkCorrectInitValue(HGLMModel model, double[] initBetas, Frame ubetaFrame, Frame tMat, double sigmaEpsilon) {
    // check fixed coefficient initialization
    checkArrays(initBetas, model._output._beta, TOL);
    // check random coefficient initialization
    double[][] ubetaInit = new double[(int) ubetaFrame.numRows()][(int) ubetaFrame.numCols()];
    final ArrayUtils.FrameToArray f2a = new ArrayUtils.FrameToArray(0, ubetaInit[0].length-1, 
            ubetaInit.length, ubetaInit);
    ubetaInit = f2a.doAll(ubetaFrame).getArray();
    checkDoubleArrays(ubetaInit, model._output._ubeta, TOL);
    // check T matrix initialization
    double[][] tMatInit = new double[tMat.numCols()][tMat.numCols()];
    final ArrayUtils.FrameToArray f2a2 = new ArrayUtils.FrameToArray(0, tMat.numCols()-1, tMatInit.length, tMatInit);
    tMatInit = f2a2.doAll(tMat).getArray();
    checkDoubleArrays(tMatInit, model._output._tmat, TOL);
    // check sigma epsilon initializaiton
    assertEquals(sigmaEpsilon, model._output._tau_e_var, TOL);
  }

  /**
   * Here I am testing a different way to set the T matrix
   */
  @Test
  public void testSetInitT() {
    Scope.enter();
    try {
      Frame prostate = parseAndTrackTestFile("smalldata/prostate/prostate.csv");
      prostate.replace(3, prostate.vec(3).toCategoricalVec()).remove();
      prostate.replace(4, prostate.vec(4).toCategoricalVec()).remove();
      prostate.replace(5, prostate.vec(5).toCategoricalVec()).remove();
      DKV.put(prostate);
      double sigmaU = 0.09847638;
      HGLMModel.HGLMParameters params = new HGLMModel.HGLMParameters();
      params._train = prostate._key;
      params._response_column = "VOL";
      params._ignored_columns = new String[]{"ID"};
      params._group_column = "RACE";
      params._use_all_factor_levels = true;
      params._random_columns = new String[]{"GLEASON", "DPROS", "DCAPS"};
      params._tau_u_var_init = sigmaU;
      params._max_iterations = 0;
      HGLMModel model = new HGLM(params).trainModel().get();
      Scope.track_generic(model);
      checkCorrectTMat(model, sigmaU);      
    } finally {
      Scope.exit();
    }
  }
  
  public void checkCorrectTMat(HGLMModel model, double sigmaU) {
    double[][] correctTMat = new double[model._output._tmat.length][model._output._tmat.length];
    for (int ind=0; ind<correctTMat.length; ind++)
      correctTMat[ind][ind] = sigmaU;
    checkDoubleArrays(correctTMat, model._output._tmat, TOL);
  }
  
  public static Frame makeUBetaFrame(double[][] initUBeta) {
    double[][] initUBetaT = new Matrix(initUBeta).transpose().getArray();
    Frame ubetaFrame = new TestFrameBuilder()
            .withColNames("x1", "x3", "intercept")
            .withVecTypes(T_NUM, T_NUM, T_NUM)
            .withDataForCol(0, initUBetaT[0])
            .withDataForCol(1, initUBetaT[1])
            .withDataForCol(2, initUBetaT[2])
            .build();
    return ubetaFrame; 
  }
  
  @Test
  public void testRandomInterceptOnly() {
      Scope.enter();
      try {
        HGLMModel.HGLMParameters params = new HGLMModel.HGLMParameters();
        params._train = _simpleFrame1._key;
        params._response_column = "response";
        params._group_column = "enum1";
        params._use_all_factor_levels = true;
        params._random_intercept = true;
        params._showFixedMatVecs = true;
        params._max_iterations = 0;
        HGLMModel model = new HGLM(params).trainModel().get();
        Scope.track_generic(model);
        checkWithManualResults1(model, params._response_column, _simpleFrame1, params._group_column,
                new String[]{"enum2", "enum3", "enum4", "enum5", "enum6"}, new String[]{"num1", "num2", "num3"},
                new String[]{}, new String[]{});
      } finally {
        Scope.exit();
      }
  }
  
  @Test
  public void testPredictionMetricsSummaryScoringHistoryWRIntercept() {
    try {
      Scope.enter();
      double[] initBeta = new double[]{0.57305, 0.95066, 0.4277, 0.2814, 0.3727};
      double[][] initUbeta = new double[][]{{-1.4257393174908208, 1.9459515904358207, -1.5121424866231998,
              0.757565557144771, 1.6454093526843507, 0.521525656276774, 0.15102292603863332,
              -0.5629664504958487, 0.39941437871543806, -0.17666156140184344, -0.9012256565441157,
              0.4013361512547679, -0.7655048415710769, 0.9625031349421274, -1.6916150004681492, 0.8967295711861796},
              {0.7307560306666573, -0.43350728257793125, 0.761204681372934,
                      -0.9665905711121056, -0.0485193797802151, -0.6595712372715338, -0.4616825414753406,
                      0.7886590178655907, 0.27241373557806586, -0.04301812863182515, -0.10936899265127145,
                      0.8173502195208687, -0.1473779447485634, -2.1395714941712223, -0.9096112739244531, -1.8557521580762681},
              {-1.818395521031121, 0.3423166377645478, 2.803250124441809,
                      0.36788162518520634, 0.2854761765342526, 1.9802144801614998, 1.0295144701971513,
                      -0.0195871711309739, -0.04015765623938129, -0.22232686097490753, -1.1551081071985216,
                      0.4799532222692264, 0.1858090583440908, -0.25703386958964214, 1.3293865207793107, -0.6641399983332995}};
      Frame ubetaInitFrame = new TestFrameBuilder()
              .withColNames("x1", "x3", "intercept")
              .withVecTypes(T_NUM, T_NUM, T_NUM)
              .withDataForCol(0, initUbeta[0])
              .withDataForCol(1, initUbeta[1])
              .withDataForCol(2, initUbeta[2])
              .build();
      Scope.track(ubetaInitFrame);

      Frame fr = parseTestFile("smalldata/hglm_test/semiconductor.csv");
      Frame validFr = parseTestFile("smalldata/hglm_test/semiconductor.csv");
      fr.replace(0, fr.vec(0).toCategoricalVec()).remove();
      validFr.replace(0, validFr.vec(0).toCategoricalVec()).remove();
      DKV.put(fr);
      DKV.put(validFr);
      Scope.track(fr);
      Scope.track(validFr);
      SplitFrame sf = new SplitFrame(validFr, new double[]{0.1, 0.9}, new Key[]{Key.make("train.hex"), Key.make("test.hex")});
      sf.exec().get();
      Key[] ksplits = sf._destination_frames;
      Frame tr = DKV.get(ksplits[0]).get();
      Scope.track(tr);
      Frame te = DKV.get(ksplits[1]).get();
      Scope.track(te);
      HGLMModel.HGLMParameters parms = new HGLMModel.HGLMParameters();
      parms._train = fr._key;
      parms._valid = te._key;
      parms._response_column = "y";
      parms._ignored_columns = new String[]{"x2", "x4"};
      parms._ignore_const_cols = true;
      parms._family = GLMModel.GLMParameters.Family.gaussian;
      parms._random_columns = new String[]{"x1", "x3"};
      parms._group_column = "Device";
      parms._max_iterations = 0;
      parms._seed = 1234;
      parms._initial_fixed_effects = initBeta;
      parms._initial_random_effects = ubetaInitFrame._key;
      parms._random_intercept = true;
      HGLMModel modelNS = new HGLM(parms).trainModel().get();
      Scope.track_generic(modelNS);
      Frame predFrameNS = modelNS.score(fr);
      Scope.track(predFrameNS);
      checkPrediction(fr, predFrameNS, modelNS, 0.0);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testPredictionMetricsSummaryScoringHistoryWORIntercept() {
    try {
      Scope.enter();
      double[] initBeta = new double[]{0.57305, 0.95066, 0.4277, 0.2814, 0.3727};
      double[][] initUbeta = new double[][]{{-1.4257393174908208, 1.9459515904358207, -1.5121424866231998,
              0.757565557144771, 1.6454093526843507, 0.521525656276774, 0.15102292603863332,
              -0.5629664504958487, 0.39941437871543806, -0.17666156140184344, -0.9012256565441157,
              0.4013361512547679, -0.7655048415710769, 0.9625031349421274, -1.6916150004681492, 0.8967295711861796},
              {0.7307560306666573, -0.43350728257793125, 0.761204681372934,
                      -0.9665905711121056, -0.0485193797802151, -0.6595712372715338, -0.4616825414753406,
                      0.7886590178655907, 0.27241373557806586, -0.04301812863182515, -0.10936899265127145,
                      0.8173502195208687, -0.1473779447485634, -2.1395714941712223, -0.9096112739244531, -1.8557521580762681}};
      Frame ubetaInitFrame = new TestFrameBuilder()
              .withColNames("x1", "x3")
              .withVecTypes(T_NUM, T_NUM)
              .withDataForCol(0, initUbeta[0])
              .withDataForCol(1, initUbeta[1])
              .build();
      Scope.track(ubetaInitFrame);

      Frame fr = parseTestFile("smalldata/hglm_test/semiconductor.csv");
      Frame validFr = parseTestFile("smalldata/hglm_test/semiconductor.csv");
      fr.replace(0, fr.vec(0).toCategoricalVec()).remove();
      validFr.replace(0, validFr.vec(0).toCategoricalVec()).remove();
      DKV.put(fr);
      DKV.put(validFr);
      Scope.track(fr);
      Scope.track(validFr);
      SplitFrame sf = new SplitFrame(validFr, new double[]{0.1, 0.9}, new Key[]{Key.make("train.hex"), Key.make("test.hex")});
      sf.exec().get();
      Key[] ksplits = sf._destination_frames;
      Frame tr = DKV.get(ksplits[0]).get();
      Scope.track(tr);
      Frame te = DKV.get(ksplits[1]).get();
      Scope.track(te);
      HGLMModel.HGLMParameters parms = new HGLMModel.HGLMParameters();
      parms._train = fr._key;
      parms._valid = te._key;
      parms._response_column = "y";
      parms._ignored_columns = new String[]{"x2", "x4"};
      parms._ignore_const_cols = true;
      parms._family = GLMModel.GLMParameters.Family.gaussian;
      parms._random_columns = new String[]{"x1", "x3"};
      parms._group_column = "Device";
      parms._max_iterations = 0;
      parms._seed = 1234;
      parms._initial_fixed_effects = initBeta;
      parms._initial_random_effects = ubetaInitFrame._key;
      parms._random_intercept = false;
      HGLMModel modelNS = new HGLM(parms).trainModel().get();
      Scope.track_generic(modelNS);
      Frame predFrameNS = modelNS.score(fr);
      Scope.track(predFrameNS);
      checkPrediction(fr, predFrameNS, modelNS, 0.0);
    } finally {
      Scope.exit();
    }
  }
  
  public void checkPrediction(Frame fr, Frame predFrame, HGLMModel model, double val) {
    double[] beta = model._output._beta;
    double[][] ubeta = model._output._ubeta;
    String[] coeffNames = model._output._fixed_coefficient_names;
    String[] rCoeffNames = model._output._random_coefficient_names;
    String level2Col = model._parms._group_column;
    Random obj = new Random();
    int numRow = (int) fr.numRows();
    double estimatedY;
    double[] xvals = new double[coeffNames.length];
    double[] zvals = new double[model._output._ubeta[0].length];
    int level2Val;
    
    for (int index=0; index<numRow; index++) {
      if (obj.nextDouble() > val) { // may not want to check all rows for large dataset
        // grab xval and zval
        fillDataRows(fr, index, coeffNames, rCoeffNames, xvals, zvals);
        level2Val = (int) fr.vec(level2Col).at(index);
        // produce estimated response from fixed effect
        estimatedY = innerProduct(beta, xvals) + innerProduct(ubeta[level2Val], zvals);
        // compare our answer with generated answer executed in parallel
        assertEquals(estimatedY, predFrame.vec(0).at(index), TOL);
      }
    }
  }
  
  public void fillDataRows(Frame fr, int rowInd, String[] coefNames, String[] rCoeffNames, double[] xvals, 
                           double[] zvals) {
    Arrays.fill(xvals, 0.0);
    int interceptInd = xvals.length-1;
    xvals[interceptInd] = 1.0;
    Arrays.fill(zvals, 0.0);
    if (zvals.length > rCoeffNames.length || rCoeffNames[rCoeffNames.length-1] == "intercept")
      zvals[zvals.length-1] = 1.0;
    for (int index=0; index<interceptInd; index++)
      xvals[index] = fr.vec(coefNames[index]).at(rowInd);
    
    int rCoeffLen = rCoeffNames.length;
    for (int index=0; index<rCoeffLen; index++)
      if (!"intercept".equals(rCoeffNames[index])) {
        zvals[index] = fr.vec(rCoeffNames[index]).at(rowInd);
      }
  }

  @Test
  public void testFrameToArray1() {
    /***
     * test for one column
     */
    Scope.enter();
    try {
      int arrayLen = 18;
      final double[] arrayContent =  genRandomArray(arrayLen, 123);
      Frame tMat = new TestFrameBuilder()
              .withColNames("C1")
              .withVecTypes(T_NUM)
              .withDataForCol(0, arrayContent)
              .build();
      Scope.track(tMat);
      double[][] fromFrame = generateArrayFromFrame(tMat);
      for (int index=0; index<arrayLen; index++)
        assertEquals(arrayContent[index], fromFrame[index][0], 1e-6);
    } finally {
      Scope.exit();
    }
  }

  public double[][] generateArrayFromFrame(Frame fr) {
    double[][] fromFrame = new double[(int) fr.numRows()][fr.numCols()];
    final ArrayUtils.FrameToArray f2a = new ArrayUtils.FrameToArray(0, fr.numCols()-1, fromFrame.length, fromFrame);
    fromFrame = f2a.doAll(fr).getArray();
    return fromFrame;
  }

  @Test
  public void testFrameToArray2() {
    /***
     * test for one row and multiple columns
     */
    Scope.enter();
    try {
      int arrayLen = 10;
      double[] arrayContent =  genRandomArray(arrayLen, 123);
      Frame tMat = new TestFrameBuilder()
              .withColNames("C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10")
              .withVecTypes(T_NUM, T_NUM, T_NUM, T_NUM, T_NUM, T_NUM, T_NUM, T_NUM, T_NUM, T_NUM)
              .withDataForCol(0, new double[]{arrayContent[0]})
              .withDataForCol(1, new double[]{arrayContent[1]})
              .withDataForCol(2, new double[]{arrayContent[2]})
              .withDataForCol(3, new double[]{arrayContent[3]})
              .withDataForCol(4, new double[]{arrayContent[4]})
              .withDataForCol(5, new double[]{arrayContent[5]})
              .withDataForCol(6, new double[]{arrayContent[6]})
              .withDataForCol(7, new double[]{arrayContent[7]})
              .withDataForCol(8, new double[]{arrayContent[8]})
              .withDataForCol(9, new double[]{arrayContent[9]})
              .build();
      Scope.track(tMat);
      double[][] fromFrame = generateArrayFromFrame(tMat);
      for (int index=0; index<arrayLen; index++)
        assertEquals(arrayContent[index], fromFrame[0][index], 1e-6);
    } finally{
      Scope.exit();
    }
  }

  @Test
  public void testFrameToArray3() {
    /***
     * test for multiple columns and rows
     */
    Scope.enter();
    try {
      int numCol = 8;
      int numRow = 18;
      double[][] arrayContents = genRandomMatrix(numCol, numRow, 123);
      Frame tMat = new TestFrameBuilder()
              .withColNames("C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8")
              .withVecTypes(T_NUM, T_NUM, T_NUM, T_NUM, T_NUM, T_NUM, T_NUM, T_NUM)
              .withDataForCol(0, arrayContents[0])
              .withDataForCol(1, arrayContents[1])
              .withDataForCol(2, arrayContents[2])
              .withDataForCol(3, arrayContents[3])
              .withDataForCol(4, arrayContents[4])
              .withDataForCol(5, arrayContents[5])
              .withDataForCol(6, arrayContents[6])
              .withDataForCol(7, arrayContents[7])
              .build();
      Scope.track(tMat);
      double[][] fromFrame = generateArrayFromFrame(tMat);
      for (int rowInd = 0; rowInd < numRow; rowInd++)
        for (int colInd = 0; colInd < numCol; colInd++)
          assertEquals(arrayContents[colInd][rowInd], fromFrame[rowInd][colInd], 1e-6);
    } finally {
      Scope.exit();
    }
  }
}
