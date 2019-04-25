package hex.svm;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;

public class MatrixUtils {

  static LLMatrix cf(LLMatrix original) {
    int dim = original.dim();
    LLMatrix m = new LLMatrix(dim);
    for (int i = 0; i < dim; ++i) {
      for (int j = i; j < dim; ++j) {
        double sum = original.get(j, i);
        for (int k = i-1; k >= 0; --k) {
          sum -= m.get(i, k) * m.get(j, k);
        }
        if (i == j) {
          if (sum <= 0) {  // sum should be larger than 0
            throw new IllegalStateException("Only symmetric positive definite matrix can perform Cholesky factorization.");
          }
          m.set(i, i, Math.sqrt(sum));
        } else {
          m.set(j, i, sum / m.get(i, i));
        }
      }
    }
    return m;
  }

  static LLMatrix productMM(Frame icf, Vec diagonal) {
    Vec[] vecs = ArrayUtils.append(icf.vecs(), diagonal);
    double result[] = new ProductMMTask().doAll(vecs)._result;
    
    LLMatrix m = new LLMatrix(icf.numCols());
    int pos = 0;
    for (int i = 0; i < icf.numCols(); i++) {
      for (int j = 0; j <= i; j++) {
        m.set(i, j, result[pos++] + (i == j ? 1 : 0));
      }
    }
    return m;
  }

  private static class ProductMMTask extends MRTask<ProductMMTask> {
    // OUT
    private double[] _result;

    @Override
    public void map(Chunk[] cs) {
      final Chunk diagonal = cs[cs.length - 1];
      final int column  = cs.length - 1;
      _result = new double[(column + 1) * column / 2];
      double[] buff = new double[cs[0]._len];
      int offset = 0;
      for (int i = 0; i < column; i++) {
        offset += i;
        for (int p = 0; p < buff.length; p++) {
          buff[p] = cs[i].atd(p) * diagonal.atd(p);
        }
        for (int j = 0; j <= i; j++) {
          double tmp = 0;
          for (int p = 0; p < buff.length; p++) {
            tmp += buff[p] * cs[j].atd(p);
          }
          _result[offset+j] = tmp;
        }
      }
    }

    @Override
    public void reduce(ProductMMTask mrt) {
      ArrayUtils.add(_result, mrt._result);
    }
  }

  static void cholBackwardSub(LLMatrix a, double[] b, double[] x) {
    int dim = a.dim();
    for (int k = dim - 1; k >= 0; k--) {
      double tmp = b[k];
      for (int i = k + 1; i < dim; i++) {
        tmp -= x[i] * a.get(i, k);
      }
      x[k] = tmp / a.get(k, k);
    }
  }

  static void cholForwardSub(LLMatrix a, double[] b, double[] x) {
    int dim = a.dim();
    for (int k = 0; k < dim; ++k) {
      double tmp = b[k];
      for (int i = 0; i < k; ++i) {
        tmp -= x[i] * a.get(k, i);
      }
      x[k] = tmp / a.get(k, k);
    }
  }

}
