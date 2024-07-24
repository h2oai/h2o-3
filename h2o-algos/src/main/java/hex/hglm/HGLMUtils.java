package hex.hglm;

import Jama.Matrix;
import hex.DataInfo;
import water.DKV;
import water.Key;
import water.fvec.Frame;
import water.util.ArrayUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static hex.glm.GLMModel.GLMParameters.MissingValuesHandling.*;
import static water.util.ArrayUtils.*;

public class HGLMUtils {
  // the doc = document attached to https://github.com/h2oai/h2o-3/issues/8487, title HGLM_H2O_Implementation.pdf
  // I will be referring to the doc and different parts of it to explain my implementation.
  public static double[][][] copy3DArray(double[][][] src) {
    int firstInd = src.length;
    double[][][] dest = new double[firstInd][][];
    for (int index=0; index<firstInd; index++)
      dest[index] = copy2DArray(src[index]);
    return dest;
  }
  
  public static void grabInitValuesFromFrame(Key frameKey, double[][] ubeta) {
    int numRow = ubeta.length;
    int numCol = ubeta[0].length;
    Frame randomEffects = DKV.getGet(frameKey);
    if (randomEffects.numRows() != numRow || randomEffects.numCols() != numCol)
      throw new IllegalArgumentException("initial_random_effects: Initial random coefficients must be" +
              " a double[][] array of size "+numRow+" rows and "+numCol+" columns" +
              " but is not.");
    final ArrayUtils.FrameToArray f2a = new ArrayUtils.FrameToArray(0, numCol-1,
            numRow, ubeta);
    f2a.doAll(randomEffects).getArray();
  }
  
  public static void setDiagValues(double[][] tMat, double tauUVar) {
    int matSize = tMat.length;
    for (int index=0; index<matSize; index++) {
      assert matSize == tMat[index].length;
      tMat[index][index] = tauUVar;
    }
  }
  
  public static boolean equal2DArrays(double[][] arr1, double[][] arr2, double threshold) {
    int dim1 = arr1.length;
    int dim2;
    assert dim1 == arr2.length : "arrays first dimension are different.";
    for (int ind1 = 0; ind1 < dim1; ind1++) {
      dim2 = arr1[ind1].length;
      assert dim2 == arr2[ind1].length : "arrays second dimension are different.";
      for (int ind2 = 0; ind2 < dim2; ind2++)
        if (Math.abs(arr1[ind1][ind2]-arr2[ind1][ind2]) > threshold)
          return false;
    }
     return true;
  }
  
  public static double[][] generateTInverse(double[][] tMat) {
    Matrix tMatrix = new Matrix(tMat);
    return tMatrix.inverse().getArray();
  }
  public static double[][][] generateCJInverse(double[][][] arjTArj, double tauEVar, double[][] tMatInv) {
    int numLevel2Unit = arjTArj.length;
    double[][][] cJInverse = new double[numLevel2Unit][][];
    int arjTArjSize = arjTArj[0].length;
    double[][] tempResult = new double[arjTArjSize][arjTArjSize];
    double[][] sigmaTimestMatInv = new double[arjTArjSize][arjTArjSize];
    mult(tMatInv, sigmaTimestMatInv, tauEVar);
    for (int index = 0; index < numLevel2Unit; index++) {
      add(tempResult, arjTArj[index], sigmaTimestMatInv);
      cJInverse[index] = new Matrix(tempResult).inverse().getArray();
    }
    return cJInverse;
  }

  /**
   * Note that the term ArjTYj and ArjTAfj are fixed and won't change.  They are stored in engineTask
   */
  public static double[][] estimateNewRandomEffects(double[][][] cjInv, double[][] ArjTYj, double[][][] ArjTAfj, double[] beta) {
    int numLevel2Unit = cjInv.length;
    int numRandCoef = cjInv[0].length;
    double[][] ubeta = new double[numLevel2Unit][numRandCoef];
    double[] arjTafjbeta = new double[numRandCoef];
    double[] result = new double[numRandCoef];
    for (int index=0; index < numLevel2Unit; index++) {
      matrixVectorMult(arjTafjbeta, ArjTAfj[index], beta);  // ArjTAfj*betaUtil
      minus(result, ArjTYj[index], arjTafjbeta);  // (ArjTYj-ArjTAfj*beta)
      matrixVectorMult(ubeta[index], cjInv[index], result);
      Arrays.fill(arjTafjbeta, 0.0);
    }
    return ubeta;
  }

  public static double[] estimateFixedCoeff(double[][] AfjTAfjSumInv, double[] AfjTYjSum, double[][][] AfjTArj, double[][] ubeta) {
    int numLevel2 = ubeta.length;
    int numFixedCoeffs = AfjTAfjSumInv.length;
    double[] betaFixed = new double[numFixedCoeffs];
    double[] AfjTArjTimesBrj = new double[numFixedCoeffs];
    for (int index=0; index<numLevel2; index++) {
      matrixVectorMult(AfjTArjTimesBrj, AfjTArj[index], ubeta[index]);
    }
    minus(AfjTArjTimesBrj, AfjTYjSum, AfjTArjTimesBrj);
    matrixVectorMult(betaFixed, AfjTAfjSumInv, AfjTArjTimesBrj);
    return betaFixed;
  }
  
