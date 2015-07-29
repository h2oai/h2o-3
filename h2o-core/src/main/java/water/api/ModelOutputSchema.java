package water.api;

import hex.Model;
import hex.ModelCategory;
import water.Weaver;
import water.util.IcedHashMap;
import water.util.Log;

import java.lang.reflect.Field;

/**
 * An instance of a ModelOutput schema contains the Model build output (e.g., the cluster centers for KMeans).
 * NOTE: use subclasses, not this class directly.  It is not abstract only so that we can instantiate it to generate metadata
 * for it for the metadata API.
 */
public class ModelOutputSchema<O extends Model.Output, S extends ModelOutputSchema<O, S>> extends Schema<O, S> {

  @API(help="Column names", direction=API.Direction.OUTPUT)
  public String[] names;

  @API(help="Domains for categorical (enum) columns", direction=API.Direction.OUTPUT, level=API.Level.expert)
  public String[][] domains;

  @API(help="Cross-validation models (model ids)", direction=API.Direction.OUTPUT, level=API.Level.expert)
  public KeyV3.ModelKeyV3[] cross_validation_models;

  @API(help="Cross-validation predictions (frame ids)", direction=API.Direction.OUTPUT, level=API.Level.expert)
  public KeyV3.FrameKeyV3[] cross_validation_predictions;

  @API(help="Category of the model (e.g., Binomial)", values={"Unknown", "Binomial", "Multinomial", "Regression", "Clustering", "AutoEncoder", "DimReduction"}, direction=API.Direction.OUTPUT)
  public ModelCategory model_category;

  @API(help="Model summary", direction=API.Direction.OUTPUT, level=API.Level.critical)
  TwoDimTableBase model_summary;

  @API(help="Scoring history", direction=API.Direction.OUTPUT, level=API.Level.secondary)
  TwoDimTableBase scoring_history;

  @API(help="Training data model metrics", direction=API.Direction.OUTPUT, level=API.Level.critical)
  ModelMetricsBase training_metrics;

  @API(help="Validation data model metrics", direction=API.Direction.OUTPUT, level=API.Level.critical)
  ModelMetricsBase validation_metrics;

  @API(help="Cross-validation model metrics", direction=API.Direction.OUTPUT, level=API.Level.critical)
  ModelMetricsBase cross_validation_metrics;

  @API(help="Job status", direction=API.Direction.OUTPUT, level=API.Level.secondary)
  public String state;

  @API(help="Start time in milliseconds", direction=API.Direction.OUTPUT, level=API.Level.secondary)
  public long start_time;

  @API(help="End time in milliseconds", direction=API.Direction.OUTPUT, level=API.Level.secondary)
  public long end_time;

  @API(help="Runtime in milliseconds", direction=API.Direction.OUTPUT, level=API.Level.secondary)
  public long msec;

  @API(help="Help information for output fields", direction=API.Direction.OUTPUT)
  public IcedHashMap.IcedHashMapStringString help;

  public ModelOutputSchema() {
    super();
  }

  public S fillFromImpl( O impl ) {
    super.fillFromImpl(impl);
    this.model_category = impl.getModelCategory();
    fillHelp();
    return (S)this;
  }

  private void fillHelp() {
    this.help = new IcedHashMap.IcedHashMapStringString();
    try {
      Field[] dest_fields = Weaver.getWovenFields(this.getClass());
      for (Field f : dest_fields) {
        fillHelp(f);
      }
    }
    catch (Exception e) {
      Log.warn(e);
    }
  }

  private void fillHelp(Field f) {
    API annotation = f.getAnnotation(API.class);

    if (null != annotation) {
      String helpString = annotation.help();
      if (helpString == null) {
        return;
      }

      String name = f.getName();
      if (name == null) {
        return;
      }

      this.help.put(name, helpString);
    }
  }
}
