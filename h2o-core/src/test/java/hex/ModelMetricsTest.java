package hex;

import Jama.Matrix;
import org.junit.Test;
import water.TestUtil;

import static hex.ModelMetricsRegressionHGLM.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static water.TestUtil.genRandomMatrix;
import static water.TestUtil.genSymPsdMatrix;
import static water.util.ArrayUtils.expandMat;
import static water.util.ArrayUtils.flattenArray;

public class ModelMetricsTest {

  @Test
  public void testEmptyModelAUC() {
    ModelMetricsBinomial.MetricBuilderBinomial mbb =
            new ModelMetricsBinomial.MetricBuilderBinomial(new String[]{"yes", "yes!!"});
    ModelMetrics mm = mbb.makeModelMetrics(null, null, null, null);

    assertTrue(mm instanceof ModelMetricsBinomial);
    assertTrue(Double.isNaN(((ModelMetricsBinomial) mm).auc()));
    assertTrue(Double.isNaN(ModelMetrics.getMetricFromModelMetric(mm, "auc")));
  }
  
  // test loglikelihood calculation from ref [1] of the doc.
  @Test
  public void testCalLlg2() {
    checkCalLlg2(20, 6, 100, 1);
    checkCalLlg2(1, 2, 100, 1);
    checkCalLlg2(6, 15, 20, 1);
  }
  
  public void checkCalLlg2(int numRandomCoeff, int numLevel2, int nobs, int multiplier) {
    double varResidual = Math.abs(genRandomMatrix(1,1,123)[0][0]);
    double yMinsXFixSqaure = Math.abs(genRandomMatrix(1,1,123)[0][0]);
    double[][] tmat = genSymPsdMatrix(numRandomCoeff, 124, multiplier);
    double[][] zTTimesZ = genSymPsdMatrix(numRandomCoeff*numLevel2, 125, multiplier);
    double[][] yMinusXFixTimesZ = genRandomMatrix(numLevel2, numRandomCoeff, 126);
    double loglikelihood =  calHGLMllg2(nobs, tmat, varResidual, zTTimesZ, yMinsXFixSqaure, yMinusXFixTimesZ);
    // change yMinusXFixTimesZ to double[][] of size numLevel2*numRandomCoeff by 1
    double[] yMinusXFixTimesZT = flattenArray(yMinusXFixTimesZ);
    Matrix yMXF = new Matrix(new double[][] {yMinusXFixTimesZT}).transpose();
    Matrix zTz = new Matrix(zTTimesZ);
    // manually generating the log likelihood
    double oneOverVar = 1.0/varResidual;
    double oneOverVarSq = oneOverVar*oneOverVar;
    Matrix gMat = new Matrix(expandMat(tmat, numLevel2));
    double vDeterminant = (gMat.inverse().plus(zTz.times(oneOverVar)).det())*varResidual*(gMat.det());
    Matrix tMatPlusZTZ = gMat.inverse().plus(zTz.times(oneOverVar));
    Matrix tmatPlusZTZInv = tMatPlusZTZ.inverse();
    Matrix expVal = yMXF.transpose().times(tmatPlusZTZInv).times(yMXF).times(oneOverVarSq);
    double expValM = oneOverVar * yMinsXFixSqaure-expVal.getArray()[0][0];
    double logLikelihoodManual = -0.5*(nobs*Math.log(2*Math.PI)+Math.log(vDeterminant)+expValM);
    assertEquals(logLikelihoodManual, loglikelihood, 1e-6);
  }

  @Test
  public void testCalICC() {
    checkCalICC(10);
    checkCalICC(2);
    checkCalICC(29);
  }

  public void checkCalICC(int tmatSize) {
    double varE = genRandomMatrix(1,1,123)[0][0];
    double[][] mat = genRandomMatrix(tmatSize, tmatSize, 124);
    // generate symmetric matrix
    double[][] symMatArray = genSymPsdMatrix(tmatSize, 123, 10);
    double[] iccManual = new double[tmatSize];
    double denom = varE;
    for (int index=0; index<tmatSize; index++) {
      denom += symMatArray[index][index];
    }
    double oneOverDenom = 1.0/denom;
    for (int index=0; index<tmatSize; index++)
      iccManual[index] = symMatArray[index][index]*oneOverDenom;
    TestUtil.checkArrays(iccManual, calICC(symMatArray, varE), 1e-6);
  }
  
  @Test
  public void testCalVMatrix() {
    checkCalVMatrix(2, 1, 10);
    checkCalVMatrix(10, 1, 10);
    checkCalVMatrix(5, 10, 10);
    checkCalVMatrix(12, 8, 10);
    checkCalVMatrix(33, 33, 10);
  } 
  
  public void checkCalVMatrix(int numLevel2, int numRandomCoeff, int multiplier) {
    int qTimesJ = numLevel2*numRandomCoeff;
    double[][] gmat = genSymPsdMatrix(qTimesJ, 123, multiplier);
    double[][] zTTimesZ = genSymPsdMatrix(qTimesJ, 124, multiplier);
    double oneOVar = Math.abs(genRandomMatrix(1, 1, 123)[0][0]);
    Matrix gMatInv = new Matrix(gmat).inverse();
    Matrix zTimesZMat = new Matrix(zTTimesZ);
    double[][] vMat =  calInnverV(gmat, zTTimesZ, oneOVar);

    double[][] vMatManuel = gMatInv.plus(zTimesZMat.times(oneOVar)).getArray();
    
    TestUtil.checkDoubleArrays(vMat, vMatManuel, 1e-6);
  }
}
