package hex.gam.MatrixFrameUtils;

import hex.DataInfo;
import hex.Model;
import hex.gam.GAMModel;
import hex.gam.GAMModel.GAMParameters;
import hex.glm.GLMModel;
import hex.glm.GLMModel.GLMParameters;
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

public class GamUtils {

  // allocate 3D array to store various information;
  public static double[][][] allocate3DArray(int num2DArrays, GAMParameters parms, AllocateType fileMode) {
    double[][][] array3D = new double[num2DArrays][][];
    for (int frameIdx = 0; frameIdx < num2DArrays; frameIdx++) {
      int numKnots = parms._num_knots[frameIdx];
      switch (fileMode) {
        case firstOneLess: array3D[frameIdx] = MemoryManager.malloc8d(numKnots-1, numKnots); break;
        case sameOrig: array3D[frameIdx] = MemoryManager.malloc8d(numKnots, numKnots); break;
        case bothOneLess: array3D[frameIdx] = MemoryManager.malloc8d(numKnots-1, numKnots-1); break;
        case firstTwoLess: array3D[frameIdx] = MemoryManager.malloc8d(numKnots-2, numKnots); break;
        default: throw new IllegalArgumentException("fileMode can only be firstOneLess, sameOrig, bothOneLess or " +
                "firstTwoLess.");
      }
    }
    return array3D;
  }

  public static enum AllocateType {firstOneLess, sameOrig, bothOneLess, firstTwoLess} // special functions are performed depending on GLMType.  Internal use

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

  public static GLMParameters copyGAMParams2GLMParams(GAMParameters parms, Frame trainData, Frame valid) {
    GLMParameters glmParam = new GLMParameters();
    Field[] field1 = GAMParameters.class.getDeclaredFields();
    setParamField(parms, glmParam, false, field1);
    Field[] field2 = Model.Parameters.class.getDeclaredFields();
    setParamField(parms, glmParam, true, field2);
    glmParam._train = trainData._key;
    glmParam._valid = valid==null?null:valid._key;
    return glmParam;
  }

  public static void setParamField(GAMParameters parms, GLMParameters glmParam, boolean superClassParams, Field[] gamFields) {
    // assign relevant GAMParameter fields to GLMParameter fields
    List<String> gamOnlyList = Arrays.asList(new String[]{"_num_knots", "_gam_columns", "_bs", "_scale", "_train", "_saveZMatrix",
            "_saveGamCols", "_savePenaltyMat"});
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
      } catch (IllegalAccessException e) { // suppress error printing, only cares about fields that are accessible
        ;
      } catch (NoSuchFieldException e) {  // only cares about fields that are there and don't care about missing ones
        ;
      }
    }
  }

  public static int colIndexFromColNames(String[] colnames, String oneName) {
    int len = colnames.length;
    for (int index = 0; index < len; index++)
      if (colnames[index].equals(oneName))
        return index;

    return -1;
  }

  public static void copyGLMCoeffs2GAMCoeffs(GAMModel model, GLMModel glm, DataInfo dinfo, GLMParameters.Family family,
                                             int gamNumStart, boolean standardized, int nclass) {
    int numCoeffPerClass = model._output._coefficient_names_no_centering.length;
    if (family.equals(GLMParameters.Family.multinomial) || family.equals(GLMParameters.Family.ordinal)) {
      double[][] model_beta_multinomial = glm._output.get_global_beta_multinomial();
      double[][] standardized_model_beta_multinomial = glm._output.getNormBetaMultinomial();
      model._output._model_beta_multinomial_no_centering = new double[nclass][];
      model._output._standardized_model_beta_multinomial_no_centering = new double[nclass][];
      for (int classInd = 0; classInd < nclass; classInd++) {
        model._output._model_beta_multinomial_no_centering[classInd] = convertCenterBeta2Beta(model._output._zTranspose,
                gamNumStart, model_beta_multinomial[classInd], numCoeffPerClass);
        model._output._standardized_model_beta_multinomial_no_centering[classInd] = convertCenterBeta2Beta(model._output._zTranspose,
                gamNumStart, standardized_model_beta_multinomial[classInd], numCoeffPerClass);
      }
    } else {  // other families
      model._output._model_beta_no_centering = convertCenterBeta2Beta(model._output._zTranspose, gamNumStart,
              glm.beta(), numCoeffPerClass);
      model._output._standardized_model_beta_no_centering = convertCenterBeta2Beta(model._output._zTranspose, gamNumStart,
              glm._output.getNormBeta(), numCoeffPerClass);
    }
  }

  
