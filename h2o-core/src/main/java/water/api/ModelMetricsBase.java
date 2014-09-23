package water.api;

import hex.Model;
import hex.ModelMetrics;
import water.fvec.Frame;

/**
 * Base Schema for individual instances of ModelMetrics objects.
 */
public abstract class ModelMetricsBase extends Schema<ModelMetrics, ModelMetricsBase> {
  // InOut fields
  @API(help="The model used for this scoring run.")
  public ModelSchema model;

  @API(help="The checksum for the model used for this scoring run.")
  public long model_checksum;

  @API(help="The frame used for this scoring run.")
  public FrameV2 frame; // TODO: should use a base class!

  @API(help="The checksum for the frame used for this scoring run.")
  public long frame_checksum;

  // Output fields
  @API(help="The category (e.g., Clustering) for the model used for this scoring run.")
  public Model.ModelCategory model_category ;

  @API(help="The duration in mS for this scoring run.")
  public long duration_in_ms;

  @API(help="The time in mS since the epoch for the start of this scoring run.")
  public long scoring_time;

  @API(help="The AUC object for this scoring run.")
  public AUCBase auc;

  @API(help="The ConfusionMatrix object for this scoring run.")
  public ConfusionMatrixBase cm;

  // Non-version-specific filling into the impl
  @Override public ModelMetrics createImpl() {
    ModelMetrics m = new ModelMetrics(this.model.createImpl(),
                                      this.frame.createImpl(),
                                      this.duration_in_ms,
                                      this.scoring_time,
                                      this.auc.createImpl(),
                                      null
                                      /* TODO: choose either ConfusionMatrix or ConfusionMatrix2! this.cm.createImpl()*/);
    return m;
  }

  @Override public ModelMetricsBase fillFromImpl(ModelMetrics modelMetrics) {
    // TODO: this is failing in PojoUtils with an IllegalAccessException.  Why?  Different class loaders?
    // PojoUtils.copyProperties(this, m, PojoUtils.FieldNaming.CONSISTENT);

    // Shouldn't need to do this manually. . .
    Model model = modelMetrics.model.get();
    this.model = model.schema().fillFromImpl(model);
    this.model_checksum = modelMetrics.model_checksum;
    // TODO: remove fillFromImpl once it is refactored together with the constructor (see Frame.java)
    this.frame = new FrameV2((Frame) modelMetrics.frame.get()).fillFromImpl((Frame) modelMetrics.frame.get());
    this.frame_checksum = modelMetrics.frame_checksum;
    this.model_category = modelMetrics.model_category;
    this.duration_in_ms = modelMetrics.duration_in_ms;
    this.scoring_time = modelMetrics.scoring_time;
    this.auc = new AUCV3().fillFromImpl(modelMetrics.auc); // TODO: shouldn't be version-specific

    return this;
  }
}
