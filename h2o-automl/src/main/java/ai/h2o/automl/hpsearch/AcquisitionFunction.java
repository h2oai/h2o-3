package ai.h2o.automl.hpsearch;

import water.fvec.Vec;

public abstract class AcquisitionFunction {
  public abstract boolean isIncumbentColdStartSetupHappened();
  public abstract void setIncumbent(double incumbent);
  public abstract void updateIncumbent(double possiblyNewIncumbent);
  public abstract Vec compute(Vec medians, Vec variances);
}

