package hex.psvm.psvm;

import water.MRTask;
import water.fvec.*;
import water.util.ArrayUtils;
import water.util.FrameUtils;

import java.util.Arrays;

/**
 * Utils class for matrix operations. See also {code DMatrix.java}
 * 
 */
public class MatrixUtils {

  /**
   * Calculates matrix product M'DM
   * @param m Frame representing the M matrix (m x n), M' is expected to be lower triangular
   * @param diagonal Vec representation of a diagonal matrix (m x m)
   * @return lower triangular portion of the product (the product is a symmetrical matrix, only the lower portion is represented)
   */
  public static LLMatrix productMtDM(Frame m, Vec diagonal) {
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
   * @param v Vec representing vector v (m x 1)
   * @return m-element array representing the result of the product
   */
  public static double[] productMtv(Frame m, Vec v) {
    Vec[] vecs = ArrayUtils.append(m.vecs(), v);
    return new ProductMtvTask().doAll(vecs)._result;
  }

  /**
   * Previous Mtv method is not working.
   * 
   * Calculates matrix-vector product M'v
   * 
   * @param m Frame representing matrix M (m x n)
   * @param v Vec representing vector v (n x 1)
   * @return m-element array representing the result of the product
   */
  public static Vec workingProductMtv(Frame m, Vec v) {
    // TODO avalenta better vec creation
    Vec result = new ProductMtvTask2(v, m.numRows()).doAll(m)._result;
    // System.out.println(Arrays.toString(FrameUtils.asDoubles(result)));
    return result;
  }  

  /**
   * Calculates vector-vector product v1*v2
   *
   * @param v1 vec representing first element (1 x n)
   * @param v2 Vec representing second element (n x 1)
   * @return double value (vector 1 x 1)
   */
  public static double productVtV(Vec v1, Vec v2) {
    return new ProductMtvTask().doAll(v1, v2)._result[0];
  }

  /**
   * Calculates matrix-vector subtraction M'v
   * 
   * @param m Frame representing matrix M (m x n)
   * @param v Vec representing vector v (n x 1)
   * @return m-element array representing the result of the subtraction
   */
  public static Frame subtractionMtv(Frame m, Vec v) {
    if (v.length() != m.numCols()) {
      throw new UnsupportedOperationException("Vector elements number must be the same as matrix column number");
    }
    Frame result = new SubMtvTask(v).doAll(m.deepCopy("newKey"))._fr;
    return result;
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

  static class SubMtvTask extends MRTask<SubMtvTask> {

    private Vec v;

    public SubMtvTask(Vec v) {
      this.v = v;
    }

    @Override
    public void map(Chunk[] cs) {
      for (int column = 0; column < cs.length; column++) {
        for (int row = 0; row < cs[0]._len; row++) {
          cs[column].set(row, cs[column].atd(row) - v.at(column));
        }
      }
    }
  }

  static class ProductMtvTask2 extends MRTask<ProductMtvTask2> {
    private final Vec v;

    private Vec _result;

    public ProductMtvTask2(Vec v, long length) {
      this.v = v;
      _result = Vec.makeZero(length);
    }
    
    @Override
    public void map(Chunk[] cs) {
      for (int row = 0; row < cs[0]._len; row++) {
        double sum = 0.0;
        for (int column = 0; column < cs.length; ++column) {
          sum += cs[column].atd(row) * v.at(column);
        }
        _result.set(cs[0].start() + row, sum);
      }
    }
  }

}
