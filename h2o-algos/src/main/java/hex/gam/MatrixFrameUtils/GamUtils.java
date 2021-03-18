package hex.gam.MatrixFrameUtils;

import hex.Model;
import hex.gam.GAMModel.GAMParameters;
import hex.glm.GLMModel.GLMParameters;
import hex.quantile.Quantile;
import hex.quantile.QuantileModel;
import water.DKV;
import water.Key;
import water.MemoryManager;
import water.Scope;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static hex.gam.GamSplines.ThinPlateRegressionUtils.calculateM;
import static hex.gam.GamSplines.ThinPlateRegressionUtils.calculatem;

public class GamUtils {

  // allocate 3D array to store various information;
  public static double[][][] allocate3DArrayCS(int num2DArrays, GAMParameters parms, AllocateType fileMode) {
    double[][][] array3D = new double[num2DArrays][][];
    int gamColCount = 0;
    for (int frameIdx = 0; frameIdx < num2DArrays; frameIdx++) {
      if (parms._gam_columns_sorted[frameIdx].length == 1) {
        int numKnots = parms._num_knots_sorted[frameIdx];
        array3D[gamColCount++] = allocate2DArray(fileMode, numKnots);
      }
    }
    return array3D;
  }

  public static double[][][] allocate3DArray(int num2DArrays, GAMParameters parms, AllocateType fileMode) {
    double[][][] array3D = new double[num2DArrays][][];
    for (int frameIdx = 0; frameIdx < num2DArrays; frameIdx++)
        array3D[frameIdx] = allocate2DArray(fileMode, parms._num_knots_sorted[frameIdx]);
    return array3D;
  }

  // allocate 3D array to store various information;
  public static double[][][] allocate3DArrayTP(int num2DArrays, GAMParameters parms, int[] secondDim, int[] thirdDim) {
    double[][][] array3D = new double[num2DArrays][][];
    int gamColCount = 0;
    int numGamCols = parms._gam_columns.length;
    for (int frameIdx = 0; frameIdx < numGamCols; frameIdx++) {
      if (parms._bs_sorted[frameIdx] == 1) {
        array3D[gamColCount] = MemoryManager.malloc8d(secondDim[gamColCount], thirdDim[gamColCount]);
        gamColCount++;
      }
    }
    return array3D;
  }

  // allocate 3D array to store various information;
  public static double[][] allocate2DArray(AllocateType fileMode, int numKnots) {
    double[][] array2D;
      switch (fileMode) {
        case firstOneLess: array2D = MemoryManager.malloc8d(numKnots-1, numKnots); break;
        case sameOrig: array2D = MemoryManager.malloc8d(numKnots, numKnots); break;
        case bothOneLess: array2D = MemoryManager.malloc8d(numKnots-1, numKnots-1); break;
        case firstTwoLess: array2D = MemoryManager.malloc8d(numKnots-2, numKnots); break;
        default: throw new IllegalArgumentException("fileMode can only be firstOneLess, sameOrig, bothOneLess or " +
                "firstTwoLess.");
      }
    return array2D;
  }

  public enum AllocateType {firstOneLess, sameOrig, bothOneLess, firstTwoLess} // special functions are performed depending on GLMType.  Internal use

  public static Integer[] sortCoeffMags(int arrayLength, double[] coeffMags) {
    Integer[] indices = new Integer[arrayLength];
    for (int i = 0; i < indices.length; ++i)
      indices[i] = i;
    Arrays.sort(indices, new Comparator<Integer>() {
      @Override
      public int compare(Integer o1, Integer o2) {
        if (coeffMags[o1] < coeffMags[o2]) return +1;
        if (coeffMags[o1] > coeffMags[o2]) return -1;
        return 0;
      }
    });
    return indices;
  }
  
