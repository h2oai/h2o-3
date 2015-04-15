package hex.svd;

import hex.DataInfo;
import hex.Model;
import hex.ModelMetrics;
import water.H2O;
import water.Key;

public class SVDModel extends Model<SVDModel,SVDModel.SVDParameters,SVDModel.SVDOutput> {
  public static class SVDParameters extends Model.Parameters {
    public int _k = 1;    // Number of singular vectors to calculate
    public int _max_iterations = 1000;    // Maximum number of iterations
    public long _seed = System.nanoTime();        // RNG seed
    public DataInfo.TransformType _transform = DataInfo.TransformType.NONE; // Data transformation (demean to compare with PCA)
  }

  public static class SVDOutput extends Model.Output {
    // Right singular vectors (V)
    public double[][] _v;

    // Singular values (diagonal of S)
    public double[] _singular_vals;

    // If standardized, mean of each numeric data column
    public double[] _normSub;

    // If standardized, one over standard deviation of each numeric data column
    public double[] _normMul;

    public SVDOutput(SVD b) { super(b); }

    @Override public ModelCategory getModelCategory() { return Model.ModelCategory.DimReduction; }
  }

  public SVDModel(Key selfKey, SVDParameters parms, SVDOutput output) { super(selfKey,parms,output); }

  @Override public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    throw H2O.unimpl("SVDModel does not have Model Metrics.");
  }

  @Override protected double[] score0(double data[/*ncols*/], double preds[/*nclasses+1*/]) {
    throw H2O.unimpl();
  }
}
