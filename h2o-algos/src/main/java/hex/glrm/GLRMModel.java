package hex.glrm;

import hex.DataInfo;
import hex.Model;
import hex.ModelMetrics;
import hex.ModelMetricsUnsupervised;
import water.H2O;
import water.Key;
import water.fvec.Frame;

public class GLRMModel extends Model<GLRMModel,GLRMModel.GLRMParameters,GLRMModel.GLRMOutput> {
  public static class GLRMParameters extends Model.Parameters {
    public int _k = 1;                            // Number of principal components
    public int _max_iterations = 1000;            // Max iterations
    public final Loss _loss = Loss.L2;            // Loss function
    public double _gamma = 0;                     // Regularization weight
    public final Regularizer _regularization = Regularizer.L2;   // Regularization function
    public long _seed = System.nanoTime(); // RNG seed
    public GLRM.Initialization _init = GLRM.Initialization.PlusPlus;
    public Key<Frame> _user_points;
    public Key<Frame> _loading_key;

    public enum Loss {
      L2, L1, Huber, Poisson, Hinge, Logistic
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
          return Math.signum(u-a);
        case Huber:
          return Math.abs(u-a) <= 1 ? u-a : Math.signum(u-a);
        case Poisson:
          return Math.exp(u) - a;
        case Hinge:
          return a*u <= 1 ? -a : 0;
        case Logistic:
          return -a/(1+Math.exp(a*u));
        default:
          throw new RuntimeException("Unknown loss function " + _loss);
      }
    }

    // \prox_{\alpha_k*r}(u): Proximal gradient of (step size) * (regularization function) evaluated at u
    public final double rproxgrad(double u, double alpha) {
      switch(_regularization) {
        case L2:
          return u/(1+2*alpha*_gamma);
        case L1:
          return Math.max(u-alpha*_gamma,0) + Math.min(u+alpha*_gamma,0);
        default:
          throw new RuntimeException("Unknown regularization function " + _regularization);
      }
    }
  }

  public static class GLRMOutput extends Model.Output {
    // Iterations executed
    public int _iterations;

    // Average change in objective function this iteration
    public double _avg_change_obj;

    // Mapping from training data to lower dimensional k-space (Y)
    public double[][] _archetypes;

    // Model parameters
    GLRMParameters _parameters;

    // If standardized, mean of each numeric data column
    public double[] _normSub;

    // If standardized, one over standard deviation of each numeric data column
    public double[] _normMul;

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
      super(model, frame);
    }

    // GLRM currently does not have any model metrics to compute during scoring
    public static class GLRMModelMetrics extends MetricBuilderUnsupervised {
      public GLRMModelMetrics(int dims) {
        _work = new double[dims];
      }

      @Override
      public double[] perRow(double[] dataRow, float[] preds, Model m, int row) { return dataRow; }

      @Override
      public ModelMetrics makeModelMetrics(Model m, Frame f, double sigma) {
        return m._output.addModelMetrics(new ModelMetricsGLRM(m, f));
      }
    }
  }
}
