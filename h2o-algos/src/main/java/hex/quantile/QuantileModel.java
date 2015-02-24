package hex.quantile;

import hex.Model;
import hex.ModelMetrics;
import hex.schemas.QuantileModelV2;
import water.H2O;
import water.Key;
import water.api.ModelSchema;

public class QuantileModel extends Model<QuantileModel,QuantileModel.QuantileParameters,QuantileModel.QuantileOutput> {

  public static class QuantileParameters extends Model.Parameters {
    // Set of probabilities to compute
    public double _probs[/*Q*/] = new double[]{0.01,0.05,0.10,0.25,0.333,0.50,0.667,0.75,0.90,0.95,0.99};
    @Override protected boolean defaultDropConsCols() { return false; }
  }

  public static class QuantileOutput extends Model.Output {
    public int _iterations;        // Iterations executed
    public double _quantiles[/*N*/][/*Q*/]; // Our N columns, Q quantiles reported
    public QuantileOutput( Quantile b ) { super(b); }
    @Override public ModelCategory getModelCategory() { return Model.ModelCategory.Unknown; }
  }

  QuantileModel( Key selfKey, QuantileParameters parms, QuantileOutput output) { super(selfKey,parms,output); }

  @Override
  public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    throw H2O.unimpl("No model metrics for Quantile.");
  }

  @Override protected float[] score0(double data[/*ncols*/], float preds[/*nclasses+1*/]) {
    throw H2O.unimpl();
  }

}
