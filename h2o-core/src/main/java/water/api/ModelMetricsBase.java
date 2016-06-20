package water.api;

import hex.Model;
import hex.ModelCategory;
import hex.ModelMetrics;
import water.api.KeyV3.FrameKeyV3;
import water.api.KeyV3.ModelKeyV3;
import water.fvec.Frame;
import water.util.PojoUtils;

/**
 * Base Schema for individual instances of ModelMetrics objects.  Note: this class should not be used directly.
 */
public class ModelMetricsBase<I extends ModelMetrics, S extends ModelMetricsBase<I, S>> extends SchemaV3<I, S> {
  // InOut fields
  @API(help="The model used for this scoring run.", direction=API.Direction.INOUT)
  public ModelKeyV3 model;

  @API(help="The checksum for the model used for this scoring run.", direction=API.Direction.INOUT)
  public long model_checksum;

  @API(help="The frame used for this scoring run.", direction=API.Direction.INOUT)
  public FrameKeyV3 frame;
  // public FrameV2 frame; // TODO: should use a base class!

  @API(help="The checksum for the frame used for this scoring run.", direction=API.Direction.INOUT)
  public long frame_checksum;

  // Output fields
  @API(help="Optional description for this scoring run (to note out-of-bag, sampled data, etc.)", direction=API.Direction.OUTPUT)
  public String description;

  @API(help="The category (e.g., Clustering) for the model used for this scoring run.", values={"Unknown", "Binomial", "Multinomial", "Regression", "Clustering"}, direction=API.Direction.OUTPUT)
  public ModelCategory model_category;

//  @API(help="The duration in mS for this scoring run.", direction=API.Direction.OUTPUT)
//  public long duration_in_ms;

  @API(help="The time in mS since the epoch for the start of this scoring run.", direction=API.Direction.OUTPUT)
  public long scoring_time;

  @API(help="Predictions Frame.", direction=API.Direction.OUTPUT)
  public FrameV3 predictions;

  @API(help = "The Mean Squared Error of the prediction for this scoring run.", direction = API.Direction.OUTPUT)
  public double MSE;

  @Override public S fillFromImpl(ModelMetrics modelMetrics) {
    // If we're copying in a Model we need a ModelSchema of the right class to fill into.
    Model m = modelMetrics.model();
    if( m != null ) {
      this.model = new ModelKeyV3(m._key);
      this.model_category = m._output.getModelCategory();
      this.model_checksum = m.checksum();
    }

    // If we're copying in a Frame we need a Frame Schema of the right class to fill into.
    Frame f = modelMetrics.frame();
    if (null != f) { //true == f.getClass().getSuperclass().getGenericSuperclass() instanceof ParameterizedType
      this.frame = new FrameKeyV3(f._key);
      this.frame_checksum = f.checksum();
    }

    PojoUtils.copyProperties(this, modelMetrics, PojoUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES,
            new String[]{"model", "model_category", "model_checksum", "frame", "frame_checksum"});


    return (S) this;
  }
}
