package ai.h2o.automl;

import water.H2O;
import water.Job;
import water.Key;


public class H2OJob {

  protected final H2ORunnable _target;
  protected final Key _key;
  protected Key<Job> _jobKey;
  long _max_runtime_msecs;

  public H2OJob(H2ORunnable runnable, long max_runtime_msecs) {
    _target=runnable;
    _key=Key.make();
    _max_runtime_msecs = max_runtime_msecs;
  }
  public H2OJob(H2ORunnable runnable, Key k, long max_runtime_msecs) {
    _target=runnable;
    _key=k;
    _max_runtime_msecs = max_runtime_msecs;
  }

  public Job start() {
    Job j = new Job<>(_key,_target.getClass().getName(), _target.getClass().getSimpleName() + " build");
    j._max_runtime_msecs = _max_runtime_msecs;
    _jobKey=j._key;
    return j.start(new H2O.H2OCountedCompleter() {
      @Override public void compute2() {
        _target.run();
        tryComplete();
      }
    },1);
  }
  public void stop() { _jobKey.get().stop(); }
}
