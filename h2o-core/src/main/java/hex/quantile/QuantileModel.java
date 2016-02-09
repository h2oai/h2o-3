package hex.quantile;

import hex.Model;
import hex.ModelCategory;
import hex.ModelMetrics;
import water.H2O;
import water.Key;

public class QuantileModel extends Model<QuantileModel,QuantileModel.QuantileParameters,QuantileModel.QuantileOutput> {

  public static enum CombineMethod { INTERPOLATE, AVERAGE, LOW, HIGH }
  public static class QuantileParameters extends Model.Parameters {
    // Set of probabilities to compute
    public double _probs[/*Q*/] = new double[]{0.001,0.01,0.1,0.25,0.333,0.50,0.667,0.75,0.9,0.99,0.999};
    public CombineMethod _combine_method = CombineMethod.INTERPOLATE;
    protected boolean defaultDropConsCols() { return false; }
    public String algoName() { return "Quantiles"; }
    public String fullName() { return "Quantiles"; }
    public String javaName() { return QuantileModel.class.getName(); }
    @Override public long progressUnits() { return train().numCols()*_probs.length; }
  }

  public static class QuantileOutput extends Model.Output {
    public QuantileParameters _parameters;   // Model parameters
    public int _iterations;        // Iterations executed
    public double _quantiles[/*N*/][/*Q*/]; // Our N columns, Q quantiles reported
    public QuantileOutput( Quantile b ) { super(b); }
    @Override public ModelCategory getModelCategory() { return ModelCategory.Unknown; }
  }

  QuantileModel( Key selfKey, QuantileParameters parms, QuantileOutput output) { super(selfKey,parms,output); }

  @Override
  public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    throw H2O.unimpl("No model metrics for Quantile.");
  }

  @Override protected double[] score0(double data[/*ncols*/], double preds[/*nclasses+1*/]) {
    throw H2O.unimpl();
  }
}
