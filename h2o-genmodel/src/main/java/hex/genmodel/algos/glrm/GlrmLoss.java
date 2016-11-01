package hex.genmodel.algos.glrm;

import hex.genmodel.utils.ArrayUtils;

/**
 * Loss function for the GLRM algorithm.
 */
public enum GlrmLoss {

  //--------------------------------------------------------------------------------------------------------------------
  // Loss functions for numeric features
  //--------------------------------------------------------------------------------------------------------------------

  Quadratic {
    @Override public boolean isForNumeric() { return true; }
    @Override public boolean isForCategorical() { return false; }
    @Override public boolean isForBinary() { return false; }

    @Override public double loss(double u, double a) {
      return (u - a)*(u - a);
    }
    @Override public double lgrad(double u, double a) {
      return 2*(u - a);
    }
    @Override public double impute(double u) {
      return u;
    }
  },

  Absolute {
    @Override public boolean isForNumeric() { return true; }
    @Override public boolean isForCategorical() { return false; }
    @Override public boolean isForBinary() { return false; }

    @Override public double loss(double u, double a) {
      return Math.abs(u - a);
    }
    @Override public double lgrad(double u, double a) {
      return Math.signum(u - a);
    }
    @Override public double impute(double u) {
      return u;
    }
  },

  Huber {
    @Override public boolean isForNumeric() { return true; }
    @Override public boolean isForCategorical() { return false; }
    @Override public boolean isForBinary() { return false; }

    @Override public double loss(double u, double a) {
      double x = u - a;
      return x > 1? x - 0.5 : x < -1 ? -x - 0.5 : 0.5*x*x;
    }
    @Override public double lgrad(double u, double a) {
      double x = u - a;
      return x > 1? 1 : x < -1 ? -1 : x;
    }
    @Override public double impute(double u) {
      return u;
    }
  },

  Poisson {
    @Override public boolean isForNumeric() { return true; }
    @Override public boolean isForCategorical() { return false; }
    @Override public boolean isForBinary() { return false; }

    @Override public double loss(double u, double a) {
      assert a >= 0 : "Poisson loss L(u,a) requires variable a >= 0";
      return Math.exp(u) + (a == 0 ? 0 : -a*u + a*Math.log(a) - a);   // Since \lim_{a->0} a*log(a) = 0
    }
    @Override public double lgrad(double u, double a) {
      assert a >= 0 : "Poisson loss L(u,a) requires variable a >= 0";
      return Math.exp(u) - a;
    }
    @Override public double impute(double u) {
      return Math.exp(u);
    }
  },

  Periodic {
    @Override public boolean isForNumeric() { return true; }
    @Override public boolean isForCategorical() { return false; }
    @Override public boolean isForBinary() { return false; }

    private double f;
    private int period;

    @Override public double loss(double u, double a) {
      return 1 - Math.cos((u - a)*f);
    }
    @Override public double lgrad(double u, double a) {
      return f * Math.sin((u - a)*f);
    }
    @Override public double impute(double u) {
      return u;
    }

    @Override public void setParameters(int period) {
      this.period = period;
      f = 2 * Math.PI / period;
    }
    @Override public String toString() { return "Periodic(" + period + ")"; }
  },


  //--------------------------------------------------------------------------------------------------------------------
  // Loss functions for binary features
  //--------------------------------------------------------------------------------------------------------------------

  Logistic {
    @Override public boolean isForNumeric() { return false; }
    @Override public boolean isForCategorical() { return false; }
    @Override public boolean isForBinary() { return true; }

    @Override public double loss(double u, double a) {
      assert a == 0 || a == 1 : "Logistic loss should be applied to binary features only";
      return Math.log(1 + Math.exp((1 - 2*a)*u));
    }
    @Override public double lgrad(double u, double a) {
      double s = 1 - 2*a;
      return s/(1 + Math.exp(s*u));
    }
    @Override public double impute(double u) {
      return u > 0? 1 : 0;
    }
  },

  Hinge {
    @Override public boolean isForNumeric() { return false; }
    @Override public boolean isForCategorical() { return false; }
    @Override public boolean isForBinary() { return true; }

    @Override public double loss(double u, double a) {
      assert a == 0 || a == 1 : "Hinge loss should be applied to binary variables only";
      return Math.max(1 + (1 - 2*a)*u, 0);
    }
    @Override public double lgrad(double u, double a) {
      double s = 1 - 2*a;
      return 1 + s*u > 0? s : 0;
    }
    @Override public double impute(double u) {
      return u > 0? 1 : 0;
    }
  },


  //--------------------------------------------------------------------------------------------------------------------
  // Loss functions for multinomial features
  //--------------------------------------------------------------------------------------------------------------------

