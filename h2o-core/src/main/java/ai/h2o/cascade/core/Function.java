package ai.h2o.cascade.core;

import ai.h2o.cascade.vals.Val;

/**
 */
public abstract class Function {
  public Scope scope;

  public abstract Val apply0(Val[] args);

}
