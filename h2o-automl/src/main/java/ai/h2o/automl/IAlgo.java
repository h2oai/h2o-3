package ai.h2o.automl;

import java.io.Serializable;

public interface IAlgo extends Serializable {
  String name();
  default String urlName() { return name().toLowerCase(); }
  default boolean enabled() { return true; }
}
