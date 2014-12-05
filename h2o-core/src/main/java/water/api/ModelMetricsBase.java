package water.api;

import hex.AUCData;
import hex.Model;
import hex.ModelMetrics;
import water.DKV;
import water.H2O;
import water.fvec.Frame;
import water.util.PojoUtils;

/**
 * Base Schema for individual instances of ModelMetrics objects.
 */
public abstract class ModelMetricsBase extends Schema<ModelMetrics, ModelMetricsBase> {
  // InOut fields
  @API(help="The model used for this scoring run.", direction=API.Direction.INOUT)
  public ModelSchema model;

  @API(help="The checksum for the model used for this scoring run.", direction=API.Direction.INOUT)
  public long model_checksum;

  @API(help="The frame used for this scoring run.", direction=API.Direction.INOUT)
  public FrameV2 frame; // TODO: should use a base class!

  @API(help="The checksum for the frame used for this scoring run.", direction=API.Direction.INOUT)
  public long frame_checksum;

  // Output fields
  @API(help="The category (e.g., Clustering) for the model used for this scoring run.", values={"Unknown", "Binomial", "Multinomial", "Regression", "Clustering"}, direction=API.Direction.OUTPUT)
  public Model.ModelCategory model_category ;

  @API(help="The duration in mS for this scoring run.", direction=API.Direction.OUTPUT)
  public long duration_in_ms;

  @API(help="The time in mS since the epoch for the start of this scoring run.", direction=API.Direction.OUTPUT)
  public long scoring_time;

  @API(help="The AUC object for this scoring run.", direction=API.Direction.OUTPUT)
  public AUCBase auc;

  @API(help="The ConfusionMatrix object for this scoring run.", direction=API.Direction.OUTPUT)
  public ConfusionMatrixBase cm;

  @API(help="Predictions Frame.", direction=API.Direction.OUTPUT)
  public FrameV2 predictions;

  // Non-version-specific filling into the impl
  @Override public ModelMetrics createImpl() {
    ModelMetrics m = new ModelMetrics((Model)this.model.createImpl(), this.frame.createImpl()); // TODO: why does the model need a cast but not the frame?
    return m;
  }

  public ModelMetrics fillImpl(ModelMetrics m) {
    PojoUtils.copyProperties(m, this, PojoUtils.FieldNaming.CONSISTENT, new String[] {"auc", "cm"});
    //m._aucdata = (AUCData)this.auc.createImpl();
    /*
     * m._cm = this.cm.createImpl();
     */
    return m;
  }

  @Override public ModelMetricsBase fillFromImpl(ModelMetrics modelMetrics) {
    // If we're copying in a Model we need a ModelSchema of the right class to fill into.
    Model m = modelMetrics.model();
    if( m != null )
      this.model = (ModelSchema)Schema.schema(this.schema_version, m.getClass());

    // If we're copying in a Frame we need a Frame Schema of the right class to fill into.
    if (null != modelMetrics.frame())
      this.frame = new FrameV2().fillFromImpl(modelMetrics.frame());

    // TODO: remove this hack:
    new AUCV3();
    new ConfusionMatrixV3();
    //modelMetrics._cm = null; // still have to decide between CM and CM2


    if (null != modelMetrics._aucdata)
      this.auc = (AUCBase)Schema.schema(this.schema_version, modelMetrics._aucdata);

    if (null != modelMetrics._cm)
      this.cm = (ConfusionMatrixBase)Schema.schema(this.schema_version, modelMetrics._cm);

    super.fillFromImpl(modelMetrics);

    // TODO: Schema.fillFromImpl really ought to be able to do this.
    if( m != null )
      if (Model.class.isAssignableFrom(m.getClass()))
        this.model.fillFromImpl(m);
      else
        throw H2O.fail("Can't fill a model schema from a non-Model key" );

    if( f != null )
      if (Frame.class.isAssignableFrom(f.getClass()))
        this.frame.fillFromImpl(f);
      else
        throw H2O.fail("Can't fill a frame schema from a non-Frame key");

    // For the Model we want the key and the parameters, but no output.
    if (null != this.model)
      this.model.output = null;

    // TODO: need to have only one Iced CM class. . .
    // this._cm = new ConfusionMatrixV3().fillFromImpl(modelMetrics.cm);  // TODO: shouldn't be version-specific

    return this;
  }
}
