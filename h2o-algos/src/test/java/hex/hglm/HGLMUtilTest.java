package hex.hglm;

import Jama.Matrix;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.TestUtil;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static hex.hglm.HGLMTask.ComputationEngineTask.sumAfjAfjAfjTYj;
import static hex.hglm.HGLMUtils.*;
import static hex.hglm.MetricBuilderHGLM.calHGLMLlg;
import static org.junit.Assert.assertEquals;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class HGLMUtilTest extends TestUtil {
  
  @Test
  public void testGenerateCJInverse() {
    testGenCJInv(5, 10);
    testGenCJInv(10, 5);
    testGenCJInv(8, 9);
  }
  
  public void testGenCJInv(int level2Num, int randCoeffSize) {
    double[][][] arjTArj = new double[level2Num][][];
    double tauEVar = genRandomMatrix(1, 1,123)[0][0];
    double[][] tMatInv = makeSymMatrix(genRandomMatrix(randCoeffSize, randCoeffSize, 134));
    Matrix matTMatInv = new Matrix(tMatInv).times(tauEVar);
    double[][][] matCJInv = new double[level2Num][][];
    
    for (int index=0; index< level2Num; index++) {
      arjTArj[index] = makeSymMatrix(genRandomMatrix(randCoeffSize, randCoeffSize, 145+index));
      matCJInv[index] = new Matrix(arjTArj[index]).plus(matTMatInv).inverse().getArray();
    }
    double[][][] allCJInv = generateCJInverse(arjTArj, tauEVar, tMatInv);
    check3DArrays(matCJInv, allCJInv, 1e-6);
  }
  
  @Test
  public void testEstimateNewRandomEffects() {
    testEstRandomEff(5, 4, 10);
    testEstRandomEff(10, 1, 10);
    testEstRandomEff(8, 4, 30);
  }
  
  public void testEstRandomEff(int level2Num, int randCoeffSize, int fixedCoefSize) {
    double[][][] cjInv = new double[level2Num][][];
    double[][] ArjTYj = genRandomMatrix(level2Num, randCoeffSize, 124);
    double[][][] ArjTAfj = new double[level2Num][][];
    double[] beta = genRandomArray(fixedCoefSize, 123);
    Matrix matBeta = new Matrix(new double[][]{beta}).transpose();
    double[][] matUBeta = new double[level2Num][];
    
    for (int index=0; index<level2Num; index++) {
      cjInv[index] = genRandomMatrix(randCoeffSize, randCoeffSize, 125+index);
      ArjTAfj[index] = genRandomMatrix(randCoeffSize, fixedCoefSize, 150+index);
      Matrix part1 = new Matrix(new double[][]{ArjTYj[index]}).transpose().minus(new Matrix(ArjTAfj[index]).times(matBeta));
      Matrix matCjInv = new Matrix(cjInv[index]);
      matUBeta[index] = matCjInv.times(part1).transpose().getArray()[0];
    }
    double[][] ubeta = estimateNewRandomEffects(cjInv, ArjTYj, ArjTAfj, beta);
    TestUtil.checkDoubleArrays(ubeta, matUBeta, 1e-6);
  }
  
  @Test
  public void testEstimateFixedCoeff() {
    testEstFixedCoeffs(8, 1, 20);
    testEstFixedCoeffs(5, 6, 10);
    testEstFixedCoeffs(20, 8, 8);
  }
  
  public void testEstFixedCoeffs(int level2Num, int numRandNum, int numFixedCoeffs) {
    double[][] ubeta = genRandomMatrix(level2Num, numRandNum, 123);
    double[][][] AfjTArj = new double[level2Num][][];
    double[] AfjTYjSum = genRandomArray(numFixedCoeffs, 124);
    double[][] AfjTAfjSumInv = genRandomMatrix(numFixedCoeffs, numFixedCoeffs, 125);
    Matrix part = new Matrix(new double[numFixedCoeffs][1]);
    Matrix matAfjTAfjSumInv = new Matrix(AfjTAfjSumInv);
    
    for (int index=0; index<level2Num; index++) {
      AfjTArj[index] = genRandomMatrix(numFixedCoeffs, numRandNum, 150+index);
      part = part.minus(new Matrix(AfjTArj[index]).times(new Matrix(new double[][]{ubeta[index]}).transpose()));
    }
    part = part.plus(new Matrix(new double[][]{AfjTYjSum}).transpose());
    double[] matBeta = matAfjTAfjSumInv.times(part).transpose().getArray()[0];
    double[] beta = estimateFixedCoeff(AfjTAfjSumInv, AfjTYjSum, AfjTArj, ubeta);
    
    TestUtil.checkArrays(beta, matBeta, 1e-6);
  }
  
  @Test
  public void testEstimateNewtMat() {
    testNewTMat(5, 1);
    testNewTMat(5, 10);
    testNewTMat(10, 4);
    testNewTMat(1, 1);
  }
  
  public void testNewTMat(int numLevel2, int numRandCoeff) {
    double[][][] cjInv = new double[numLevel2][][];
    double[][] randCoeff = genRandomMatrix(numLevel2, numRandCoeff, 134);
    double tauEVar = genRandomMatrix(1, 1, 137)[0][0];
    Matrix oneSum = new Matrix(new double[numRandCoeff][numRandCoeff]);
    Matrix twoSum = new Matrix(new double[numRandCoeff][numRandCoeff]);
    
    for (int index=0; index<numLevel2; index++) {
      cjInv[index] = genRandomMatrix(numRandCoeff, numRandCoeff, 200 + index);
      Matrix oneRandomCoeff = new Matrix(new double[][]{randCoeff[index]}).transpose();
      Matrix outerProd = oneRandomCoeff.times(oneRandomCoeff.transpose());
      oneSum = oneSum.plus(outerProd);
      twoSum = twoSum.plus(new Matrix(cjInv[index]).times(tauEVar));
    }
    double[][] matResult = oneSum.plus(twoSum).times(1.0/numLevel2).getArray();
    double[][] result = estimateNewtMat(randCoeff, tauEVar, cjInv, 1.0/numLevel2);
    
    TestUtil.checkDoubleArrays(matResult, result, 1e-6);
  }
  
  public static double[][] makeSymMatrix(double[][] mat) {
    return (new Matrix(mat).plus(new Matrix(mat).transpose()).times(0.5)).getArray();
  }
  
  @Test
  public void testCalTauEVar() {
    checkCalTauEvar(3, 3);
    checkCalTauEvar(10, 20);
    checkCalTauEvar(20, 5);
    checkCalTauEvar(15, 15);
    
  }
  public void checkCalTauEvar(int numLevel2, int numRandomCoeffs) {
    double[][][] cjInv = new double[numLevel2][][];
    double[][][] arjTArj = new double[numLevel2][][];
    double residualSquare = genRandomMatrix(1,1,123)[0][0];
    double tauEVar = genRandomMatrix(1,1,124)[0][0];
    double oneOverN = genRandomMatrix(1,1,125)[0][0];
    double sigmaTrace = 0.0;
    
    // generate random matrices and generate results using Java Matrix boolbox
    for (int index=0; index<numLevel2; index++) {
      cjInv[index] = genRandomMatrix(numRandomCoeffs, numRandomCoeffs, 130+index);
      arjTArj[index] = genRandomMatrix(numRandomCoeffs, numRandomCoeffs, 13000+index);
      
      Matrix cJINV = new Matrix(cjInv[index]);
      Matrix arJTARJ = new Matrix(arjTArj[index]);
      sigmaTrace += cJINV.times(arJTARJ).trace();
    }
    double newTauEVarMat = (sigmaTrace*tauEVar+residualSquare)*oneOverN;
    double newTauEVar =  calTauEvarEq17(residualSquare, tauEVar, cjInv, arjTArj, oneOverN);
    assertEquals(newTauEVar, newTauEVarMat, 1e-6);
  }
  
  @Test
  public void testGenerateNewTmat() {
    checkGenerateNewTmat(2, 1);
    checkGenerateNewTmat(5, 5);
    checkGenerateNewTmat(10, 8);
    checkGenerateNewTmat(8, 15);
  }
  
  public void checkGenerateNewTmat(int numLevel2, int numRandCoeff) {
    double[][] ubeta = genRandomMatrix(numLevel2, numRandCoeff, 123);
    double[][] tmat = generateNewTmat(ubeta);
    double oneOverJ = 1.0/numLevel2;
    Matrix tmatManual = new Matrix(new double[numRandCoeff][numRandCoeff]);
    for (int index=0; index < numLevel2; index++) {
      Matrix oneVect = new Matrix(new double[][]{ubeta[index]});
      tmatManual = tmatManual.plus(oneVect.transpose().times(oneVect));
    }
    double[][] tmatM = tmatManual.times(oneOverJ).getArray();
    TestUtil.checkDoubleArrays(tmatM, tmat, 1e-6);     
  }
  
  @Test
  public void testCumSum() {
    checkCumSum(10, 5);
    checkCumSum(3, 1);
    checkCumSum(20, 25);
  }
  
  public void checkCumSum(int numLevel2, int numFixedLength) {
    double[][][] AfjTAfj = new double[numLevel2][][];
    double[][] AfjTYj = genRandomMatrix(numLevel2, numFixedLength, 123);
    double[][] sumAfjTAfj = new double[numFixedLength][numFixedLength];
    double[] sumAfjTYj = new double[numFixedLength];
    Matrix sumAfjTAfjMat = new Matrix(new double[numFixedLength][numFixedLength]);
    Matrix sumAfjTYjMat = new Matrix(new double[numFixedLength][1]);
    
    for (int index=0; index<numLevel2; index++) {
      AfjTAfj[index] = genRandomMatrix(numFixedLength, numFixedLength, 1800+index);
      sumAfjTAfjMat = sumAfjTAfjMat.plus(new Matrix(AfjTAfj[index]));
      sumAfjTYjMat = sumAfjTYjMat.plus(new Matrix(new double[][] {AfjTYj[index]}).transpose());
    }
    sumAfjAfjAfjTYj(AfjTAfj, AfjTYj, sumAfjTAfj, sumAfjTYj);
    TestUtil.checkDoubleArrays(sumAfjTAfj, sumAfjTAfjMat.getArray(), 1e-12);
    TestUtil.checkArrays(sumAfjTYj, sumAfjTYjMat.transpose().getArray()[0], 1e-12);
  }

  @Test
  public void testCalLlg() {
    checkCalLlg(1, 2, 10, 1);
    checkCalLlg(10, 20, 100, 3);
    checkCalLlg(18, 8, 20, 1);
    checkCalLlg(8, 8, 10, 1);
  }

  public void checkCalLlg(int numRandomCoeff, int numLevel2, int nobs, int multiplier) {
    double varResidual = Math.abs(genRandomMatrix(1, 1, 123)[0][0]);
    double yMinsXFixSquare = Math.abs(genRandomMatrix(1,1, 124)[0][0]);
    double[][] tmat = genSymPsdMatrix(numRandomCoeff, 124, multiplier);
    Matrix tmatInv = new Matrix(tmat).inverse();
    double oneOVar = 1.0/varResidual;
    double oneOVarSq = oneOVar*oneOVar;
    double llgManual = nobs*Math.log(2*Math.PI)+oneOVar*yMinsXFixSquare;
    double[][] yMinusXFixTimesZ = genRandomMatrix(numLevel2, numRandomCoeff, 126);
    double[][][] zjTimesZj = new double[numLevel2][][];

    for (int ind2=0; ind2<numLevel2; ind2++) {
      zjTimesZj[ind2] = genSymPsdMatrix(numRandomCoeff, 125+ind2, multiplier);
      Matrix tInvPZjZ = tmatInv.plus(new Matrix(zjTimesZj[ind2]).times(oneOVar));
      llgManual += Math.log(varResidual * tInvPZjZ.det() * new Matrix(tmat).det());
      Matrix yMinusXFTTZj = new Matrix(new double[][]{yMinusXFixTimesZ[ind2]});
      llgManual -= yMinusXFTTZj.times(tInvPZjZ.inverse()).times(yMinusXFTTZj.transpose()).getArray()[0][0] * oneOVarSq;
    }
    llgManual *= -0.5;
    double llg = calHGLMLlg(nobs, tmat, varResidual, zjTimesZj, yMinsXFixSquare, yMinusXFixTimesZ);
    assertEquals(llg, llgManual, 1e-6);
  }
}
