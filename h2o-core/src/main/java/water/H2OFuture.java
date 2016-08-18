package water;

import java.util.concurrent.Future;

/**
 * Created by tomas on 8/18/16.
 */
public interface H2OFuture<V> extends Future<V> {
  boolean isDoneExceptionally();
  Throwable getException();
}