  public static boolean equalColNames(String[] name1, String[] standardN, String response_column) {
    boolean name1ContainsResp = ArrayUtils.contains(name1, response_column);
    boolean standarNContainsResp = ArrayUtils.contains(standardN, response_column);
    boolean equalNames = name1.length==standardN.length;
    
    if (name1ContainsResp && !standarNContainsResp)   // if name1 contains response but standardN does not
      equalNames = name1.length==(standardN.length+1);
    else if (!name1ContainsResp && standarNContainsResp)  // if name1 does not contain response but standardN does
      equalNames = (name1.length+1)==standardN.length;
    
    if (equalNames) { // number of columns are correct but with the same column names and column types?
      for (String name : name1) {
        if (name==response_column)  // leave out the response columns in this comparison.  Only worry about predictors
          continue;
        if (!ArrayUtils.contains(standardN, name))
          return false;
      }
      return true;
    } else
      return equalNames;
  }

  public static void copy2DArray(double[][] src_array, double[][] dest_array) {
    int numRows = src_array.length;
    for (int colIdx = 0; colIdx < numRows; colIdx++) { // save zMatrix for debugging purposes or later scoring on training dataset
      System.arraycopy(src_array[colIdx], 0, dest_array[colIdx], 0,
              src_array[colIdx].length);
    }
  }

  public static void copy2DArray(int[][] src_array, int[][] dest_array) {
    int numRows = src_array.length;
    for (int colIdx = 0; colIdx < numRows; colIdx++) { // save zMatrix for debugging purposes or later scoring on training dataset
      System.arraycopy(src_array[colIdx], 0, dest_array[colIdx], 0,
              src_array[colIdx].length);
    }
  }

  public static GLMParameters copyGAMParams2GLMParams(GAMParameters parms, Frame trainData, Frame valid) {
    GLMParameters glmParam = new GLMParameters();
    Field[] field1 = GAMParameters.class.getDeclaredFields();
    setParamField(parms, glmParam, false, field1);
    Field[] field2 = Model.Parameters.class.getDeclaredFields();
    setParamField(parms, glmParam, true, field2);
    glmParam._train = trainData._key;
    glmParam._valid = valid==null?null:valid._key;
    glmParam._nfolds = 0; // always set nfolds to 0 to disable cv in GLM.  It is done in GAM
    glmParam._fold_assignment = Model.Parameters.FoldAssignmentScheme.AUTO;
    glmParam._keep_cross_validation_fold_assignment = false;
    glmParam._keep_cross_validation_models = false;
    glmParam._keep_cross_validation_predictions = false;
    glmParam._is_cv_model = false; // disable cv in GLM.
    return glmParam;
  }

  public static void setParamField(GAMParameters parms, GLMParameters glmParam, boolean superClassParams, Field[] gamFields) {
    // assign relevant GAMParameter fields to GLMParameter fields
    List<String> gamOnlyList = Arrays.asList(
            "_num_knots", "_gam_columns", "_bs", "_scale", "_train", 
        "_saveZMatrix", "_saveGamCols", "_savePenaltyMat"
    );
    Field glmField;
    for (Field oneField : gamFields) {
      try {
        if (!gamOnlyList.contains(oneField.getName())) {
          if (superClassParams)
            glmField = glmParam.getClass().getSuperclass().getDeclaredField(oneField.getName());
          else
            glmField = glmParam.getClass().getDeclaredField(oneField.getName());
          glmField.set(glmParam, oneField.get(parms));
        }
      } catch (IllegalAccessException|NoSuchFieldException e) { // suppress error printing, only cares about fields that are accessible
        ;
      }
    }
  }

  public static void keepFrameKeys(List<Key<Vec>> keep, Key<Frame> ... keyNames) {
    for (Key<Frame> keyName:keyNames) {
      Frame loadingFrm = DKV.getGet(keyName);
      if (loadingFrm != null) for (Vec vec : loadingFrm.vecs()) keep.add(vec._key);
    }
  }

  public static void setDefaultBSType(GAMParameters parms) {
    parms._bs = new int[parms._gam_columns.length];
    for (int index = 0; index < parms._bs.length; index++) {
      if (parms._gam_columns[index].length > 1) {
        parms._bs[index] = 1;
      } else {
        parms._bs[index] = 0;
      }
    }
  }

