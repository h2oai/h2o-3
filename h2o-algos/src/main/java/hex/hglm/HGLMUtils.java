package hex.hglm;

import Jama.Matrix;
import water.DKV;
import water.Key;
import water.fvec.Frame;
import water.util.ArrayUtils;

import java.util.Arrays;

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
  
  public static double calTauEvarEq17(double residualSquare, double tauEVar, double[][][] cjInv, double[][][] arjTArj, double oneOverN) {
    int numLevel2 = cjInv.length;
    int numRandCoef = arjTArj[0].length;
    double[][] cInvArjTArj = new double[numRandCoef][numRandCoef];
    for (int index=0; index<numLevel2; index++)
      matrixMult(cInvArjTArj, cjInv[index], arjTArj[index]);

    double sigmaTrace = tauEVar * trace(cInvArjTArj) ;
    return (residualSquare + sigmaTrace)*oneOverN;
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
}