  Categorical {
    @Override public boolean isForNumeric() { return false; }
    @Override public boolean isForCategorical() { return true; }
    @Override public boolean isForBinary() { return false; }

    @Override public double mloss(double[] u, int a) {
      return mloss(u, a, u.length);
    }
    // this function performs the same function as the one above but it is memory optimized for the original
    // GLRM.java code.  See GLRM.java for details
    @Override public double mloss(double[] u, int a, int u_len) {
      if (!(a >= 0 && a < u_len))
        throw new IndexOutOfBoundsException("a must be between 0 and " + (u_len - 1));
      double sum = 0;
      for (int ind=0; ind < u_len; ind++)
        sum += Math.max(1 + u[ind], 0);
      sum += Math.max(1 - u[a], 0) - Math.max(1 + u[a], 0);
      return sum;
    }
    @Override public double[] mlgrad(double[] u, int a) {
      double[] grad = new double[u.length];
      return mlgrad(u, a, grad, u.length);
    }
    @Override public double[] mlgrad(double[] u, int a, double[] grad, int u_len) {
      if (!(a >= 0 && a < u_len)) throw new IndexOutOfBoundsException("a must be between 0 and " + (u_len - 1));
      for (int i = 0; i < u_len; i++)
        grad[i] = (1 + u[i] > 0) ? 1 : 0;
      grad[a] = (1 - u[a] > 0) ? -1 : 0;
      return grad;
    }
    @Override public int mimpute(double[] u) {
      return ArrayUtils.maxIndex(u);
    }
  },

  Ordinal {
    @Override public boolean isForNumeric() { return false; }
    @Override public boolean isForCategorical() { return true; }
    @Override public boolean isForBinary() { return false; }

    @Override public double mloss(double[] u, int a) {
      if (!(a >= 0 && a < u.length)) throw new IndexOutOfBoundsException("a must be between 0 and " + (u.length - 1));
      double sum = 0;
      for (int i = 0; i < u.length - 1; i++)
        sum += a > i ? Math.max(1 - u[i], 0) : 1;
      return sum;
    }
    @Override public double mloss(double[] u, int a, int u_len) {
      if (!(a >= 0 && a < u_len)) throw new IndexOutOfBoundsException("a must be between 0 and " + (u_len - 1));
      double sum = 0;
      for (int i = 0; i < u_len - 1; i++)
        sum += a > i ? Math.max(1 - u[i], 0) : 1;
      return sum;
    }
    @Override public double[] mlgrad(double[] u, int a) {
      if (!(a >= 0 && a < u.length)) throw new IndexOutOfBoundsException("a must be between 0 and " + (u.length - 1));
      double[] grad = new double[u.length];
      for (int i = 0; i < u.length - 1; i++)
        grad[i] = (a > i && 1 - u[i] > 0) ? -1 : 0;
      return grad;
    }
    @Override public double[] mlgrad(double[] u, int a, double[] grad, int u_len) {
      if (!(a >= 0 && a < u_len)) throw new IndexOutOfBoundsException("a must be between 0 and " + (u_len - 1));
      for (int i = 0; i < u_len - 1; i++)
        grad[i] = (a > i && 1 - u[i] > 0) ? -1 : 0;
      return grad;
    }
    @Override public int mimpute(double[] u) {
      double sum = u.length - 1;
      double best_loss = sum;
      int best_a = 0;
      for (int a = 1; a < u.length; a++) {
        sum -= Math.min(1, u[a - 1]);
        if (sum < best_loss) {
          best_loss = sum;
          best_a = a;
        }
      }
      return best_a;
    }
  };


  //--------------------------------------------------------------------------------------------------------------------
  // Public interface
  //--------------------------------------------------------------------------------------------------------------------

  public abstract boolean isForNumeric();
  public abstract boolean isForCategorical();
  public abstract boolean isForBinary();

  /** Loss function for numeric variables */
  public double loss(double u, double a) { throw new UnsupportedOperationException(); }

  /** \grad_u L(u,a): Derivative of the numeric loss function with respect to u */
  public double lgrad(double u, double a) { throw new UnsupportedOperationException(); }

  /** \argmin_a L(u, a): Data imputation for real numeric values */
  public double impute(double u) { throw new UnsupportedOperationException(); }

  /** Loss function for categorical variables where the size of u represents the true column length. */
  public double mloss(double[] u, int a) { throw new UnsupportedOperationException(); }

  /** Loss function for categorical variables performing same function as mloss above.  However, in this case,
   * the size of u can be much bigger than what is needed.  The actual length of u is now specified in u_len. */
  public double mloss(double[] u, int a, int u_len) { throw new UnsupportedOperationException(); }

  /** \grad_u L(u,a): Gradient of multidimensional loss function with respect to u */
  public double[] mlgrad(double[] u, int a) { throw new UnsupportedOperationException(); }

  /** \grad_u L(u,a): Gradient of multidimensional loss function with respect to u.  This method avoids the
   * memory allocation compared to the method above by passing in a array prod which can be longer
   * than the actual column length. The actual column length for prod is now specified by u_len. */
  public double[] mlgrad(double[] u, int a, double[] prod, int u_len) { throw new UnsupportedOperationException(); }

  /** \argmin_a L(u, a): Data imputation for categorical values {0, 1, 2, ...} */
  public int mimpute(double[] u) { throw new UnsupportedOperationException(); }

  /** Initialize additional parameters on the loss function. Currently used by Periodic class only. */
  public void setParameters(int p) { throw new UnsupportedOperationException(); }


}