  public static void setThinPlateParameters(GAMParameters parms, int thinPlateNum) {
    int numGamCols = parms._gam_columns.length;
    parms._m = MemoryManager.malloc4(thinPlateNum);
    parms._M = MemoryManager.malloc4(thinPlateNum);
    int countThinPlate = 0;
    for (int index = 0; index < numGamCols; index++) {
      if (parms._bs[index] == 1) { // todo: add in bs==2 when it is supported
        int d = parms._gam_columns[index].length;
        parms._m[countThinPlate] = calculatem(d);
        parms._M[countThinPlate] = calculateM(d, parms._m[countThinPlate]);
        countThinPlate++;
      }
    }
  }
  
  public static void setGamPredSize(GAMParameters parms, int csOffset) {
    int numGamCols = parms._gam_columns.length;
    int tpCount = csOffset;
    int csCount = 0;
    parms._gamPredSize = MemoryManager.malloc4(numGamCols);
    for (int index = 0; index < numGamCols; index++) {
      if (parms._gam_columns[index].length == 1) { // CS
        parms._gamPredSize[csCount++] = 1;
      } else {  // TP
        parms._gamPredSize[tpCount++] = parms._gam_columns[index].length;
      }
    }
  }

  // This method will generate knot locations by choosing them from a uniform quantile distribution of that
  // chosen column.
  public static double[] generateKnotsOneColumn(Frame gamFrame, int knotNum) {
    double[] knots = MemoryManager.malloc8d(knotNum);
    try {
      Scope.enter();
      Frame tempFrame = new Frame(gamFrame);  // make sure we have a frame key
      DKV.put(tempFrame);
      double[] prob = MemoryManager.malloc8d(knotNum);
      assert knotNum > 1;
      double stepProb = 1.0 / (knotNum - 1);
      for (int knotInd = 0; knotInd < knotNum; knotInd++)
        prob[knotInd] = knotInd * stepProb;
      QuantileModel.QuantileParameters parms = new QuantileModel.QuantileParameters();
      parms._train = tempFrame._key;
      parms._probs = prob;
      QuantileModel qModel = new Quantile(parms).trainModel().get();
      DKV.remove(tempFrame._key);
      Scope.track_generic(qModel);
      System.arraycopy(qModel._output._quantiles[0], 0, knots, 0, knotNum);
    } finally {
      Scope.exit();
    }
    return knots;
  }

  // grad all predictors to build a smoother
  public static Frame prepareGamVec(int gam_column_index, GAMParameters parms, Frame fr) {
    final Vec weights_column = (parms._weights_column == null) ? Scope.track(Vec.makeOne(fr.numRows()))
            : Scope.track(fr.vec(parms._weights_column));
    final Frame predictVec = new Frame();
    int numPredictors = parms._gam_columns_sorted[gam_column_index].length;
    for (int colInd = 0; colInd < numPredictors; colInd++)
      predictVec.add(parms._gam_columns_sorted[gam_column_index][colInd],
              fr.vec(parms._gam_columns_sorted[gam_column_index][colInd]));
    predictVec.add("weights_column", weights_column); // add weight columns for CV support
    return predictVec;
  }

  public static String[] generateGamColNames(int gam_col_index, GAMParameters parms) {
    String[] newColNames = new String[parms._num_knots_sorted[gam_col_index]];
    StringBuffer nameStub = new StringBuffer();
    int numPredictors = parms._gam_columns_sorted[gam_col_index].length;
    for (int predInd = 0; predInd < numPredictors; predInd++) {
      nameStub.append(parms._gam_columns_sorted[gam_col_index][predInd]+"_");
    }
    String stubName = nameStub.toString();
    for (int knotIndex = 0; knotIndex < parms._num_knots_sorted[gam_col_index]; knotIndex++) {
      newColNames[knotIndex] = stubName+knotIndex;
    }
    return newColNames;
  }
  
  public static String[] generateGamColNamesThinPlateKnots(int gamColIndex, GAMParameters parms, 
                                                           int[][] polyBasisDegree, String nameStub) {
    int num_knots = parms._num_knots_sorted[gamColIndex];
    int polyBasisSize = polyBasisDegree.length;
    String[] gamColNames = new String[num_knots+polyBasisSize];
    for (int index = 0; index < num_knots; index++)
      gamColNames[index] = nameStub+index;
    
    for (int index = 0; index < polyBasisSize; index++) {
      gamColNames[index+num_knots] = genPolyBasisNames(parms._gam_columns_sorted[gamColIndex], polyBasisDegree[index]);
    }
    return gamColNames;
  }
  
