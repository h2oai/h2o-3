package ai.h2o.automl;

import water.H2O;
import water.Job;
import water.Key;


public class H2OJob {

  protected final H2ORunnable _target;
  protected final Key _key;
  protected Key<Job> _jobKey;

  public H2OJob(H2ORunnable runnable) {
    _target=runnable;
    _key=Key.make();
  }
  public H2OJob(H2ORunnable runnable, Key k) {
    _target=runnable;
    _key=k;
  }

  public Job start() {
    Job j = new Job<>(_key,_target.getClass().getName(), _target.getClass().getName());
    _jobKey=j._key;
    return j.start(new H2O.H2OCountedCompleter() {
      @Override public void compute2() {
        _target.run();
        tryComplete();
      }
    },-1);
  }
}
