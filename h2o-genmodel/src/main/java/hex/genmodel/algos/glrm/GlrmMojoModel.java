package hex.genmodel.algos.glrm;

import hex.ModelCategory;
import hex.genmodel.MojoModel;

import java.util.EnumSet;
import java.util.Random;


/**
 */
public class GlrmMojoModel extends MojoModel {
  public int _ncolA;
  public int _ncolX;
  public int _ncolY;
  public int _nrowY;
  public double[][] _archetypes;
  public int[] _numLevels;
  public int[] _permutation;
  public GlrmLoss[] _losses;
  public GlrmRegularizer _regx;
  public double _gammax;
  public GlrmInitialization _init;
  public int _ncats;
  public int _nnums;
  public double[] _normSub;
  public double[] _normMul;
  // We don't really care about regularization of Y since it is not used during scoring

  private static EnumSet<ModelCategory> CATEGORIES = EnumSet.of(ModelCategory.AutoEncoder, ModelCategory.DimReduction);
  @Override public EnumSet<ModelCategory> getModelCategories() {
    return CATEGORIES;
  }


  protected GlrmMojoModel(String[] columns, String[][] domains) {
    super(columns, domains);
  }

  @Override public int getPredsSize(ModelCategory mc) {
    return _ncolX;
  }

  /**
   * This function corresponds to the DimReduction model category
   */
  @Override
  public double[] score0(double[] row, double[] preds) {
    assert row.length == _ncolA;
    assert preds.length == _ncolX;
    assert _nrowY == _ncolX;
    assert _archetypes.length == _nrowY;
    assert _archetypes[0].length == _ncolY;

    // Step 0: prepare the data row
    double[] a = new double[_ncolA];
    for (int i = 0; i < _ncolA; i++)
      a[i] = row[_permutation[i]];

    // Step 1: initialize X  (for now do Random initialization only)
    double[] x = new double[_ncolX];
    Random random = new Random();
    for (int i = 0; i < _ncolX; i++)
      x[i] = random.nextGaussian();
    x = _regx.project(x, random);

    // Step 2: update X based on prox-prox algorithm, iterate until convergence
    double alpha = 1;
    double obj = objective(x, a);
    boolean done = false;
    int iters = 0;
    while (!done && iters++ < 100) {
      // Compute the gradient of the loss function
      double[] grad = gradientL(x, a);

      // Try to make a step of size alpha, until we can achieve improvement in the objective.
      double[] u = new double[_ncolX];
      while (true) {
        // Compute the tentative new x (using the prox algorithm)
        for (int k = 0; k < _ncolX; k++) {
          u[k] = x[k] - alpha * grad[k];
        }
        double[] xnew = _regx.rproxgrad(u, alpha * _gammax, random);

        double newobj = objective(xnew, a);
        if (newobj < obj) {
          if (newobj > obj * 1.000001) done = true;
          obj = newobj;
          x = xnew;
          alpha *= 1.05;
          break;
        } else {
          alpha *= 0.6;
        }
      }
    }

    // Step 3: return the result
    System.arraycopy(x, 0, preds, 0, _ncolX);
    return preds;
  }

  /**
   * Compute gradient of the objective function with respect to x, i.e. d/dx Sum_j[L_j(xY_j, a)]
   * @param x: current x row
   * @param a: the adapted data row
   */
  private double[] gradientL(double[] x, double[] a) {
    // Prepate output row
    double[] grad = new double[_ncolX];

    // Categorical columns
    int cat_offset = 0;
    for (int j = 0; j < _ncats; j++) {
      if (Double.isNaN(a[j])) continue;   // Skip missing observations in row (???)
      int n_levels = _numLevels[j];

      // Calculate xy = x * Y_j where Y_j is sub-matrix corresponding to categorical col j
      double[] xy = new double[n_levels];
      for (int level = 0; level < n_levels; level++) {
        for (int k = 0; k < _ncolX; k++) {
          xy[level] += x[k] * _archetypes[k][level + cat_offset];
        }
      }

      // Gradient wrt x is matrix product \grad L_j(x * Y_j, A_j) * Y_j'
      double[] gradL = _losses[j].mlgrad(xy, (int) a[j]);
      for (int k = 0; k < _ncolX; k++) {
        for (int c = 0; c < n_levels; c++)
          grad[k] += gradL[c] * _archetypes[k][c + cat_offset];
      }
      cat_offset += n_levels;
    }

    // Numeric columns
    for (int j = _ncats; j < _ncolA; j++) {
      int js = j - _ncats;
      if (Double.isNaN(a[j])) continue;   // Skip missing observations in row

      // Inner product x * y_j
      double xy = 0;
      for (int k = 0; k < _ncolX; k++)
        xy += x[k] * _archetypes[k][js + cat_offset];

      // Sum over y_j weighted by gradient of loss \grad L_j(x * y_j, A_j)
      double gradL = _losses[j].lgrad(xy, (a[j] - _normSub[js]) * _normMul[js]);
      for (int k = 0; k < _ncolX; k++)
        grad[k] += gradL * _archetypes[k][js + cat_offset];
    }
    return grad;
  }

  private double objective(double[] x, double[] a) {
    double res = 0;

    // Loss: Categorical columns
    int cat_offset = 0;
    for (int j = 0; j < _ncats; j++) {
      if (Double.isNaN(a[j])) continue;   // Skip missing observations in row
      int n_levels = _numLevels[j];
      double[] xy = new double[n_levels];
      for (int level = 0; level < n_levels; level++) {
        for (int k = 0; k < _ncolX; k++) {
          xy[level] += x[k] * _archetypes[k][level + cat_offset];
        }
      }
      res +=  _losses[j].mloss(xy, (int) a[j]);
      cat_offset += n_levels;
    }
    // Loss: Numeric columns
    for (int j = _ncats; j < _ncolA; j++) {
      int js = j - _ncats;
      if (Double.isNaN(a[j])) continue;   // Skip missing observations in row
      double xy = 0;
      for (int k = 0; k < _ncolX; k++)
        xy += x[k] * _archetypes[k][js + cat_offset];
      res += _losses[j].loss(xy, (a[j] - _normSub[js]) * _normMul[js]);
    }

    res += _gammax * _regx.regularize(x);
    return res;
  }
}