  public static String genPolyBasisNames(String[] gam_columns, int[] oneBasis) {
    StringBuffer polyBasisName = new StringBuffer();
    int numGamCols = gam_columns.length;
    int beforeLastIndex = numGamCols-1;
    for (int index = 0; index < numGamCols; index++) {
      polyBasisName.append(gam_columns[index]);
      polyBasisName.append("_");
      polyBasisName.append(oneBasis[index]);
      if (index < beforeLastIndex)
        polyBasisName.append("_");
    }
    return polyBasisName.toString();
  }

  public static Frame buildGamFrame(GAMParameters parms, Frame train, Key<Frame>[] gamFrameKeysCenter) {
    Vec responseVec = train.remove(parms._response_column);
    Vec weightsVec = null;
    if (parms._weights_column != null) // move weight vector to be the last vector before response variable
      weightsVec = Scope.track(train.remove(parms._weights_column));
    for (int colIdx = 0; colIdx < parms._gam_columns_sorted.length; colIdx++) {  // append the augmented columns to _train
      Frame gamFrame = Scope.track(gamFrameKeysCenter[colIdx].get());
      train.add(gamFrame.names(), gamFrame.removeAll());
      train.remove(parms._gam_columns_sorted[colIdx]);
    }
    if (weightsVec != null)
      train.add(parms._weights_column, weightsVec);
    if (responseVec != null)
      train.add(parms._response_column, responseVec);
    return train;
  }

  public static Frame concateGamVecs(Key<Frame>[] gamFrameKeysCenter) {
    Frame gamVecs =  new Frame(Key.make());
    for (int index = 0; index < gamFrameKeysCenter.length; index++) {
      Frame tempCols = Scope.track(gamFrameKeysCenter[index].get());
      gamVecs.add(tempCols.names(), tempCols.removeAll());
    }
    return gamVecs;
  }
  
  // move CS spline smoothers to the front and TP spline smoothers to the back for arrays:
  // gam_columns, bs, scale, num_knots
  public static void sortGAMParameters(GAMParameters parms, int csNum, int tpNum) {
    int gamColNum = parms._gam_columns.length;
    int csIndex = 0;
    int tpIndex = csNum;
    parms._gam_columns_sorted = new String[gamColNum][];
    parms._num_knots_sorted = MemoryManager.malloc4(gamColNum);
    parms._scale_sorted = MemoryManager.malloc8d(gamColNum);
    parms._bs_sorted = MemoryManager.malloc4(gamColNum);
    parms._gamPredSize = MemoryManager.malloc4(gamColNum);
    for (int index = 0; index < gamColNum; index++) {
      if (parms._bs[index] == 0) { // cubic spline
        parms._gam_columns_sorted[csIndex] = parms._gam_columns[index].clone();
        parms._num_knots_sorted[csIndex] = parms._num_knots[index];
        parms._scale_sorted[csIndex] = parms._scale[index];
        parms._gamPredSize[csIndex] = parms._gam_columns_sorted[csIndex].length;
        parms._bs_sorted[csIndex++] = parms._bs[index];
      } else {  // thin plate
        parms._gam_columns_sorted[tpIndex] = parms._gam_columns[index].clone();
        parms._num_knots_sorted[tpIndex] = parms._num_knots[index];
        parms._scale_sorted[tpIndex] = parms._scale[index];
        parms._gamPredSize[tpIndex] = parms._gam_columns_sorted[tpIndex].length;
        parms._bs_sorted[tpIndex++] = parms._bs[index];
      }
    }
  }

  // default value of scale is 1.0
  public static void setDefaultScale(GAMParameters parms) {
    int numGamCol = parms._gam_columns.length;
    parms._scale = new double[numGamCol];
    for (int index = 0; index < numGamCol; index++)
      parms._scale[index] = 1.0;
  }
}
