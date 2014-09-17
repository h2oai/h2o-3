package water.api;

import hex.Model;
import hex.ModelMetrics;

/**
 * Base Schema for individual instances of ModelMetrics objects.
 */
public abstract class ModelMetricsBase extends Schema<ModelMetrics, ModelMetricsBase> {
  // InOut fields
  @API(help="The unique ID (key / uuid / creation timestamp) for the model used for this scoring run.")
  public UniqueIdBase model;

  @API(help="The unique ID (key / uuid / creation timestamp) for the frame used for this scoring run.")
  public UniqueIdBase frame;

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
                                      this.model_category,
                                      this.frame.createImpl(),
                                      this.duration_in_ms,
                                      this.scoring_time,
                                      this.auc.createImpl(),
                                      null
                                      /* TODO: choose either ConfusionMatrix or ConfusionMatrix2! this.cm.createImpl()*/);
    return m;
  }

  @Override public ModelMetricsBase fillFromImpl(ModelMetrics m) {
    // TODO: this is failing in BeanUtils with an IllegalAccessException.  Why?  Different class loaders?
    // BeanUtils.copyProperties(this, m, BeanUtils.FieldNaming.CONSISTENT);

    // Shouldn't need to do this manually. . .
    this.model = new UniqueIdV3().fillFromImpl(m.model); // TODO: shouldn't have hardwired version
    this.frame = new UniqueIdV3().fillFromImpl(m.frame);
    this.model_category = m.model_category;
    this.duration_in_ms = m.duration_in_ms;
    this.scoring_time = m.scoring_time;
    this.auc = new AUCV3().fillFromImpl(m.auc); // TODO: shouldn't be version-specific

    return this;
  }
}
