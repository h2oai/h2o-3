package hex.pca;

import hex.util.LinearAlgebraUtils;
import no.uib.cipr.matrix.DenseMatrix;
import no.uib.cipr.matrix.NotConvergedException;

/**
 * @author mathemage </ha@h2o.ai>
 * @date 1.5.17
 */
public class SVD_MTJ {
  private PCA pca;
  private DenseMatrix gramJ;
  private no.uib.cipr.matrix.SVD svdJ;
  private double[][] vt_2D;

  public SVD_MTJ(PCA pca, double[][] gramMatrix) {
    this.pca = pca;
    this.gramJ = new DenseMatrix(gramMatrix);
    runSVD();
  }

  public double[] getS() {
    return svdJ.getS();
  }

  public double[][] getVt_2D() {
    return vt_2D;
  }

  private void runSVD() {
    int gramDimension = gramJ.numRows();
    try {
      // Note: gramJ will be overwritten after this
      svdJ = new no.uib.cipr.matrix.SVD(gramDimension, gramDimension).factor(gramJ);
    } catch (NotConvergedException e) {
      throw new RuntimeException(e);
    }
    pca._job.update(1, "Computing stats from SVD");
    double[] Vt_1D = svdJ.getVt().getData();
    vt_2D = LinearAlgebraUtils.reshape1DArray(Vt_1D, gramDimension, gramDimension);
  }
}
