package water;

import jsr166y.CountedCompleter;
import jsr166y.ForkJoinPool;
import water.util.Log;

/**  A Distributed DTask.
 * Execute a set of Keys on the home for each Key.
 * Limited to doing a map/reduce style.
 */
public abstract class DRemoteTask<T extends DRemoteTask> extends DTask<T> implements ForkJoinPool.ManagedBlocker {
}
