package ai.h2o.cascade.asts;

import ai.h2o.cascade.vals.Val;
import water.Iced;

/**
 * Base class for all AST nodes in the Cascade language.
 */
public abstract class Ast<T extends Ast<T>> extends Iced<T> {

  public abstract String str();

  public Val exec() { return null; }  // should be abstract

  /**
   * Override of the standard {@link Object#toString()}, used primarily for
   * the debugging output.
   */
  public final String toString() {
    return str();
  }
}
