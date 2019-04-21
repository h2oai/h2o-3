package ai.h2o.automl;

public interface TimedH2ORunnable extends H2ORunnable {
  boolean keepRunning();
  long timeRemainingMs();
}
