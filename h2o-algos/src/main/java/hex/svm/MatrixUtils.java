package hex.svm;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;

class MatrixUtils {

  /**
   * Calculates matrix product M'DM
   * @param m Frame representing the M matrix (m x n), M' is expected to be lower triangular
   * @param diagonal Vec representation of a diagonal matrix (m x m)
   * @return lower triangular portion of the product (the product is a symmetrical matrix, only the lower portion is represented)
   */
  static LLMatrix productMtDM(Frame m, Vec diagonal) {
    Vec[] vecs = ArrayUtils.append(m.vecs(), diagonal);
    double result[] = new ProductMMTask().doAll(vecs)._result;
    
    LLMatrix product = new LLMatrix(m.numCols());
    int pos = 0;
    for (int i = 0; i < m.numCols(); i++) {
      for (int j = 0; j <= i; j++) {
        product.set(i, j, result[pos++]);
      }
    }
    return product;
  }

  /**
   * Calculates matrix-vector product M'v
   * @param m Frame representing matrix M (m x n)
   * @param v Vec representing vector v (n x 1)
   * @return m-element array representing the result of the product
   */
  static double[] productMtv(Frame m, Vec v) {
    Vec[] vecs = ArrayUtils.append(m.vecs(), v);
    return new ProductMtvTask().doAll(vecs)._result;
  }

  private static class ProductMMTask extends MRTask<ProductMMTask> {
    // OUT
    private double[] _result;

    @Override
    public void map(Chunk[] cs) {
      final int column = cs.length - 1;
      final Chunk diagonal = cs[column];
      _result = new double[(column + 1) * column / 2];
      double[] buff = new double[cs[0]._len];
      int offset = 0;
      for (int i = 0; i < column; i++) {
        offset += i;
        for (int p = 0; p < buff.length; p++) {
          buff[p] = cs[i].atd(p) * diagonal.atd(p);
        }
        for (int j = 0; j <= i; j++) {
          double sum = 0;
          for (int p = 0; p < buff.length; p++) {
            sum += buff[p] * cs[j].atd(p);
          }
          _result[offset+j] = sum;
        }
      }
    }

    @Override
    public void reduce(ProductMMTask mrt) {
      ArrayUtils.add(_result, mrt._result);
    }
  }

  static class ProductMtvTask extends MRTask<ProductMtvTask> {
    // OUT
    private double[] _result;

    @Override
    public void map(Chunk[] cs) {
      final int column = cs.length - 1;
      final Chunk v = cs[column];
      _result = new double[column];
      for (int j = 0; j < column; ++j) {
        double sum = 0.0;
        for (int i = 0; i < cs[0]._len; i++) {
          sum += cs[j].atd(i) * v.atd(i);
        }
        _result[j] = sum;
      }
    }

    @Override
    public void reduce(ProductMtvTask mrt) {
      ArrayUtils.add(_result, mrt._result);
    }
  }

}
