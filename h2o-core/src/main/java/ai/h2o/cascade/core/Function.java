package ai.h2o.cascade.core;

import ai.h2o.cascade.vals.Val;

/**
 */
public abstract class Function {

  public abstract Val apply(Val[] args);

}
