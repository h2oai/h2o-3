package ai.h2o.automl;

/**
 *
 * The <code>H2ORunnable</code> interface should be implemented by any class whose
 * instances are intended to be submitted to the H2O forkjoin pool via
 * <code>H2O.submitTask</code>. The class must define a method of no arguments called
 * <code>run</code>.
 *
 */
public interface H2ORunnable {
  void run();
  void stop();
}

