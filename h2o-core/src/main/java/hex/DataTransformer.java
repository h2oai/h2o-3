package hex;

import water.Key;
import water.Keyed;
import water.fvec.Frame;

/**
 * Abstraction 
 * @param <T>
 */
public abstract class DataTransformer<T extends DataTransformer> extends Keyed<T> {

  public enum Stage {
    Training,
    Validation,
    Scoring
  }
  
  public DataTransformer() {
    super();
  }

  public DataTransformer(Key<T> key) {
    super(key);
  }

  public abstract Frame transform(Frame fr, Model.Parameters params, Stage stage);
  
  public abstract Model asModel();
}
