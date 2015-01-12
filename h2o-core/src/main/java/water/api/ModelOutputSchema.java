package water.api;

import hex.Model;
import water.Weaver;
import water.util.IcedHashMap;
import water.util.Log;

import java.lang.reflect.Field;

/**
 * An instance of a ModelOutput schema contains the Model build output (e.g., the cluster centers for KMeans).
 */
abstract public class ModelOutputSchema<O extends Model.Output, S extends ModelOutputSchema<O, S>> extends Schema<O, S> {

  @API(help="Column names.", direction=API.Direction.OUTPUT)
  public String[] names;

  @API(help="Domains for categorical (enum) columns.", direction=API.Direction.OUTPUT)
  public String[][] domains;

  @API(help="Category of the model (e.g., Binomial).", values={"Unknown", "Binomial", "Multinomial", "Regression", "Clustering", "AutoEncoder", "DimReduction"}, direction=API.Direction.OUTPUT)
  public Model.ModelCategory model_category;

  @API(help="Help information for output fields", direction=API.Direction.OUTPUT)
  public IcedHashMap<String,String> help;

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
    this.help = new IcedHashMap<>();
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
