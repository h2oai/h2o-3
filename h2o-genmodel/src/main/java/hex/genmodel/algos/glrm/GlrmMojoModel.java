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
  public double[][] _archetypes_raw;
  public int[] _numLevels;
  public int [] _catOffsets;
  public int[] _permutation;
  public GlrmLoss[] _losses;
  public GlrmRegularizer _regx;
  public double _gammax;
  public GlrmInitialization _init;
  public int _ncats;
  public int _nnums;
  public double[] _normSub;
  public double[] _normMul;
  public long _seed;  // added to ensure reproducibility
  public boolean _transposed;
  public boolean _reverse_transform;

  // We don't really care about regularization of Y since it is changed during scoring

  /**
   * This is the "learning rate" in the gradient descent method. More specifically, at each iteration step we update
   * x according to x_new = x_old - alpha * grad_x(obj)(x_old). If the objective evaluated at x_new is smaller than
   * the objective at x_old, then we proceed with the update, increasing alpha slightly (in case we learn too slowly);
   * however if the objective at x_new is bigger than the original objective, then we "overshot" and therefore reduce
   * alpha in half.
   * When reusing the alpha between multiple computations of the gradient, we find that alpha eventually "stabilizes"
   * in a certain range; moreover that range is roughly the same when scoring different rows. This is why alpha was
   * made static -- so that its value from previous scoring round can be reused to achieve faster convergence.
   * This approach is not thread-safe! If we ever make GenModel capable of scoring multiple rows in parallel, this
   * will have to be changed to make updates to alpha synchronized.
   */
 // private double alpha = 1.0;  // Do not shared across class.
  private static final double DOWN_FACTOR = 0.5;
  private static final double UP_FACTOR = Math.pow(1.0/DOWN_FACTOR, 1.0/4);
  public long _rcnt = 0;  // increment per row and can be changed to different values to ensure reproducibility

  static {
    //noinspection ConstantAssertCondition,ConstantConditions
    assert DOWN_FACTOR < 1 && DOWN_FACTOR > 0;
    assert UP_FACTOR > 1;
  }

  private static EnumSet<ModelCategory> CATEGORIES = EnumSet.of(ModelCategory.AutoEncoder, ModelCategory.DimReduction);
  @Override public EnumSet<ModelCategory> getModelCategories() {
    return CATEGORIES;
  }


  public GlrmMojoModel(String[] columns, String[][] domains, String responseColumn) {
    super(columns, domains, responseColumn);
  }

  @Override public int getPredsSize(ModelCategory mc) {
    return _ncolX;
  }

  public double[] score0(double[] row, double[] preds, long seedValue) {
    assert row.length == _ncolA;
    assert preds.length == _ncolX;
    assert _nrowY == _ncolX;
    assert _archetypes.length == _nrowY;
    assert _archetypes[0].length == _ncolY;
    double alpha=1.0;  // reset back to 1 for each row

    // Step 0: prepare the data row
    double[] a = new double[_ncolA];
    for (int i = 0; i < _ncolA; i++)
      a[i] = row[_permutation[i]];

    // Step 1: initialize X  (for now do Random initialization only)
    double[] x = new double[_ncolX];
    Random random = new Random(seedValue);  // change the random seed everytime it is used
    for (int i = 0; i < _ncolX; i++)
      x[i] = random.nextGaussian();
    x = _regx.project(x, random);

    // Step 2: update X based on prox-prox algorithm, iterate until convergence
    double obj = objective(x, a);
    boolean done = false;
    int iters = 0;
    while (!done && iters++ < 100) {
      // Compute the gradient of the loss function
      double[] grad = gradientL(x, a);
      // Try to make a step of size alpha, until we can achieve improvement in the objective.
      double[] u = new double[_ncolX];
      while (true) {  // inner loop to run and make sure we find a stepsize and x factor that decrease objective
        // System.out.println("  " + alpha);
        // Compute the tentative new x (using the prox algorithm)
        for (int k = 0; k < _ncolX; k++) {
          u[k] = x[k] - alpha * grad[k];
        }
        double[] xnew = _regx.rproxgrad(u, alpha * _gammax, random);
        double newobj = objective(xnew, a);

        if (newobj == 0) break;
        double obj_improvement = 1 - newobj/obj;
        if (obj_improvement >= 0) {
          if (obj_improvement < 1e-6) done = true;
          obj = newobj;
          x = xnew;
          alpha *= UP_FACTOR;
          break;
        } else {
          alpha *= DOWN_FACTOR;
        }
      }
    }
    System.arraycopy(x, 0, preds, 0, _ncolX);
    return preds;

  }

  /**
   * This function corresponds to the DimReduction model category
   */
  @Override
  public double[] score0(double[] row, double[] preds) {
    return score0(row, preds, _seed+_rcnt++);
  }

  // impute data from x and archetypes
  public static double[] impute_data(double[] xfactor, double[] preds, int nnums, int ncats, int[] permutation,
                                     boolean reverse_transform, double[] normMul, double[] normSub, GlrmLoss[] losses,
                                     boolean transposed, double[][] archetypes_raw, int[] catOffsets, int[] numLevels) {
    assert preds.length == nnums + ncats;

    // Categorical columns
    for (int d = 0; d <ncats; d++) {
      double[] xyblock = lmulCatBlock(xfactor,d, numLevels, transposed, archetypes_raw, catOffsets);
      preds[permutation[d]] = losses[d].mimpute(xyblock);
    }

    // Numeric columns
    for (int d = ncats; d < preds.length; d++) {
      int ds = d - ncats;
      double xy = lmulNumCol(xfactor, ds, transposed, archetypes_raw, catOffsets);
      preds[permutation[d]] = losses[d].impute(xy);
      if (reverse_transform)
        preds[permutation[d]] = preds[permutation[d]] / normMul[ds] + normSub[ds];
    }
    return preds;
  }

  // For j = 0 to number of numeric columns - 1
  public static int getNumCidx(int j, int[] catOffsets) {
    return catOffsets[catOffsets.length-1]+j;
  }
  // Inner product x * y_j where y_j is numeric column j of Y
  public static double lmulNumCol(double[] x, int j, boolean transposed, double[][] archetypes_raw, int[] catOffsets) {
    assert x != null && x.length == rank(transposed, archetypes_raw) : "x must be of length " + rank(transposed, archetypes_raw);
    int cidx = getNumCidx(j, catOffsets);

    double prod = 0;
    if (transposed) {
      for (int k = 0; k < rank(transposed, archetypes_raw); k++)
        prod += x[k] * archetypes_raw[cidx][k];
    } else {
      for (int k = 0; k < rank(transposed, archetypes_raw); k++)
        prod += x[k] * archetypes_raw[k][cidx];
    }
    return prod;
  }

  // For j = 0 to number of categorical columns - 1, and level = 0 to number of levels in categorical column - 1
  public static int getCatCidx(int j, int level, int[] numLevels, int[] catOffsets) {
    int catColJLevel = numLevels[j];
    assert catColJLevel != 0 : "Number of levels in categorical column cannot be zero";
    assert !Double.isNaN(level) && level >= 0 && level < catColJLevel : "Got level = " + level +
            " when expected integer in [0," + catColJLevel + ")";
    return catOffsets[j]+level;
  }

  // Vector-matrix product x * Y_j where Y_j is block of Y corresponding to categorical column j
  public static double[] lmulCatBlock(double[] x, int j, int[] numLevels, boolean transposed, double[][] archetypes_raw, int[] catOffsets) {
    int catColJLevel = numLevels[j];
    assert catColJLevel != 0 : "Number of levels in categorical column cannot be zero";
    assert x != null && x.length == rank(transposed, archetypes_raw) : "x must be of length " +
            rank(transposed, archetypes_raw);
    double[] prod = new double[catColJLevel];

    if (transposed) {
      for (int level = 0; level < catColJLevel; level++) {
        int cidx = getCatCidx(j,level, numLevels, catOffsets);
        for (int k = 0; k < rank(transposed, archetypes_raw); k++)
          prod[level] += x[k] * archetypes_raw[cidx][k];
      }
    } else {
      for (int level = 0; level < catColJLevel; level++) {
        int cidx = getCatCidx(j,level, numLevels, catOffsets);
        for (int k = 0; k < rank(transposed, archetypes_raw); k++)
          prod[level] += x[k] * archetypes_raw[k][cidx];
      }
    }
    return prod;
  }

  public static int rank(boolean transposed, double[][] archetypes_raw) {
    return transposed ? archetypes_raw[0].length : archetypes_raw.length;
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
