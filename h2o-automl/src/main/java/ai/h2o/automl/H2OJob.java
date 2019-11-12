package ai.h2o.automl;

import water.H2O;
import water.Job;
import water.Key;
import water.Keyed;


public class H2OJob<T extends Keyed & H2ORunnable> {

  protected final T _target;
  protected Key<Job> _jobKey;
  Job<T> _job;

  public H2OJob(T runnable, long max_runtime_msecs) {
    this(runnable, Key.make(), max_runtime_msecs);
  }

  public H2OJob(T runnable, Key<T> key, long max_runtime_msecs) {
    _target = runnable;
    _job = new Job<>(key, _target.getClass().getName(), _target.getClass().getSimpleName() + " build");
    _jobKey = _job._key;
    _job._max_runtime_msecs = max_runtime_msecs;
  }

  public Job<T> start() {
    return this.start(1);
  }

  public Job<T> start(int work) {
    return _job.start(new H2O.H2OCountedCompleter() {
      @Override public void compute2() {
        _target.run();
        tryComplete();
      }
    }, work);
  }

  public void stop() { _jobKey.get().stop(); }
}
