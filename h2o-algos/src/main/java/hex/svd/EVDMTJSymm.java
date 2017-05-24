package hex.svd;

import hex.util.LinearAlgebraUtils;
import no.uib.cipr.matrix.*;

/**
 * @author mathemage <ha@h2o.ai>
 * created on 1.5.17
 */
public class EVDMTJSymm implements SVDInterface {
  private UpperSymmDenseMatrix symmGramMatrix;
  private no.uib.cipr.matrix.SymmDenseEVD symmDenseEVD;
  private double[][] eigenvectors;

  EVDMTJSymm(double[][] gramMatrix) {
    this.symmGramMatrix = new UpperSymmDenseMatrix(new DenseMatrix(gramMatrix));
    runEVD();
  }

  @Override
  public double[] getVariances() {
    return symmDenseEVD.getEigenvalues();
  }

  @Override
  public double[][] getPrincipalComponents() {
    return eigenvectors;
  }

  private void runEVD() {
    int gramDimension = symmGramMatrix.numRows();
    try {
      symmDenseEVD = no.uib.cipr.matrix.SymmDenseEVD.factorize(this.symmGramMatrix);
    } catch (NotConvergedException e) {
      throw new RuntimeException(e);
    }
    double[] Vt_1D = symmDenseEVD.getEigenvectors().getData();
    this.eigenvectors = LinearAlgebraUtils.reshape1DArray(Vt_1D, gramDimension, gramDimension);
  }
}
