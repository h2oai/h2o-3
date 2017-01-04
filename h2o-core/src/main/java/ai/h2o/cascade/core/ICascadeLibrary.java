package ai.h2o.cascade.core;

import java.util.Map;

/**
 * Generic interface for a library in Cascade. Currently only one library
 * is implemented: {@link ai.h2o.cascade.stdlib.StandardLibrary}.
 */
public interface ICascadeLibrary {

  Map<String, Val> members();

}