// This method carries out the evaluation of beta = Z betaCenter as explained in documentation 7.2
  public static double[] convertCenterBeta2Beta(double[][][] ztranspose, int gamNumStart, double[] centerBeta,
                                                int betaSize) {
    double[] originalBeta = new double[betaSize];
    if (ztranspose!=null) { // centering is performed
      int numGamCols = ztranspose.length;
      int gamColStart = gamNumStart;
      int origGamColStart = gamNumStart;
      System.arraycopy(centerBeta,0, originalBeta, 0, gamColStart);   // copy everything before gamCols
      for (int colInd=0; colInd < numGamCols; colInd++) {
        double[] tempCbeta = new double[ztranspose[colInd].length];
        System.arraycopy(centerBeta, gamColStart, tempCbeta, 0, tempCbeta.length);
        double[] tempBeta = ArrayUtils.multVecArr(tempCbeta, ztranspose[colInd]);
        System.arraycopy(tempBeta, 0, originalBeta, origGamColStart, tempBeta.length);
        gamColStart += tempCbeta.length;
        origGamColStart += tempBeta.length;
      }
      originalBeta[betaSize-1]=centerBeta[centerBeta.length-1];
    } else
      System.arraycopy(centerBeta, 0, originalBeta, 0, betaSize); // no change needed, just copy over

    return originalBeta;
  }

  public static int copyGLMCoeffNames2GAMCoeffNames(GAMModel model, GLMModel glm, DataInfo dinfo) {
      int numGamCols = model._gamColNamesNoCentering.length;
      String[] glmColNames = glm._output.coefficientNames();
      int lastGLMCoeffIndex = glmColNames.length-1;
      int lastGAMCoeffIndex = lastGLMCoeffIndex+numGamCols;
      int gamNumColStart = colIndexFromColNames(glmColNames, model._gamColNames[0][0]);
      int gamLengthCopied = gamNumColStart;
      System.arraycopy(glmColNames, 0, model._output._coefficient_names_no_centering, 0, gamLengthCopied); // copy coeff names before gam columns
      for (int gamColInd = 0; gamColInd < numGamCols; gamColInd++) {
        System.arraycopy(model._gamColNamesNoCentering[gamColInd], 0, model._output._coefficient_names_no_centering, gamLengthCopied,
                model._gamColNamesNoCentering[gamColInd].length);
        gamLengthCopied += model._gamColNamesNoCentering[gamColInd].length;
      }
      model._output._coefficient_names_no_centering[lastGAMCoeffIndex] = new String(glmColNames[lastGLMCoeffIndex]);
      return gamNumColStart;
  }

  public static Frame buildGamFrame(int numGamFrame, Key<Frame>[] gamFramesKey, Frame _train, String response_column) {
    Vec responseVec=null;
    if (response_column != null)
      responseVec = _train.remove(response_column);
    for (int frameInd = 0; frameInd < numGamFrame; frameInd++) {  // append the augmented columns to _train
      Frame gamFrame = gamFramesKey[frameInd].get();
      _train.add(gamFrame.names(), gamFrame.removeAll());
      Scope.track(gamFrame);
    }
    if (response_column!=null)
      _train.add(response_column, responseVec);
    return _train;
  }

  public static void addFrameKeys2Keep(List<Key<Vec>> keep, Key<Frame> ... keyNames) {
    for (Key<Frame> keyName:keyNames) {
      Frame loadingFrm = DKV.getGet(keyName);
      if (loadingFrm != null) for (Vec vec : loadingFrm.vecs()) keep.add(vec._key);
    }
  }
}
