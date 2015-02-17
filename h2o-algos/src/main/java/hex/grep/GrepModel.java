package hex.grep;

import hex.Model;
import hex.ModelMetrics;
import water.H2O;
import water.Key;

public class GrepModel extends Model<GrepModel,GrepModel.GrepParameters,GrepModel.GrepOutput> {

  public static class GrepParameters extends Model.Parameters {
    public String _regex;       // The regex
  }

  public static class GrepOutput extends Model.Output {
    // Iterations executed
    public String[] _matches;
    public long[] _offsets;
    public GrepOutput( Grep b ) { super(b); }
    @Override public ModelCategory getModelCategory() { return Model.ModelCategory.Unknown; }
  }

  GrepModel( Key selfKey, GrepParameters parms, GrepOutput output) { super(selfKey,parms,output); }

  @Override
  public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    throw H2O.unimpl("GrepModel does not have Model Metrics.");
  }

  @Override protected float[] score0(double data[/*ncols*/], float preds[/*nclasses+1*/]) {
    throw H2O.unimpl();
  }

}
