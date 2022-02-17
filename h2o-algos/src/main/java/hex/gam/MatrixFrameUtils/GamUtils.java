package hex.gam.MatrixFrameUtils;

import hex.Model;
import hex.gam.GAM;
import hex.gam.GAMModel;
import hex.gam.GAMModel.GAMParameters;
import hex.glm.GLMModel;
import hex.quantile.Quantile;
import hex.quantile.QuantileModel;
import water.DKV;
import water.Key;
import water.MemoryManager;
import water.Scope;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.TwoDimTable;

import java.lang.reflect.Field;
import java.util.*;

import static hex.gam.GamSplines.ThinPlateRegressionUtils.calculateM;
import static hex.gam.GamSplines.ThinPlateRegressionUtils.calculatem;
import static hex.gam.MatrixFrameUtils.GAMModelUtils.*;

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

    for (int frameIdx = 0; frameIdx < num2DArrays; frameIdx++) {
      if (parms._bs_sorted[frameIdx] != 2) { // cs spline
        array3D[frameIdx] = allocate2DArray(fileMode, parms._num_knots_sorted[frameIdx]);
      } else { // I-splines
        int totBasis = parms._num_knots_sorted[frameIdx] + parms._spline_orders_sorted[frameIdx] - 2; // I-spline order=NBSplineTypeII order
        array3D[frameIdx] = allocate2DArray(fileMode, totBasis);
      }
    }
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

  public static void copyCVGLMtoGAMModel(GAMModel model, GLMModel glmModel, GAMParameters parms, String foldColumn) {
    // copy over cross-validation metrics
    model._output._cross_validation_metrics = glmModel._output._cross_validation_metrics;
    model._output._cross_validation_metrics_summary =
            copyTwoDimTable(glmModel._output._cross_validation_metrics_summary,
                    "GLM cross-validation metrics summary");
    int nFolds = glmModel._output._cv_scoring_history.length;
    model._output._glm_cv_scoring_history = new TwoDimTable[nFolds];
    if (parms._keep_cross_validation_predictions)
      model._output._cross_validation_predictions = new Key[nFolds];
    
    for (int fInd = 0; fInd < nFolds; fInd++) {
      model._output._glm_cv_scoring_history[fInd] = copyTwoDimTable(glmModel._output._cv_scoring_history[fInd],
              glmModel._output._cv_scoring_history[fInd].getTableHeader());
      // copy over hold-out predictions
      if (parms._keep_cross_validation_predictions) {
        Frame pred = DKV.getGet(glmModel._output._cross_validation_predictions[fInd]);
        Frame newPred = pred.deepCopy(Key.make().toString());
        DKV.put(newPred);
        model._output._cross_validation_predictions[fInd] = newPred.getKey();
      }
    }

    // copy over cross-validation models
    if (parms._keep_cross_validation_models)
      model._output._cross_validation_models = buildCVGamModels(model, glmModel, parms, foldColumn);
    
    // copy over fold_assignments
    if (parms._keep_cross_validation_predictions) {
      Frame cvPred = DKV.getGet(glmModel._output._cross_validation_holdout_predictions_frame_id);
      Frame newPred = cvPred.deepCopy(Key.make().toString());
      DKV.put(newPred);
      model._output._cross_validation_holdout_predictions_frame_id = newPred.getKey();
    }

    if (parms._keep_cross_validation_fold_assignment) {
      Frame foldAssignment = DKV.getGet(glmModel._output._cross_validation_fold_assignment_frame_id);
      Frame newFold = foldAssignment.deepCopy((Key.make()).toString());
      DKV.put(newFold);
      model._output._cross_validation_fold_assignment_frame_id = newFold.getKey();
    }
  }

  public static Key[] buildCVGamModels(GAMModel model, GLMModel glmModel, GAMParameters parms, String foldColumn) {
    int nFolds = glmModel._output._cross_validation_models.length;
    Key[] cvModelKeys = new Key[nFolds];
    for (int fInd=0; fInd<nFolds; fInd++) {
      GLMModel cvModel = DKV.getGet(glmModel._output._cross_validation_models[fInd]);
      // set up GAMParameters
      GAMParameters gamParams = makeGAMParameters(parms);
      if (foldColumn != null) {
        if (gamParams._ignored_columns != null) {
          List<String> ignoredCols = new ArrayList<>(Arrays.asList(gamParams._ignored_columns));
          ignoredCols.add(foldColumn);
          gamParams._ignored_columns = ignoredCols.toArray(new String[0]);
        } else {
          gamParams._ignored_columns = new String[]{foldColumn};
        }
      }
      int maxIterations = gamParams._max_iterations;
      gamParams._max_iterations = 1;
      // instantiate GAMModels
      GAMModel gamModel = new GAM(gamParams).trainModel().get();
      gamParams._max_iterations = maxIterations;
      // extract GLM CV model run results to GAMModels
      copyGLMCoeffs(cvModel, gamModel, gamParams, model._nclass);
      copyGLMtoGAMModel(gamModel, cvModel, parms, true);
      cvModelKeys[fInd] = gamModel.getKey();
      DKV.put(gamModel);
    }
    return cvModelKeys;
  }
  
  public static GAMParameters makeGAMParameters(GAMParameters parms) {
    GAMParameters gamParams = new GAMParameters();
    final Field[] field1 = GAMParameters.class.getDeclaredFields();
    final Field[] field2 = Model.Parameters.class.getDeclaredFields();
    setParamField(parms, gamParams, false, field1, Collections.emptyList());
    setParamField(parms, gamParams, true, field2, Collections.emptyList());
    gamParams._nfolds = 0;
    gamParams._keep_cross_validation_predictions = false;
    gamParams._keep_cross_validation_fold_assignment = false;
    gamParams._keep_cross_validation_models = false;
    gamParams._train = parms._train;
    return gamParams;
  }

  public static void setParamField(Model.Parameters parms, Model.Parameters glmParam, boolean superClassParams,
                                   Field[] gamFields, List<String> excludeList) {
    // assign relevant GAMParameter fields to GLMParameter fields
    Field glmField;
    boolean emptyExcludeList = excludeList == null || excludeList.size() == 0;
    for (Field oneField : gamFields) {
      try {
        if (emptyExcludeList || !excludeList.contains(oneField.getName())) {
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

  public static void keepFrameKeys(List<Key> keep, Key<Frame> ... keyNames) {
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
      if (parms._bs[index] == 1) {
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

  // grab all predictors to build a smoother
  public static Frame prepareGamVec(int gam_column_index, GAMParameters parms, Frame fr) {
    final Vec weights_column = (parms._weights_column == null) ? Scope.track(Vec.makeOne(fr.numRows()))
            : fr.vec(parms._weights_column);
    final Frame predictVec = new Frame();
    int numPredictors = parms._gam_columns_sorted[gam_column_index].length;
    for (int colInd = 0; colInd < numPredictors; colInd++)
      predictVec.add(parms._gam_columns_sorted[gam_column_index][colInd],
              fr.vec(parms._gam_columns_sorted[gam_column_index][colInd]));
    predictVec.add("weights_column", weights_column); // add weight columns for CV support
    return predictVec;
  }

  public static String[] generateGamColNames(int gamColIndex, GAMParameters parms) {
    String[] newColNames = null;
    if (parms._bs_sorted[gamColIndex] == 0)
      newColNames = new String[parms._num_knots_sorted[gamColIndex]];
    else
      newColNames = new String[parms._num_knots_sorted[gamColIndex]+parms._spline_orders_sorted[gamColIndex]-2];

/*    StringBuffer nameStub = new StringBuffer();
    int numPredictors = parms._gam_columns_sorted[gamColIndex].length;
    for (int predInd = 0; predInd < numPredictors; predInd++) {
      nameStub.append(parms._gam_columns_sorted[gamColIndex][predInd]+"_");
    }*/
    String stubName = parms._gam_columns_sorted[gamColIndex][0]+"_";
    for (int knotIndex = 0; knotIndex < newColNames.length; knotIndex++) {
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
      weightsVec = train.remove(parms._weights_column);
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
  
  /**
   * move CS spline smoothers to the front and TP spline smoothers to the back for arrays:
   * gam_columns, bs, scale, num_knots.
   * The array knots have already been moved with CS spline/I-spline in the front and TP splines in the back
   */
  public static void sortGAMParameters(GAMParameters parms, int singlePredictorSmootherNum) {
    int gamColNum = parms._gam_columns.length;
    int csIsIndex = 0;
    int tpIndex = singlePredictorSmootherNum;
    parms._gam_columns_sorted = new String[gamColNum][];
    parms._num_knots_sorted = MemoryManager.malloc4(gamColNum);
    parms._scale_sorted = MemoryManager.malloc8d(gamColNum);
    parms._bs_sorted = MemoryManager.malloc4(gamColNum);
    parms._gamPredSize = MemoryManager.malloc4(gamColNum);
    parms._spline_orders_sorted = MemoryManager.malloc4(gamColNum);
    for (int index = 0; index < gamColNum; index++) {
      if (parms._bs[index] == 0 || parms._bs[index] == 2) { // cubic spline
        parms._gam_columns_sorted[csIsIndex] = parms._gam_columns[index].clone();
        parms._num_knots_sorted[csIsIndex] = parms._num_knots[index];
        parms._scale_sorted[csIsIndex] = parms._scale[index];
        parms._gamPredSize[csIsIndex] = parms._gam_columns_sorted[csIsIndex].length;
        parms._bs_sorted[csIsIndex] = parms._bs[index];
        if (parms._spline_orders == null)
          parms._spline_orders_sorted[csIsIndex++] = 1;
        else
          parms._spline_orders_sorted[csIsIndex++] = parms._spline_orders[index];
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