  public static double[][] estimateNewtMat(double[][] ubeta, double tauEVar, double[][][] cJInv, double oneOverNumLevel2) {
    int numLevel2 = ubeta.length;
    int numRandCoef = ubeta[0].length;
    double[][] tmat = new double[numRandCoef][numRandCoef];
    double[][] tempCInvj = new double[numRandCoef][numRandCoef];
    
    for (int index=0; index<numLevel2; index++) {
      outputProductSymCum(tmat, ubeta[index]);
      add(tempCInvj, cJInv[index]);
    }
    mult(tempCInvj, tauEVar);
    add(tmat, tempCInvj);
    mult(tmat, oneOverNumLevel2);
    return tmat;
  }
  
  public static double calTauEvar(double residualSquare, double tauEVar, double[][][] cjInv, double[][][] arjTArj, double oneOverN) {
    int numLevel2 = cjInv.length;
    int numRandCoef = arjTArj[0].length;
    double[][] cInvArjTArj = new double[numRandCoef][numRandCoef];
    for (int index=0; index<numLevel2; index++)
      matrixMult(cInvArjTArj, cjInv[index], arjTArj[index]);

    double sigmaTrace = tauEVar * trace(cInvArjTArj) ;
    return (residualSquare + sigmaTrace)*oneOverN;
  }

  public static double[] denormalizedOneBeta(double[] beta, String[] coeffNames, String[] colNames,
                                             Frame train, boolean interceptPresent) {
    int numRandomCoeff = beta.length;
    Map<String, Double> coefMean = new HashMap<>();
    Map<String, Double> coefStd = new HashMap<>();
    List<String> randomColList = Arrays.stream(colNames).collect(Collectors.toList());
    genMeanStd(coeffNames, randomColList, train, coefMean, coefStd);
    int interceptIndex = interceptPresent ? numRandomCoeff - 1 : numRandomCoeff;
    double[] denormalizedUBeta = new double[interceptIndex + 1];
    if (interceptPresent)
      denormalizedUBeta[interceptIndex] = beta[interceptIndex];

    String coefName;
    for (int coefInd = 0; coefInd < numRandomCoeff; coefInd++) {
      coefName = coeffNames[coefInd];
      if (randomColList.contains(coefName)) { // pick out the numerical columns
          denormalizedUBeta[coefInd] = beta[coefInd] / coefStd.get(coefName);
          denormalizedUBeta[interceptIndex] -= beta[coefInd] * coefMean.get(coefName) / coefStd.get(coefName);
      } else if (coefName != "intercept") {
        denormalizedUBeta[coefInd] = beta[coefInd];
      }
    }
    return denormalizedUBeta;
  }
  
  public static double[][] denormalizedUBeta(double[][] ubeta, String[] randomCoeffNames, String[] randomColNames,
                                             Frame train, boolean randomIntercept) {
    int numLevel2 = ubeta.length;
    double[][] denormalizedBeta = new double[numLevel2][];
    boolean onlyEnumRandomCols = randomColAllEnum(train, randomColNames);
    for (int index=0; index<numLevel2; index++) {
      if (onlyEnumRandomCols)
        denormalizedBeta[index] = ubeta[index].clone();
      else
        denormalizedBeta[index] = denormalizedOneBeta(ubeta[index], randomCoeffNames, randomColNames, train,
              randomIntercept);
    }
    return denormalizedBeta;
  }
  
  public static double[] normalizedOneBeta(double[] beta, String[] coeffNames, String[] columnNames,
                                           Frame train, boolean interceptPresent) {
    int numCoeff = beta.length;
    int interceptIndex = interceptPresent ? numCoeff-1 : numCoeff;
    double[] normalizedBeta = new double[interceptIndex+1];
    List<String> colNamesList = Arrays.stream(columnNames).collect(Collectors.toList());
    Map<String, Double> coefMean = new HashMap<>();
    Map<String, Double> coefStd = new HashMap<>();
    genMeanStd(coeffNames, colNamesList, train, coefMean, coefStd);
    
    if (interceptPresent)
      normalizedBeta[interceptIndex] = beta[interceptIndex];

    String coefName;
    for (int coefInd=0; coefInd < numCoeff; coefInd++) {
      coefName = coeffNames[coefInd];
      if (colNamesList.contains(coefName)) {  // pick out numerical columns
        normalizedBeta[coefInd] = beta[coefInd] * coefStd.get(coefName);
        normalizedBeta[interceptIndex] += normalizedBeta[coefInd] * coefMean.get(coefName)/coefStd.get(coefName);
      } else if (coefName != "intercept"){    // no change to enum columns
        normalizedBeta[coefInd] = beta[coefInd];
      }
    }
    return normalizedBeta;
  }

