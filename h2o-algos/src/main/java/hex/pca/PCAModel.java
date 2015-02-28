package hex.pca;

import hex.DataInfo;
import hex.Model;
import hex.ModelMetrics;
import water.H2O;
import water.Key;
import water.api.ModelSchema;
import hex.schemas.PCAModelV2;
import water.fvec.Frame;
import water.util.TwoDimTable;

public class PCAModel extends Model<PCAModel,PCAModel.PCAParameters,PCAModel.PCAOutput> {

  public static class PCAParameters extends Model.Parameters {
    public int _k = 1;                // Number of principal components
    public double _gamma = 0;         // Regularization
    public int _max_iterations = 1000;     // Max iterations
    public long _seed = System.nanoTime(); // RNG seed
    public DataInfo.TransformType _transform = DataInfo.TransformType.NONE; // Data transformation (demean to compare with PCA)
    public PCA.Initialization _init = PCA.Initialization.PlusPlus;
    public Key<Frame> _user_points;
    public Key<Frame> _loading_key;
  }

  public static class PCAOutput extends Model.Output {
    // Iterations executed
    public int _iterations;

    // Average change in objective function this iteration
    public double _avg_change_obj;

    // Final loading matrix (X)
    // public Frame _loadings;

    // Mapping from training data to lower dimensional k-space (Y)
    public double[][] _archetypes;

    // PCA output on XY
    // Principal components (eigenvectors) from SVD of XY
    public double[/*feature*/][/*k*/] _eigenvectors_raw;
    public TwoDimTable _eigenvectors;

    // Standard deviation of each principal component
    public double[] _std_deviation;

    // Importance of principal components
    // Standard deviation, proportion of variance explained, and cumulative proportion of variance explained
    public TwoDimTable _pc_importance;

    // Model parameters
    PCAParameters _parameters;

    // If standardized, mean of each numeric data column
    public double[] _normSub;

    // If standardized, one over standard deviation of each numeric data column
    public double[] _normMul;

    public PCAOutput(PCA b) { super(b); }

    /** Override because base class implements ncols-1 for features with the
     *  last column as a response variable; for PCA all the columns are
     *  features. */
    @Override public int nfeatures() { return _names.length; }

    @Override public ModelCategory getModelCategory() {
      return ModelCategory.DimReduction;
    }
  }

  public PCAModel(Key selfKey, PCAParameters parms, PCAOutput output) { super(selfKey,parms,output); }

  @Override
  public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    throw H2O.unimpl("No model metrics for PCA.");
  }

  public ModelSchema schema() {
    return new PCAModelV2();
  }

  @Override
  protected float[] score0(double data[/*ncols*/], float preds[/*nclasses+1*/]) {
    throw new RuntimeException("TODO Auto-generated method stub");
  }
}
