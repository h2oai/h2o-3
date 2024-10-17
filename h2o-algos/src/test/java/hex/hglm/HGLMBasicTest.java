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
      checkWithManualResults1(model, params._response_column, _simpleFrame1, _simpleFrameSortEnum1, params._group_column, params._standardize,
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
      checkWithManualResults1(model, params._response_column, _simpleFrame1, _simpleFrameSortEnum1, params._group_column, params._standardize,
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
      params._standardize = false;
      params._max_iterations = 0;
      HGLMModel model = new HGLM(params).trainModel().get();
      Scope.track_generic(model);
      checkWithManualResults1(model, params._response_column, _simpleFrame1, _simpleFrameSortEnum1, params._group_column, params._standardize,
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
      params._standardize = false;
      params._max_iterations = 0;
      HGLMModel model = new HGLM(params).trainModel().get();
      Scope.track_generic(model);
      checkWithManualResults1(model, params._response_column, _simpleFrame1, _simpleFrameSortEnum2, params._group_column, params._standardize,
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
      params._standardize = false;
      params._max_iterations = 0;
      HGLMModel model = new HGLM(params).trainModel().get();
      Scope.track_generic(model);
      checkWithManualResults1(model, params._response_column, _simpleFrame1, _simpleFrameSortEnum2, params._group_column, params._standardize,
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
      params._standardize = false;
      params._max_iterations = 0;
      params._random_intercept = false;
      HGLMModel model = new HGLM(params).trainModel().get();
      Scope.track_generic(model);
      checkWithManualResults1(model, params._response_column, _simpleFrame1, _simpleFrameSortEnum2, params._group_column, params._standardize,
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
      checkWithManualResults1(model, params._response_column, _simpleFrame1, _simpleFrameSortEnum3, params._group_column, params._standardize,
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
      checkWithManualResults1(model, params._response_column, _simpleFrame1, _simpleFrameSortEnum3, params._group_column, params._standardize,
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
      checkWithManualResults1(model, params._response_column, _simpleFrame1, _simpleFrameSortEnum4, params._group_column, params._standardize,
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
      checkWithManualResults1(model, params._response_column, _simpleFrame1, _simpleFrameSortEnum4, params._group_column, params._standardize,
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
      checkWithManualResults1(model, params._response_column, _simpleFrame1, _simpleFrameSortEnum5, params._group_column, params._standardize,
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
      checkWithManualResults1(model, params._response_column, _simpleFrame1, _simpleFrameSortEnum5, params._group_column, params._standardize,
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
      params._standardize = false;
      params._max_iterations = 0;
      HGLMModel model = new HGLM(params).trainModel().get();
      Scope.track_generic(model);
      checkWithManualResults1(model, params._response_column, _simpleFrame1, _simpleFrameSortEnum6, params._group_column, params._standardize,
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
      params._standardize = false;
      HGLMModel model = new HGLM(params).trainModel().get();
      Scope.track_generic(model);
      checkWithManualResults1(model, params._response_column, _simpleFrame1, _simpleFrameSortEnum6, params._group_column, params._standardize,
              new String[]{"enum1", "enum2", "enum3", "enum4", "enum5"}, new String[]{"num1", "num2", "num3"},
              new String[]{"enum1", "enum2", "enum3", "enum5"}, new String[]{"num1", "num3"});
    } finally {
      Scope.exit();
    }
  }
  
  // todo: add check _yMinusXTimesZ and _yMinusfixPredSquare
  public void checkWithManualResults1(HGLMModel model, String response, Frame fr, Frame frSorted, String level2Name, 
                                      boolean standardize, String[] enumFixed, String[] numFixed, String[] enumRandom, 
                                      String[] numRandom) {
    double[] fixedRowValues;
    double[] randomRowValues;
    double[] randomRowValuesS;
    int numLevel2Vals = fr.vec(level2Name).domain().length;
    double[][][] afjTAfj = new double[numLevel2Vals][][];
    double[][][] arjTArj = new double[numLevel2Vals][][];
    double[][][] afjTArj = new double[numLevel2Vals][][];
    double[][] afjTYj = new double[numLevel2Vals][];
    double[][] arjTYj = new double[numLevel2Vals][];
    //double[][] zTTimesZ = new double[model._output._zttimesz.length][model._output._zttimesz.length];
    double[][] tempZTTZ = new double[model._output._arjtarj[0].length][model._output._arjtarj[0].length];
    double yMinusfixPredSquare = 0;
    double[][] yMinusXTimesZ = new double[numLevel2Vals][model._output._yminusxtimesz_score[0].length];
    double[] beta = model._output._beta;

    int numRow = (int) fr.numRows();
    double responseVal; 
    int unit2LevelS;
    int unit2Level;
    double fixEffect;
    double respMinusFix;
    
    for (int rowInd = 0; rowInd < numRow; rowInd++) {
      fixedRowValues = grabRow2Arrays(enumFixed, numFixed, true, rowInd, fr, standardize, model._parms._use_all_factor_levels);
      randomRowValues = grabRow2Arrays(enumRandom, numRandom, model._parms._random_intercept, rowInd, fr, standardize, model._parms._use_all_factor_levels);
      randomRowValuesS = grabRow2Arrays(enumRandom, numRandom, model._parms._random_intercept, rowInd, frSorted, standardize, model._parms._use_all_factor_levels);
      responseVal = fr.vec(response).at(rowInd);
      unit2LevelS = (int) frSorted.vec(level2Name).at(rowInd);
      unit2Level = (int) fr.vec(level2Name).at(rowInd);
      // calculate the various matrices and vectors
      formMatrix(afjTAfj, unit2Level, fixedRowValues, fixedRowValues); // calculate afjTAfj
      formMatrix(arjTArj, unit2Level, randomRowValues, randomRowValues); // calculate arjTArj
      formMatrix(afjTArj, unit2Level, fixedRowValues, randomRowValues); // calculate afjTArj
      formVector(afjTYj, unit2Level, fixedRowValues, responseVal); // calculate afjTYj
      formVector(arjTYj, unit2Level, randomRowValues, responseVal); // calculate arjTYj
  //    formZTTimesZ(zTTimesZ, unit2LevelS, randomRowValuesS, tempZTTZ);
      fixEffect = innerProduct(fixedRowValues, beta);
      respMinusFix = responseVal - fixEffect;
      yMinusfixPredSquare += respMinusFix*respMinusFix;
      ArrayUtils.add(yMinusXTimesZ[unit2Level], ArrayUtils.mult(randomRowValues, respMinusFix));
    }
    
    // make sure manually generated matrices/vectors and those from model._output are the same
    checkDoubleArrays(model._output._afjtyj, afjTYj, 1e-6);
    checkDoubleArrays(model._output._arjtyj, arjTYj, 1e-6);
    check3DArrays(model._output._afjtafj, afjTAfj, 1e-6);
    check3DArrays(model._output._afjtarj, afjTArj, 1e-6);
    check3DArrays(model._output._arjtarj, arjTArj, 1e-6);
  //  checkDoubleArrays(model._output._zttimesz, zTTimesZ, 1e-6);
    checkDoubleArrays(model._output._yminusxtimesz_score, yMinusXTimesZ, 1e-6);
    assertEquals(model._output._yMinusfixPredSquare, yMinusfixPredSquare, 1e-6);
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
                                 Frame fr, boolean standardize, boolean useAllFactorLevels) {
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
      if (standardize)
        rowValues.add((val - fr.vec(numName).mean())/fr.vec(numName).sigma());
      else
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
      checkDoubleArrays(model._output._afjtyj, model2._output._afjtyj, 1e-6);
      checkDoubleArrays(model._output._arjtyj, model2._output._arjtyj, 1e-6);
      check3DArrays(model._output._afjtafj, model2._output._afjtafj, 1e-6);
      check3DArrays(model._output._afjtarj, model2._output._afjtarj, 1e-6);
      check3DArrays(model._output._arjtarj, model2._output._arjtarj, 1e-6);
    } finally {
      Scope.exit();
    }
  }

  /*
   * We will try to set initial values for fixed coefficients, random coefficients, T matrix, and sigma values and make
   * sure they are set correctly
   */
  @Test
  public void testSetInitBetasTvar() {
    Scope.enter();
    try {
      Frame prostate = parseAndTrackTestFile("smalldata/prostate/prostate.csv");
      prostate.replace(3, prostate.vec(3).toCategoricalVec()).remove();
      prostate.replace(4, prostate.vec(4).toCategoricalVec()).remove();
      prostate.replace(5, prostate.vec(5).toCategoricalVec()).remove();
      DKV.put(prostate);
      double[] initBetas = new double[]{0.57305, 0.95066, 0.4277, 0.2814, 0.3727, 0.9974, 0.92813, 0.8042, 0.77725,
              0.4703, 0.1278};
      Frame ubetaFrame = new TestFrameBuilder()
              .withColNames("C1","C2","C3","C4","C5","C6","C7","C8")
              .withVecTypes(T_NUM, T_NUM, T_NUM, T_NUM, T_NUM, T_NUM, T_NUM, T_NUM)
              .withDataForCol(0, new double[]{0.8927, 0.7133, 0.08293})
              .withDataForCol(1, new double[]{0.8234, 0.7421, 0.6429})
              .withDataForCol(2, new double[]{0.70475, 0.3551, 0.4599})
              .withDataForCol(3, new double[]{0.10475, 0.2551, 0.3599})
              .withDataForCol(4, new double[]{0.6011, 0.2649, 0.8661})
              .withDataForCol(5, new double[]{0.8842, 0.0266, 0.1297})
              .withDataForCol(6, new double[]{0.14113, 0.9964, 0.3418})
              .withDataForCol(7, new double[]{0.8728, 0.74046, 0.8455})
              .build();
      Scope.track(ubetaFrame);
      Frame tMat = new TestFrameBuilder()
              .withColNames("C1","C2","C3","C4","C5","C6","C7","C8")
              .withVecTypes(T_NUM, T_NUM, T_NUM, T_NUM, T_NUM, T_NUM, T_NUM, T_NUM)
              .withDataForCol(0, new double[]{10.0173, 0.3969, 0.7209, 0.5384, 0.3691, 0.3221, 0.3118, 0.5670})
              .withDataForCol(1, new double[]{0.3969, 10.2488, 0.6870, 0.8130, 0.2864, 0.4908, 0.5384, 0.6166})
              .withDataForCol(2, new double[]{0.7209, 0.6870, 10.4728, 0.3921, 0.5716, 0.7086, 0.1817, 0.3230})
              .withDataForCol(3, new double[]{0.5384, 0.8130, 0.3921, 10.4303, 0.3022, 0.9212, 0.7127, 0.5969})
              .withDataForCol(4, new double[]{0.3691, 0.2864, 0.5716, 0.3022, 10.2905, 0.0963, 0.6902, 0.4802})
              .withDataForCol(5, new double[]{0.3221, 0.4908, 0.7086, 0.9212, 0.0963, 10.7323, 0.7772, 0.8325})
              .withDataForCol(6, new double[]{0.3118, 0.5384, 0.1817, 0.7127, 0.6902, 0.7772, 10.1840, 0.6967})
              .withDataForCol(7, new double[]{0.5670, 0.6166, 0.3230, 0.5969, 0.4802, 0.8325, 0.6967, 10.3962})
              .build();
      Scope.track(tMat);
      double sigmaEpsilon = 0.09847638;
      HGLMModel.HGLMParameters params = new HGLMModel.HGLMParameters();
      params._train = prostate._key;
      params._response_column = "VOL";
      params._ignored_columns = new String[]{"ID"};
      params._group_column = "RACE";
      params._use_all_factor_levels = true;
      params._random_columns = new String[]{"GLEASON", "DPROS", "DCAPS"};
      params._initial_fixed_effects = initBetas;
      params._initial_t_matrix = tMat._key;
      params._initial_random_effects = ubetaFrame._key;
      params._standardize = false;
      params._tau_e_var_init = sigmaEpsilon;
      params._max_iterations = 0;
      HGLMModel model = new HGLM(params).trainModel().get();
      Scope.track_generic(model);
      checkCorrectInitValue(model, initBetas, ubetaFrame, tMat, sigmaEpsilon);
    } finally {
      Scope.exit();
    }
  }
  
  public void checkCorrectInitValue(HGLMModel model, double[] initBetas, Frame ubetaFrame, Frame tMat, double sigmaEpsilon) {
    // check fixed coefficient initialization
    checkArrays(initBetas, model._output._beta, 1e-6);
    // check random coefficient initialization
    double[][] ubetaInit = new double[(int) ubetaFrame.numRows()][(int) ubetaFrame.numCols()];
    final ArrayUtils.FrameToArray f2a = new ArrayUtils.FrameToArray(0, ubetaInit[0].length-1, 
            ubetaInit.length, ubetaInit);
    ubetaInit = f2a.doAll(ubetaFrame).getArray();
    checkDoubleArrays(ubetaInit, model._output._ubeta, 1e-6);
    // check T matrix initialization
    double[][] tMatInit = new double[tMat.numCols()][tMat.numCols()];
    final ArrayUtils.FrameToArray f2a2 = new ArrayUtils.FrameToArray(0, tMat.numCols()-1, tMatInit.length, tMatInit);
    tMatInit = f2a2.doAll(tMat).getArray();
    checkDoubleArrays(tMatInit, model._output._tmat, 1e-6);
    // check sigma epsilon initializaiton
    assertEquals(sigmaEpsilon, model._output._tau_e_var, 1e-6);
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
      params._standardize = false;
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
    checkDoubleArrays(correctTMat, model._output._tmat, 1e-6);
  }

  /***
   * In this test, I check and make sure the normalization (standardization) and de-normalization (de-standardization)
   * of coefficients are done correctly. First, I setup initial coefficient values to build a model that has 
   * standardize = true.  In this case, the initial coefficients are treated as normalized coefficients.  
   * 
   * model1 is built and the following should be true:
   * 1. model1._output._beta_normalized should equal initBetaStandardize;
   * 2. model1._output._ubeta_normalized should equal to the transpose of initUBetaStandardize;
   * 
   * Next, we build a model2 with standardize = false and the initial coefficients are set to model1._output._beta and
   * model1._output._ubeta.  If the normalization and de-normalization is done correctly, the following should be true:
   * 1. model2._output._beta == model1._output._beta;
   * 2. model2._output._ubeta == model1._output._ubeta;
   * 3. model2._output._beta_normalized = model1._output._beta_normalized == initBetaStandardize;
   * 4. model2._output._ubeta_normalized = model1._output._ubeta_normalized == transpose of initUBetaStandardize;
   * 
   * We will be checking all the statements.
   */
  @Test
  public void testCoeffDeNNormalizationWithRandomIntercept() {
    try {
      Scope.enter();
      double[] initBetaStandardize = new double[]{0.57305, 0.95066, 0.4277, 0.2814, 0.3727};
      double[][] initUBetaStandardize = new double[][]{{-1.4257393174908208, 1.9459515904358207, -1.5121424866231998,
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
      Frame ubetaFrameStandardize = new TestFrameBuilder()
              .withColNames("x1", "x3", "intercept")
              .withVecTypes(T_NUM, T_NUM, T_NUM)
              .withDataForCol(0, initUBetaStandardize[0])
              .withDataForCol(1, initUBetaStandardize[1])
              .withDataForCol(2, initUBetaStandardize[2])
              .build();
      Scope.track(ubetaFrameStandardize);
      
      Frame fr = parseTestFile("smalldata/hglm_test/semiconductor.csv");
      fr.replace(0, fr.vec(0).toCategoricalVec()).remove();
      DKV.put(fr);
      Scope.track(fr);
      HGLMModel.HGLMParameters parms = new HGLMModel.HGLMParameters();
      parms._train = fr._key;
      parms._response_column = "y";
      parms._ignored_columns = new String[]{"x2", "x4"};
      parms._ignore_const_cols = true;
      parms._family = GLMModel.GLMParameters.Family.gaussian;
      parms._random_columns = new String[]{"x1", "x3"};
      parms._group_column = "Device";
      parms._max_iterations = 0;
      parms._seed = 1234;
      parms._initial_fixed_effects = initBetaStandardize;
      parms._initial_random_effects = ubetaFrameStandardize._key;
      parms._random_intercept = true;

      // just make sure it runs
      HGLMModel model1 = new HGLM(parms).trainModel().get();
      Scope.track_generic(model1);
      // the initial coefficients are set to model as standardized coefficients, get the denormalized coefficients here
      Frame uinitBetaFrame = makeUBetaFrame(model1._output._ubeta);
      Scope.track(uinitBetaFrame);
      // build model 2 that is not standardized
      parms._standardize = false;
      parms._initial_fixed_effects = model1._output._beta;
      parms._initial_random_effects = uinitBetaFrame._key;
      HGLMModel model2 = new HGLM(parms).trainModel().get();
      Scope.track_generic(model2);
      // if all the normalization and de-normalization is done correctly, we should have
      TestUtil.checkArrays(model1._output._beta_normalized, initBetaStandardize, 1e-12);
      TestUtil.checkDoubleArrays(model1._output._ubeta_normalized, new Matrix(initUBetaStandardize).transpose().getArray(),
              1e-12);
      TestUtil.checkArrays(model1._output._beta, model2._output._beta, 1e-12);
      TestUtil.checkArrays(model1._output._beta_normalized, model2._output._beta_normalized, 1e-12);
      TestUtil.checkDoubleArrays(model1._output._ubeta, model2._output._ubeta, 1e-12);
      TestUtil.checkDoubleArrays(model1._output._ubeta_normalized, model2._output._ubeta_normalized, 1e-12);
      // manually check a few cases to make sure things are actually running okay
      assertEquals(model1._output._ubeta[0][0], model1._output._ubeta_normalized[0][0]/fr.vec("x1").sigma(),  1e-6);
      assertEquals(model1._output._ubeta_normalized[1][2], 
              model1._output._ubeta[1][2]+fr.vec("x1").mean()*model1._output._ubeta[1][0]/fr.vec("x1").sigma() +
                      fr.vec("x2").mean()*model1._output._ubeta[1][1]/fr.vec("x2").sigma(), 1e-6);
      assertEquals(model2._output._beta_normalized[3], model2._output._beta[3]*fr.vec("x6").sigma(), 1e-6);
      assertEquals(model2._output._beta_normalized[4], model2._output._beta[4]+
              fr.vec("x1").mean()*model2._output._beta_normalized[0]/fr.vec("x1").sigma()+
              fr.vec("x3").mean()*model2._output._beta_normalized[1]/fr.vec("x3").sigma()+
              fr.vec("x5").mean()*model2._output._beta_normalized[2]/fr.vec("x5").sigma()+
              fr.vec("x6").mean()*model2._output._beta_normalized[3]/fr.vec("x6").sigma(), 1e-6);
    } finally {
      Scope.exit();
    }
  }

  /***
   * This test is exactly like the one in testCoeffDeNNormalizationWithRandomIntercept with the exception that there
   * is no random intercept.
   */
  @Test
  public void testCoeffDeNNormalizationWORandomIntercept() {
    try {
      Scope.enter();
      double[] initBetaStandardize = new double[]{0.57305, 0.95066, 0.4277, 0.2814, 0.3727};
      double[][] initUBetaStandardize = new double[][]{{-1.4257393174908208, 1.9459515904358207, -1.5121424866231998,
              0.757565557144771, 1.6454093526843507, 0.521525656276774, 0.15102292603863332,
              -0.5629664504958487, 0.39941437871543806, -0.17666156140184344, -0.9012256565441157,
              0.4013361512547679, -0.7655048415710769, 0.9625031349421274, -1.6916150004681492, 0.8967295711861796},
              {0.7307560306666573, -0.43350728257793125, 0.761204681372934,
                      -0.9665905711121056, -0.0485193797802151, -0.6595712372715338, -0.4616825414753406,
                      0.7886590178655907, 0.27241373557806586, -0.04301812863182515, -0.10936899265127145,
                      0.8173502195208687, -0.1473779447485634, -2.1395714941712223, -0.9096112739244531, -1.8557521580762681}};
      Frame ubetaFrameStandardize = new TestFrameBuilder()
              .withColNames("x1", "x3")
              .withVecTypes(T_NUM, T_NUM)
              .withDataForCol(0, initUBetaStandardize[0])
              .withDataForCol(1, initUBetaStandardize[1])
              .build();
      Scope.track(ubetaFrameStandardize);

      Frame fr = parseTestFile("smalldata/hglm_test/semiconductor.csv");
      fr.replace(0, fr.vec(0).toCategoricalVec()).remove();
      DKV.put(fr);
      Scope.track(fr);
      HGLMModel.HGLMParameters parms = new HGLMModel.HGLMParameters();
      parms._train = fr._key;
      parms._response_column = "y";
      parms._ignored_columns = new String[]{"x2", "x4"};
      parms._ignore_const_cols = true;
      parms._family = GLMModel.GLMParameters.Family.gaussian;
      parms._random_columns = new String[]{"x1", "x3"};
      parms._group_column = "Device";
      parms._max_iterations = 0;
      parms._seed = 1234;
      parms._initial_fixed_effects = initBetaStandardize;
      parms._initial_random_effects = ubetaFrameStandardize._key;
      parms._random_intercept = false;
      parms._standardize = true;

      // just make sure it runs
      HGLMModel model1 = new HGLM(parms).trainModel().get();
      Scope.track_generic(model1);
      // the initial coefficients are set to model as standardized coefficients, get the denormalized coefficients here
      Frame uinitBetaFrame = makeUBetaFrame(model1._output._ubeta);
      Scope.track(uinitBetaFrame);
      // build model 2 that is not standardized
      parms._standardize = false;
      parms._random_intercept = true;
      parms._initial_fixed_effects = model1._output._beta;
      parms._initial_random_effects = uinitBetaFrame._key;
      HGLMModel model2 = new HGLM(parms).trainModel().get();
      Scope.track_generic(model2);
      // If all the normalization and de-normalization is done correctly, we should have
      TestUtil.checkArrays(model1._output._beta_normalized, initBetaStandardize, 1e-12);
      TestUtil.checkDoubleArrays(model1._output._ubeta_normalized, new Matrix(initUBetaStandardize).transpose().getArray(),
              1e-12);
      TestUtil.checkArrays(model1._output._beta, model2._output._beta, 1e-12);
      TestUtil.checkDoubleArrays(model1._output._ubeta, model2._output._ubeta, 1e-12);
      TestUtil.checkArrays(model1._output._beta_normalized, model2._output._beta_normalized, 1e-12);
      // Again, an intercept term is added when you normalize beta.  model2 will have an extra column in its ubeta.
      // The last column contains value close to 0.
      double[][] temp = new Matrix(model2._output._ubeta_normalized).transpose().getArray();
      double[][] model2ubetaN = new Matrix(new double[][] {temp[0], temp[1]}).transpose().getArray();
      TestUtil.checkDoubleArrays(model1._output._ubeta_normalized, model2ubetaN, 1e-12);
      // manually check a few cases to make sure things are actually running okay
      assertEquals(model1._output._ubeta[0][0], model1._output._ubeta_normalized[0][0]/fr.vec("x1").sigma(),  1e-6);
      assertEquals(0,
              fr.vec("x1").mean()*model1._output._ubeta[1][0]/fr.vec("x1").sigma() +
                      fr.vec("x2").mean()*model1._output._ubeta[1][1]/fr.vec("x2").sigma(), 1e-6);
      assertEquals(model2._output._beta_normalized[3], model2._output._beta[3]*fr.vec("x6").sigma(), 1e-6);
      assertEquals(model2._output._beta_normalized[4], model2._output._beta[4]+
              fr.vec("x1").mean()*model2._output._beta_normalized[0]/fr.vec("x1").sigma()+
              fr.vec("x3").mean()*model2._output._beta_normalized[1]/fr.vec("x3").sigma()+
              fr.vec("x5").mean()*model2._output._beta_normalized[2]/fr.vec("x5").sigma()+
              fr.vec("x6").mean()*model2._output._beta_normalized[3]/fr.vec("x6").sigma(), 1e-6);
    } finally {
      Scope.exit();
    }
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
    ;
  }

  @Test
  public void testSemiconductor() {
    try {
      Scope.enter();
      Frame fr = parseTestFile("smalldata/hglm_test/semiconductor.csv");
      fr.replace(0, fr.vec(0).toCategoricalVec()).remove();
      DKV.put(fr);
      Scope.track(fr);
      HGLMModel.HGLMParameters parms = new HGLMModel.HGLMParameters();
      parms._train = fr._key;
      parms._response_column = "y";
      parms._ignored_columns = new String[]{"x2", "x4"};
      parms._ignore_const_cols = true;
      parms._family = GLMModel.GLMParameters.Family.gaussian;
      parms._random_columns = new String[]{"x1", "x3"};
      parms._random_intercept = true;
      parms._group_column = "Device";
      parms._max_iterations = 1;
      
      // just make sure it runs
      HGLMModel model = new HGLM(parms).trainModel().get();
      Scope.track_generic(model);
      ;
 //     ModelMetricsHGLMGaussianGaussian mmetrics = (ModelMetricsHGLMGaussianGaussian) model._output._training_metrics;
 //     Scope.track_generic(mmetrics);
 //     assertEquals(363.6833, mmetrics._hlik, 1e-4);
      System.out.println("**************** testSemiconductor test completed. ****************");
    } finally {
      Scope.exit();
    }
  }
  

  @Test
  public void testPredictionMetricsSumaryScoringHistoryWRIntercept() {
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
      parms._standardize = true;
      // check prediction with standardize = true
      HGLMModel model = new HGLM(parms).trainModel().get();
      Scope.track_generic(model);
      Frame predFrame = model.score(fr);
      Scope.track(predFrame);
      checkPrediction(fr, predFrame, model, 0.0);
      
      // check prediction again with standardize = false
      parms._standardize = false;
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
  public void testPredictionMetricsSumaryScoringHistoryWORIntercept() {
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
      fr.replace(0, fr.vec(0).toCategoricalVec()).remove();
      DKV.put(fr);
      Scope.track(fr);
      HGLMModel.HGLMParameters parms = new HGLMModel.HGLMParameters();
      parms._train = fr._key;
      parms._valid = validFr._key;
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
      parms._standardize = true;

      // check prediction without random intercept and with standardization
      HGLMModel model = new HGLM(parms).trainModel().get();
      Scope.track_generic(model);
      Frame predFrame = model.score(fr);
      Scope.track(predFrame);
      checkPrediction(fr, predFrame, model, 0.0);
      
      // check prediction without random intercept and without standardization
      parms._standardize = false;
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
        assertEquals(estimatedY, predFrame.vec(0).at(index), 1e-6);
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
      if (rCoeffNames[index] != "intercept")
        zvals[index] = fr.vec(rCoeffNames[index]).at(rowInd);
  }
}
