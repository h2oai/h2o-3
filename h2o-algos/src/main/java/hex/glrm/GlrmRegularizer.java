package hex.glrm;

import water.util.ArrayUtils;
import water.util.MathUtils;

import java.util.Random;

/**
 * Regularization method for matrices X and Y in the GLRM algorithm.
 *
 * Examples:
 *  + Non-negative matrix factorization (NNMF): r_x = r_y = NonNegative
 *  + Orthogonal NNMF: r_x = OneSparse, r_y = NonNegative
 *  + K-means clustering: r_x = UnitOneSparse, r_y = 0 (\gamma_y = 0)
 *  + Quadratic mixture: r_x = Simplex, r_y = 0 (\gamma_y = 0)
 */
public enum GlrmRegularizer {

  None {
    @Override public double regularize(double[] u) {
      return 0;
    }
    @Override public double[] rproxgrad(double[] u, double delta, Random rand) {
      return u;
    }
    @Override public double[] project(double[] u, Random rand) {
      return u;
    }
  },

  Quadratic {
    @Override public double regularize(double[] u) {
      if (u == null) return 0;
      double ureg = 0;
      for (double ui : u) ureg += ui * ui;
      return ureg;
    }
    @Override public double[] rproxgrad(double[] u, double delta, Random rand) {
      if (u == null || delta == 0) return u;
      double[] v = new double[u.length];
      double f = 1/(1 + 2*delta);
      for (int i = 0; i < u.length; i++)
        v[i] = u[i] * f;
      return v;
    }
    @Override public double[] project(double[] u, Random rand) {
      return u;
    }
  },

  L2 {
    @Override public double regularize(double[] u) {
      if (u == null) return 0;
      double ureg = 0;
      for (double ui : u) ureg += ui * ui;
      return Math.sqrt(ureg);
    }
    @Override public double[] rproxgrad(double[] u, double delta, Random rand) {
      if (u == null || delta == 0) return u;
      double[] v = new double[u.length];
      // Proof uses Moreau decomposition;
      // see section 6.5.1 of Parikh and Boyd https://web.stanford.edu/~boyd/papers/pdf/prox_algs.pdf
      double weight = 1 - delta/ArrayUtils.l2norm(u);
      if (weight < 0) return v;   // Zero vector
      for (int i = 0; i < u.length; i++)
        v[i] = weight * u[i];
      return v;
    }
    @Override public double[] project(double[] u, Random rand) {
      return u;
    }
  },

  L1 {
    @Override public double regularize(double[] u) {
      if (u == null) return 0;
      double ureg = 0;
      for (double ui : u) ureg += Math.abs(ui);
      return ureg;
    }
    @Override public double[] rproxgrad(double[] u, double delta, Random rand) {
      if (u == null || delta == 0) return u;
      double[] v = new double[u.length];
      for (int i = 0; i < u.length; i++)
        v[i] = Math.max(u[i] - delta, 0) + Math.min(u[i] + delta, 0);
      return v;
    }
    @Override public double[] project(double[] u, Random rand) {
      return u;
    }
  },

  NonNegative {
    @Override public double regularize(double[] u) {
      if (u == null) return 0;
      for (double ui : u)
        if (ui < 0)
          return Double.POSITIVE_INFINITY;
      return 0;
    }
    @Override public double[] rproxgrad(double[] u, double delta, Random rand) {
      if (u == null || delta == 0) return u;
      double[] v = new double[u.length];
      for (int i = 0; i < u.length; i++)
        v[i] = Math.max(u[i], 0);
      return v;
    }
    // Proximal operator of indicator function for a set C is (Euclidean) projection onto C
    @Override public double[] project(double[] u, Random rand) {
      return u == null? null : rproxgrad(u, 1, rand);
    }
  },

  OneSparse {
    @Override public double regularize(double[] u) {
      if (u == null) return 0;
      int card = 0;
      for (double ui : u) {
        if (ui < 0) return Double.POSITIVE_INFINITY;
        else if (ui > 0) card++;
      }
      return card == 1 ? 0 : Double.POSITIVE_INFINITY;
    }
    @Override public double[] rproxgrad(double[] u, double delta, Random rand) {
      if (u == null || delta == 0) return u;
      double[] v = new double[u.length];
      int idx = ArrayUtils.maxIndex(u, rand);
      v[idx] = u[idx] > 0 ? u[idx] : 1e-6;
      return v;
    }
    @Override public double[] project(double[] u, Random rand) {
      return u == null? null : rproxgrad(u, 1, rand);
    }
  },

