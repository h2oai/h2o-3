package ai.h2o.automl;

import water.H2O;
import water.Job;
import water.Key;


public class H2OJob {

  private final H2ORunnable _target;
  private final Key _key;
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
    return j.start(new H2O.H2OCountedCompleter() {
      @Override public void compute2() {
        _target.run();
      }
    },-1);
  }
}