  /**
   * Normalize ubeta, intercept is always the last one
   */
  public static double[][] normalizedUBeta(double[][] ubeta, String[] randomCoeffNames, String[] randomColNames,
                                           Frame train, boolean randomIntercept) {
    int numLevel2 = ubeta.length;
    double[][] normalizedUBeta = new double[numLevel2][];
    boolean onlyEnumRandomCols = randomColAllEnum(train, randomColNames);
    for (int index=0; index<numLevel2; index++) {
      if (onlyEnumRandomCols)
        normalizedUBeta[index] = ubeta[index].clone();
      else
        normalizedUBeta[index] = normalizedOneBeta(ubeta[index], randomCoeffNames, randomColNames, train, randomIntercept);
    }
    return normalizedUBeta;
  }
  
  public static void genMeanStd(String[] randomCoeffNames, List<String> randomColNames, Frame train, 
                                Map<String, Double> coefMean, Map<String, Double> coefSTD) {
    int numCoeff = randomCoeffNames.length;
    String coefName;
    double colMean;
    double colStd;
    for (int index=0; index<numCoeff; index++) {
      coefName = randomCoeffNames[index];
      if (randomColNames.contains(coefName)) {
        colMean = train.vec(coefName).mean();
        colStd = train.vec(coefName).sigma();
        coefMean.putIfAbsent(coefName, colMean);
        coefSTD.putIfAbsent(coefName, colStd);
      }
    }
  }
  
  public static double[][] fillZTTimesZ(double[][][] arjTArj) {
    int numLevel2 = arjTArj.length;
    int numRandCoef = arjTArj[0].length;
    int zSize = numLevel2 * numRandCoef;
    double[][] zTTimesZ = new double[zSize][zSize];
    int startRowIndex;
    for (int leveIndex=0; leveIndex<numLevel2; leveIndex++) {
      startRowIndex = leveIndex*numRandCoef;
      for (int rInd=0; rInd<numRandCoef; rInd++) {
        System.arraycopy(arjTArj[leveIndex][rInd], 0, zTTimesZ[startRowIndex+rInd], startRowIndex, numRandCoef);
      }
    }
    return zTTimesZ;
  }
  
  public static boolean checkPositiveG(int numLevel2Units, double[][] tMat) {
    double[][] gMat = expandMat(tMat, numLevel2Units);
    return (new Matrix(gMat).det()) >= 0;
  }
  
  public static void generateNonStandardizeZTZArjTArs(HGLMModel.HGLMParameters parms, HGLMModel model) {
    if (parms._standardize) {
      boolean orignalRandomIntercept = parms._random_intercept;
      parms._random_intercept = parms._random_intercept || !randomColAllEnum(parms.train(), parms._random_columns);
      List<String> colNames = Arrays.asList(parms.train().names());
      boolean hasWeights = model._parms._weights_column != null && colNames.contains(model._parms._weights_column);
      boolean hasOffsets = model._parms._offset_column != null && colNames.contains(model._parms._offset_column);
      DataInfo dinfo = new DataInfo(parms.train().clone(), null, 1, parms._use_all_factor_levels,
              DataInfo.TransformType.NONE, DataInfo.TransformType.NONE,
              parms.missingValuesHandling() == Skip, parms.missingValuesHandling() == MeanImputation
              || parms.missingValuesHandling() == PlugValues, parms.makeImputer(), false, hasWeights,
              hasOffsets, false, null);
      HGLMTask.ComputationEngineTask engineTask = new HGLMTask.ComputationEngineTask(null, parms, dinfo);
      engineTask.doAll(dinfo._adaptedFrame);
      model._output._arjtarj_score = engineTask._ArjTArj;
     // model._output._zttimesz_score = engineTask._zTTimesZ;
      parms._random_intercept = orignalRandomIntercept;
    } else {
      model._output._arjtarj_score = model._output._arjtarj;
     // model._output._zttimesz_score = model._output._zttimesz;
    }
  }
  
  public static double[][] generateNewTmat(double[][] ubeta) {
    int numIndex2 = ubeta.length;
    double oneOverJ = 1.0/numIndex2;
    int numRandCoeff = ubeta[0].length;
    double[][] newTmat = new double[numRandCoeff][numRandCoeff];
    for (int index=0; index<numIndex2; index++) {
      outerProductCum(newTmat, ubeta[index], ubeta[index]);
    }
    mult(newTmat, oneOverJ);
    return newTmat;
  }
  
  public static boolean randomColAllEnum(Frame train, String[] randomColumns) {
    int  numRandCols = randomColumns.length;
    return Arrays.stream(randomColumns).filter(x -> train.vec(x).isCategorical()).count() == numRandCols;
  }
}
