package ai.h2o.automl;

import jsr166y.CountedCompleter;
import water.H2O;
import water.Job;
import water.Key;
import water.util.Log;

public class TimedH2OJob extends H2OJob {

  public TimedH2OJob(TimedH2ORunnable runnable) {
    super(runnable);
  }
  public TimedH2OJob(TimedH2ORunnable runnable, Key key) {
    super(runnable, key);
  }

  public Job start() {
    Job j = super.start();
    startTimer();
    return j;
  }

  private void startTimer() { H2O.submitTask(new Timer(this)); }
  private class Timer extends H2O.H2OCountedCompleter<Timer> {
    final TimedH2OJob _j;
    Timer(TimedH2OJob j) {_j=j;}
    @Override public void compute2() {
      while( ((TimedH2ORunnable)_j._target).keepRunning()) {
        try {
          Thread.sleep(Math.min(10,((TimedH2ORunnable)_j._target).timeRemaining()));
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
      tryComplete();
    }
    @Override public void onCompletion(CountedCompleter cc) {
      Log.info("Stopping Timed H2OJob");
      _j._target.stop();
      Job j = _j._jobKey.get();
      j.update(1);
      j.stop();
    }
    @Override public boolean onExceptionalCompletion(Throwable t, CountedCompleter cc) {
      Log.info("Timed H2OJob finished exceptionally");
      _j._target.stop();
      _j._jobKey.get().stop();
      t.printStackTrace();
      return false;
    }
  }
}
