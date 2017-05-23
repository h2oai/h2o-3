package hex.svd;

import hex.util.EigenPair;
import hex.util.LinearAlgebraUtils;
import no.uib.cipr.matrix.DenseMatrix;
import no.uib.cipr.matrix.NotConvergedException;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.reverse;
import static java.util.Collections.sort;

/**
 * @author mathemage <ha@h2o.ai>
 *         created on 1.5.17
 */
public class EVDMTJDenseMatrix implements SVDInterface {
  private static DenseMatrix gramMatrix;
  private static no.uib.cipr.matrix.EVD evd;
  private static double[] eigenvalues;
  private static double[][] eigenvectors;

  EVDMTJDenseMatrix(double[][] gramMatrix) {
    assert LinearAlgebraUtils.isSymmetric(gramMatrix);
    EVDMTJDenseMatrix.gramMatrix = new DenseMatrix(gramMatrix);
    runEVD();
  }

  private static void runEVD() {
    int gramDimension = gramMatrix.numRows();
    try {
      // Note: gramMatrix will be overwritten after this
      evd = no.uib.cipr.matrix.EVD.factorize(gramMatrix);
    } catch (NotConvergedException e) {
      throw new RuntimeException(e);
    }
    eigenvalues = evd.getRealEigenvalues();
    DenseMatrix rightEigenvectors = evd.getRightEigenvectors();
    eigenvectors = LinearAlgebraUtils.reshape1DArray(rightEigenvectors.getData(), gramDimension, gramDimension);

    List<EigenPair> eigenPairs = new ArrayList<>();
    for (int i = 0; i < gramDimension; i++) {
      eigenPairs.add(new EigenPair(eigenvalues[i], eigenvectors[i]));
    }
    sort(eigenPairs);
    reverse(eigenPairs);
    int index = 0;
    for (EigenPair eigenPair : eigenPairs) {
      eigenvectors[index] = eigenPair.eigenvectors;
      eigenvalues[index] = eigenPair.eigenvalue;
      index++;
    }
    DenseMatrix sortedEigenvectors = new DenseMatrix(eigenvectors);
    eigenvectors = LinearAlgebraUtils.reshape1DArray(sortedEigenvectors.getData(), gramDimension, gramDimension);
  }

  @Override
  public double[] getVariances() {
    return eigenvalues;
  }

  @Override
  public double[][] getPrincipalComponents() {
    return eigenvectors;
  }
}
