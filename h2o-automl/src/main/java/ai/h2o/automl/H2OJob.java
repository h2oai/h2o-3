package ai.h2o.automl;

import water.H2O;
import water.Job;
import water.Key;


public class H2OJob {

  protected final H2ORunnable _target;
  protected Key<Job> _jobKey;
  Job _job;

  public H2OJob(H2ORunnable runnable, long max_runtime_msecs) {
    this(runnable, Key.make(), max_runtime_msecs);
  }

  public H2OJob(H2ORunnable runnable, Key key, long max_runtime_msecs) {
    _target = runnable;
    _job = new Job<>(key, _target.getClass().getName(), _target.getClass().getSimpleName() + " build");
    _jobKey = _job._key;
    _job._max_runtime_msecs = max_runtime_msecs;
  }

  public Job start() {
    return this.start(1);
  }

  public Job start(int work) {
    return _job.start(new H2O.H2OCountedCompleter() {
      @Override public void compute2() {
        _target.run();
        tryComplete();
      }
    }, work);
  }

  public void stop() { _jobKey.get().stop(); }
}
