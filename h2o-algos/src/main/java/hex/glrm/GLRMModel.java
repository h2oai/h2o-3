package hex.glrm;

import hex.*;
import water.H2O;
import water.Key;
import water.fvec.Frame;
import water.util.TwoDimTable;

public class GLRMModel extends Model<GLRMModel,GLRMModel.GLRMParameters,GLRMModel.GLRMOutput> {

  public static class GLRMParameters extends Model.Parameters {
    public int _k = 1;                            // Rank of resulting XY matrix
    public Loss _loss = Loss.L2;                  // Loss function for numeric cols
    public MultiLoss _multi_loss = MultiLoss.Categorical;  // Loss function for categorical cols
    public Regularizer _regularization_x = Regularizer.L2;   // Regularization function for X matrix
    public Regularizer _regularization_y = Regularizer.L2;   // Regularization function for Y matrix
    public double _gamma_x = 0;                   // Regularization weight on X matrix
    public double _gamma_y = 0;                   // Regularization weight on Y matrix
    public int _max_iterations = 1000;            // Max iterations
    public double _init_step_size = 1.0;          // Initial step size (decrease until we hit min_step_size)
    public double _min_step_size = 1e-4;          // Min step size
    public long _seed = System.nanoTime();        // RNG seed
    public DataInfo.TransformType _transform = DataInfo.TransformType.NONE; // Data transformation (demean to compare with PCA)
    public GLRM.Initialization _init = GLRM.Initialization.PlusPlus;  // Initialization of Y matrix
    public Key<Frame> _user_points;               // User-specified Y matrix (for _init = User)
    public Key<Frame> _loading_key;               // Key to save X matrix
    public boolean _recover_svd = false;          // Recover singular values and eigenvectors of XY at the end?

    public enum Loss {
      L2, L1, Huber, Poisson, Hinge, Logistic
    }

    public enum MultiLoss {
      Categorical, Ordinal
    }

    public enum Regularizer {
      L2, L1,
    }

    // L(u,a): Loss function
    public final double loss(double u, double a) {
      switch(_loss) {
        case L2:
          return (u-a)*(u-a);
        case L1:
          return Math.abs(u-a);
        case Huber:
          return Math.abs(u-a) <= 1 ? 0.5*(u-a)*(u-a) : Math.abs(u-a)-0.5;
        case Poisson:
          return Math.exp(u) - a*u + a*Math.log(a) - a;
        case Hinge:
          return Math.max(1-a*u,0);
        case Logistic:
          return Math.log(1+Math.exp(-a*u));
        default:
          throw new RuntimeException("Unknown loss function " + _loss);
      }
    }

    // \grad_u L(u,a): Gradient of loss function with respect to u
    public final double lgrad(double u, double a) {
      switch(_loss) {
        case L2:
          return 2*(u-a);
        case L1:
          return Math.signum(u - a);
        case Huber:
          return Math.abs(u-a) <= 1 ? u-a : Math.signum(u-a);
        case Poisson:
          return Math.exp(u)-a;
        case Hinge:
          return a*u <= 1 ? -a : 0;
        case Logistic:
          return -a/(1+Math.exp(a*u));
        default:
          throw new RuntimeException("Unknown loss function " + _loss);
      }
    }

    // L(u,a): Multidimensional loss function
    public final double mloss(double[] u, int a) {
      if(a < 0 || a > u.length-1)
        throw new IllegalArgumentException("Index must be between 0 and " + String.valueOf(u.length-1));

      double sum = 0;
      switch(_multi_loss) {
        case Categorical:
          for (int i = 0; i < u.length; i++) sum += Math.max(1 + u[i], 0);
          sum += Math.max(1 - u[a], 0) - Math.max(1 + u[a], 0);
          return sum;
        case Ordinal:
          for (int i = 0; i < u.length-1; i++) sum += Math.max(a>i ? 1-u[i]:1, 0);
          return sum;
        default:
          throw new RuntimeException("Unknown multidimensional loss function " + _multi_loss);
      }
    }

    // \grad_u L(u,a): Gradient of multidimensional loss function with respect to u
    public final double[] mlgrad(double[] u, int a) {
      if(a < 0 || a > u.length-1)
        throw new IllegalArgumentException("Index must be between 0 and " + String.valueOf(u.length-1));

      double[] grad = new double[u.length];
      switch(_multi_loss) {
        case Categorical:
          for (int i = 0; i < u.length; i++) grad[i] = (1+u[i] > 0) ? 1:0;
          grad[a] = (1-u[a] > 0) ? -1:0;
          return grad;
        case Ordinal:
          for (int i = 0; i < u.length-1; i++) grad[i] = (a>i && 1-u[i] > 0) ? -1:0;
          return grad;
        default:
          throw new RuntimeException("Unknown multidimensional loss function " + _multi_loss);
      }
    }

