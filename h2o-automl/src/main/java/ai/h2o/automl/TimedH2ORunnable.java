package ai.h2o.automl;

public interface TimedH2ORunnable extends H2ORunnable {
  public boolean keepRunning();
  public long timeRemainingMs();
}
