package hex;

import Jama.Matrix;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.util.ArrayUtils;

import java.util.Arrays;

import static water.util.ArrayUtils.*;

public class ModelMetricsRegressionHGLM extends ModelMetricsSupervised {
  // the doc = document attached to https://github.com/h2oai/h2o-3/issues/8487, title HGLM_H2O_Implementation.pdf
  // I will be referring to the doc and different parts of it to explain my implementation.
  public static final double LOG_2PI = Math.log(2*Math.PI);
  public final double[] _beta;  // fixed coefficients
  public final double[][] _ubeta; // random coefficients
  public final double[] _icc;
  public final int _iterations;
  public final double[][] _tmat;
  public final double _var_residual;  // variance of residual error
  public final double _log_likelihood;  // llg from reference [2] of the doc
  public final double _mse_fixed; // mse of with fixed effect only

  public ModelMetricsRegressionHGLM(Model model, Frame frame, long nobs, String[] domain, double sigma,
                                    CustomMetric customMetric, int iter, double[] beta, double[][] ubeta, 
                                    double[][] tmat, double varResidual, double mse, double mse_fixed, 
                                    double[][] yMinusXFixTimesZ, double[][][] ajTaj) {
    super(model, frame, nobs, mse, domain, sigma, customMetric);
    _beta = beta;
    _ubeta = ubeta;
    _iterations = iter;
    _tmat = tmat;
    _var_residual = varResidual;
    _icc = calICC(tmat, varResidual);
    _log_likelihood = calHGLMllg(nobs, tmat, varResidual, ajTaj, mse_fixed*nobs, yMinusXFixTimesZ);
    _mse_fixed = mse_fixed;
  }

  /**
   *
   * This method calculates the log-likelihood as described in section II.V of the doc.
   */
  public static double calHGLMllg(long nobs, double[][] tmat, double varResidual, double[][][] zjTTimesZj,
                                   double yMinsXFixSquared, double[][] yMinusXFixTimesZ) {
      int numLevel2 = zjTTimesZj.length;
      double[][] tmatInv = new Matrix(tmat).inverse().getArray();
      double tmatDeterminant = new Matrix(tmat).det();
      double oneOVar = 1.0 / varResidual;
      double oneOVarSq = oneOVar * oneOVar;
      double llg = nobs * LOG_2PI + oneOVar * yMinsXFixSquared;
      double[][] invTPlusZjTZ;
      Matrix yMinusXjFixed;
      Matrix yjMinusXjFixed;
      for (int ind2 = 0; ind2 < numLevel2; ind2++) {
        invTPlusZjTZ = calInvTPZjTZ(tmatInv, zjTTimesZj[ind2], oneOVar);
        llg += Math.log(varResidual * new Matrix(invTPlusZjTZ).det() * tmatDeterminant);
        yMinusXjFixed = new Matrix(new double[][]{yMinusXFixTimesZ[ind2]});
        yjMinusXjFixed = yMinusXjFixed.times(new Matrix(invTPlusZjTZ).inverse().times(yMinusXjFixed.transpose()));
        llg -= oneOVarSq * yjMinusXjFixed.getArray()[0][0];
      }
      return -0.5 * llg;
  }
  
  public static double[][] calInvTPZjTZ(double[][] tmatInv, double[][] zjTTimesZj, double oneOVar) {
   return new Matrix(tmatInv).plus(new Matrix(zjTTimesZj).times(oneOVar)).getArray();
  }

  /***
   *
   * This method calculates the log-likelihood as described in section II.VI of the doc.  Please keep this method
   * even though nobody is calling it.
   */
  public static double calHGLMllg2(long nobs, double[][] tmat, double varResidual, double[][] zTTimesZ,
                                   double yMinsXFixSqure, double[][] yMinusXFixTimesZ) {
    double llg = nobs*LOG_2PI;
    double oneOVar = 1.0/varResidual;
    double oneOVarSq = oneOVar*oneOVar;
    double[][] gMat = expandMat(tmat, yMinusXFixTimesZ.length);
    double[][] tInvPlusZTT = calInnverV(gMat, zTTimesZ, oneOVar);
    llg += Math.log(varResidual * new Matrix(tInvPlusZTT).det() * new Matrix(gMat).det());
    double[] yMinusXFixTimesZVec = flattenArray(yMinusXFixTimesZ);
    Matrix yMinusXFixTimesZMat = new Matrix(new double[][] {yMinusXFixTimesZVec}).transpose();
    llg += oneOVar*yMinsXFixSqure - 
            yMinusXFixTimesZMat.transpose().times(new Matrix(tInvPlusZTT).inverse()).times(yMinusXFixTimesZMat).times(oneOVarSq).getArray()[0][0];
    return -0.5*llg;
  }

  /**
   * See the doc section II.V, calculates G inverse + transpose(Z)*Z/var_e.
   * @return
   */
  public static double[][] calInnverV(double[][] gmat, double[][] zTTimesZ, double oneOVar) {
    try {
      double[][] gmatInv = new Matrix(gmat).inverse().getArray();
      double[][] tempzTTimesZ = copy2DArray(zTTimesZ);
      ArrayUtils.mult(tempzTTimesZ, oneOVar);
      ArrayUtils.add(gmatInv, tempzTTimesZ);
      return gmatInv;
    } catch(Exception ex) {
      throw new RuntimeException("Tmat matrix is singular.");
    }
  }

  public static ModelMetricsRegressionHGLM getFromDKV(Model model, Frame frame) {
    ModelMetrics mm = ModelMetrics.getFromDKV(model, frame);
    if (!(mm instanceof ModelMetricsRegressionHGLM))
      throw new H2OIllegalArgumentException("Expected to find a HGLM ModelMetrics for model: " + model._key.toString()
              + " and frame: " + frame._key.toString(), "Expected to find a ModelMetricsHGLM for model: " +
              model._key.toString() + " and frame: " + frame._key.toString() + " but found a: " + (mm == null ? null : mm.getClass()));
    return (ModelMetricsRegressionHGLM) mm;
  }

  public static double[] calICC(double[][] tmat, double varResidual) {
    int numLevel2 = tmat.length;
    double[] icc = new double[numLevel2];
    double denom = varResidual;
    denom += new Matrix(tmat).trace();  // sum of diagonal
    double oOverDenom = 1.0/denom;
    for (int index=0; index<numLevel2; index++)
      icc[index] = tmat[index][index]*oOverDenom;
    return icc;
  }
  
  public double llg() {
    return _log_likelihood;
  }
  
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(super.toString());
    sb.append(" mean square error with fixed predictor coefficients: "+_mse_fixed);
    int numLevel2 = _ubeta.length;
    for (int index=0; index<numLevel2; index++)
      sb.append(" standard error of random effects for level 2 index " + index + ": "+_tmat[index][index]);
    sb.append(" standard error of residual error: "+_var_residual);
    sb.append(" ICC: "+Arrays.toString(_icc));
    sb.append(" loglikelihood: "+_log_likelihood);
    sb.append(" iterations taken to build model: " + _iterations);
    sb.append(" coefficients for fixed effect: "+Arrays.toString(_beta));
    for (int index=0; index<numLevel2; index++)
      sb.append(" coefficients for random effect for level 2 index: "+index+": "+Arrays.toString(_ubeta[index]));
    return sb.toString();
  }
}