  UnitOneSparse {
    @Override public double regularize(double[] u) {
      if (u == null) return 0;
      int ones = 0, zeros = 0;
      for (double ui : u) {
        if (ui == 1) ones++;
        else if (ui == 0) zeros++;
        else return Double.POSITIVE_INFINITY;
      }
      return ones == 1 && zeros == u.length-1 ? 0 : Double.POSITIVE_INFINITY;
    }
    @Override public double[] rproxgrad(double[] u, double delta, Random rand) {
      if (u == null || delta == 0) return u;
      double[] v = new double[u.length];
      int idx = ArrayUtils.maxIndex(u, rand);
      v[idx] = 1;
      return v;
    }
    @Override public double[] project(double[] u, Random rand) {
      return u == null? null : rproxgrad(u, 1, rand);
    }
  },

  Simplex {
    @Override public double regularize(double[] u) {
      if (u == null) return 0;
      double sum = 0, absum = 0;
      for (double ui : u) {
        if (ui < 0) return Double.POSITIVE_INFINITY;
        else {
          sum += ui;
          absum += Math.abs(ui);
        }
      }
      return MathUtils.equalsWithinRecSumErr(sum, 1.0, u.length, absum) ? 0 : Double.POSITIVE_INFINITY;
    }
    @Override public double[] rproxgrad(double[] u, double delta, Random rand) {
      if (u == null || delta == 0) return u;

      // Proximal gradient algorithm by Chen and Ye in http://arxiv.org/pdf/1101.6081v2.pdf
      // 1) Sort input vector u in ascending order: u[1] <= ... <= u[n]
      int n = u.length;
      int[] idxs = new int[n];
      for (int i = 0; i < n; i++) idxs[i] = i;
      ArrayUtils.sort(idxs, u);

      // 2) Calculate cumulative sum of u in descending order
      // cumsum(u) = (..., u[n-2]+u[n-1]+u[n], u[n-1]+u[n], u[n])
      double[] ucsum = new double[n];
      ucsum[n-1] = u[idxs[n-1]];
      for (int i = n-2; i >= 0; i--)
        ucsum[i] = ucsum[i+1] + u[idxs[i]];

      // 3) Let t_i = (\sum_{j=i+1}^n u[j] - 1)/(n - i)
      // For i = n-1,...,1, set optimal t* to first t_i >= u[i]
      double t = (ucsum[0] - 1)/n;    // Default t* = (\sum_{j=1}^n u[j] - 1)/n
      for (int i = n-1; i >= 1; i--) {
        double tmp = (ucsum[i] - 1)/(n - i);
        if (tmp >= u[idxs[i-1]]) {
          t = tmp;
          break;
        }
      }

      // 4) Return max(u - t*, 0) as projection of u onto simplex
      double[] x = new double[u.length];
      for (int i = 0; i < u.length; i++)
        x[i] = Math.max(u[i] - t, 0);
      return x;
    }
    @Override public double[] project(double[] u, Random rand) {
      double reg = regularize(u);   // Check if inside simplex before projecting since algo is complicated
      if (reg == 0) return u;
      return rproxgrad(u, 1, rand);
    }
  };


  /** Regularization function applied to a single row x_i or column y_j */
  abstract public double regularize(double[] u);

  /** Regularization applied to an entire matrix (sum over rows) */
  public final double regularize(double[][] u) {
    if (u == null || this == GlrmRegularizer.None) return 0;
    double ureg = 0;
    for (double[] uarr : u) {
      ureg += regularize(uarr);
      if (Double.isInfinite(ureg)) break;
    }
    return ureg;
  }

  /** \prox_{\alpha_k*r}(u): Proximal gradient of (step size) * (regularization function) evaluated at vector u */
  abstract public double[] rproxgrad(double[] u, double delta, Random rand);

  /** Project X,Y matrices into appropriate subspace so regularizer is finite. Used during initialization. */
  abstract public double[] project(double[] u, Random rand);
}
