package hex.glrm;

import hex.Model;
import hex.ModelMetrics;
import water.H2O;
import water.Key;
import water.api.ModelSchema;
import hex.schemas.GLRMModelV2;

public class GLRMModel extends Model<GLRMModel,GLRMModel.GLRMParameters,GLRMModel.GLRMOutput> {

  public static class GLRMParameters extends Model.Parameters {
    public int _num_pc = 1;                // Number of principal components
    public double _gamma = 0;
    public int _max_iterations = 1000;     // Max iterations
    public double _tolerance = 0;
    public boolean _standardize = true;
  }

  public static class GLRMOutput extends Model.Output {
    // Iterations executed
    public int _iterations;

    // Average change (l2) in X and Y
    public double _avg_change;

    //Rank of eigenvector matrix
    public int _rank;

    //Standard deviation of each principal component
    public double[] _sdev;

    //Proportion of variance explained by each principal component
    public double[] _propVar;

    //Cumulative proportion of variance explained by each principal component
    public double[] _cumVar;

    //Principal components (eigenvector) matrix
    public double[][] _eigVec;

    //If standardized, mean of each numeric data column
    public double[] _normSub;

    //If standardized, one over standard deviation of each numeric data column
    public double[] _normMul;

    //@API(help = "Model parameters")
    GLRMParameters _parameters;

    public GLRMOutput( GLRM b ) { super(b); }

    /** Override because base class implements ncols-1 for features with the
     *  last column as a response variable; for PCA all the columns are
     *  features. */
    @Override public int nfeatures() { return _names.length; }

    @Override public ModelCategory getModelCategory() {
      return ModelCategory.DimReduction;
    }
  }

  public GLRMModel(Key selfKey, GLRMParameters parms, GLRMOutput output) { super(selfKey,parms,output); }

  @Override
  public boolean isSupervised() { return false; }

  @Override
  public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    throw H2O.unimpl("No model metrics for GLRM.");
  }

  @Override
  public ModelSchema schema() {
    return new GLRMModelV2();
  }

  @Override
  protected float[] score0(double data[/*ncols*/], float preds[/*nclasses+1*/]) {
    throw new RuntimeException("TODO Auto-generated method stub");
  }
}
