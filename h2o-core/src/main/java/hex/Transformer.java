package hex;

import water.Iced;
import water.Job;
import water.Key;
import water.Keyed;

/**
 * Representation of transformation from type X to Y.
 *
 * Experimental API (to support nice Java/Scala API) and share common code with ModelBuilder.
 */
abstract public class Transformer<T extends Keyed> extends Iced {
  public final Job<T> _job;

  public Transformer(Key<T> dest, String clz_of_T, String desc) { _job = new Job(dest, clz_of_T, desc); }

  /** Execution endpoint for transformations. */
  public final Job<T> exec() { return execImpl(); }

  /** Implementation endpoint for transformations. */
  protected abstract Job<T> execImpl();
}
