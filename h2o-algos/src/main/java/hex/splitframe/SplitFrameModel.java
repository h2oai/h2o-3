package hex.splitframe;

import hex.Model;
import hex.ModelMetrics;
import hex.schemas.SplitFrameModelV2;
import water.H2O;
import water.Key;
import water.api.ModelSchema;
import water.fvec.Frame;

public class SplitFrameModel extends Model<SplitFrameModel,SplitFrameModel.SplitFrameParameters,SplitFrameModel.SplitFrameOutput> {

  public static class SplitFrameParameters extends Model.Parameters {
    /** Split ratios - resulting number of split is ratios.length+1 */
    public double[] _ratios = new double[]{0.5};
    /** Destination keys for each output frame split. */
    public Key[] _destKeys;
  }

  public static class SplitFrameOutput extends Model.Output {
    /** Output frames for each output split part */
    public Frame[] _splits;
    public SplitFrameOutput( SplitFrame b ) { super(b); }
    @Override public ModelCategory getModelCategory() { return Model.ModelCategory.Unknown; }
  }

  SplitFrameModel( Key selfKey, SplitFrameParameters parms, SplitFrameOutput output) { super(selfKey,parms,output); }

  @Override
  public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    throw H2O.unimpl("No Model Metrics for SplitFrame");
  }

  // Default publically visible Schema is V2
  @Override public ModelSchema schema() { return new SplitFrameModelV2(); }

  @Override protected float[] score0(double data[/*ncols*/], float preds[/*nclasses+1*/]) {
    throw H2O.unimpl();
  }

}
