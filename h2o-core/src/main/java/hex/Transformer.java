package hex;

import water.Job;
import water.Key;
import water.Keyed;

/**
 * Representation of transformation from type X to Y.
 *
 * Experimental API (to support nice Java/Scala API) and share common code with ModelBuilder.
 */
abstract public class Transformer<T extends Keyed> extends Job<T> {

  public Transformer(Key<T> dest, String desc) { super(dest, desc); }

  /** Execution endpoint for transformations. */
  public <X extends Transformer<T>> X exec() {
    return execImpl();
  }

  /** Implementation endpoint for transformations. */
  protected abstract <X extends Transformer<T>> X execImpl();
}
