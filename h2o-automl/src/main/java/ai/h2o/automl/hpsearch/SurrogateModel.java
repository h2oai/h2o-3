package ai.h2o.automl.hpsearch;

import water.fvec.Frame;

public abstract class SurrogateModel {
  public abstract Frame evaluate(Frame hyperparameters, Frame train);
}