    // r_i(x_i): Regularization function for single entry x_i
    public final double regularize_x(double u) { return regularize(u, _regularization_x); }
    public final double regularize_y(double u) { return regularize(u, _regularization_y); }
    public final double regularize(double u, Regularizer regularization) {
      switch(regularization) {
        case L2:
          return u*u;
        case L1:
          return Math.abs(u);
        default:
          throw new RuntimeException("Unknown regularization function " + regularization);
      }
    }

    // \sum_i r_i(x_i): Sum of regularization function for all entries of X
    public final double regularize_x(double[][] u) { return regularize(u, _regularization_x); }
    public final double regularize_y(double[][] u) { return regularize(u, _regularization_y); }
    public final double regularize(double[][] u, Regularizer regularization) {
      if(u == null) return 0;

      double ureg = 0;
      for(int i = 0; i < u.length; i++) {
        for(int j = 0; j < u[0].length; j++)
          ureg += regularize(u[i][j], regularization);
      }
      return ureg;
    }

    // \prox_{\alpha_k*r}(u): Proximal gradient of (step size) * (regularization function) evaluated at u
    public final double rproxgrad_x(double u, double alpha) { return rproxgrad(u, alpha, _gamma_x, _regularization_x); }
    public final double rproxgrad_y(double u, double alpha) { return rproxgrad(u, alpha, _gamma_y, _regularization_y); }
    public final double rproxgrad(double u, double alpha, double gamma, Regularizer regularization) {
      switch(regularization) {
        case L2:
          return u/(1+2*alpha*gamma);
        case L1:
          return Math.max(u-alpha*gamma,0) + Math.min(u+alpha*gamma,0);
        default:
          throw new RuntimeException("Unknown regularization function " + regularization);
      }
    }
  }

  public static class GLRMOutput extends Model.Output {
    // Iterations executed
    public int _iterations;

    // Current value of objective function
    public double _objective;

    // Average change in objective function this iteration
    public double _avg_change_obj;

    // Mapping from training data to lower dimensional k-space (Y)
    public double[][] _archetypes;

    // Final step size
    public double _step_size;

    // SVD of output XY
    public double[/*feature*/][/*k*/] _eigenvectors;
    public double[] _singular_vals;

    // Frame key of X matrix
    public Key<Frame> _loading_key;

    // Number of categorical and numeric columns
    public int _ncats;
    public int _nnums;

    // Categorical offset vector
    public int[] _catOffsets;

    // If standardized, mean of each numeric data column
    public double[] _normSub;

    // If standardized, one over standard deviation of each numeric data column
    public double[] _normMul;

    // Permutation matrix mapping training col indices to adaptedFrame
    public int[] _permutation;

    // Expanded column names of adapted training frame
    public String[] _names_expanded;

    public GLRMOutput(GLRM b) { super(b); }

    /** Override because base class implements ncols-1 for features with the
     *  last column as a response variable; for GLRM all the columns are features. */
    @Override public int nfeatures() { return _names.length; }

    @Override public ModelCategory getModelCategory() {
      return ModelCategory.DimReduction;
    }
  }

  public GLRMModel(Key selfKey, GLRMParameters parms, GLRMOutput output) { super(selfKey,parms,output); }

  // TODO: What should we do for scoring GLRM?
  @Override protected double[] score0(double[] data, double[] preds) {
    throw H2O.unimpl();
  }

  @Override public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    return new ModelMetricsGLRM.GLRMModelMetrics(_parms._k);
  }

  public static class ModelMetricsGLRM extends ModelMetricsUnsupervised {
    public ModelMetricsGLRM(Model model, Frame frame) {
      super(model, frame, Double.NaN);
    }

    // GLRM currently does not have any model metrics to compute during scoring
    public static class GLRMModelMetrics extends MetricBuilderUnsupervised {
      public GLRMModelMetrics(int dims) {
        _work = new double[dims];
      }

      @Override public double[] perRow(double[] dataRow, float[] preds, Model m) { return dataRow; }

      @Override
      public ModelMetrics makeModelMetrics(Model m, Frame f) {
        return m._output.addModelMetrics(new ModelMetricsGLRM(m, f));
      }
    }
  }
}
