package ai.h2o.cascade.asts;

import ai.h2o.cascade.CascadeScope;
import ai.h2o.cascade.vals.Val;
import water.Iced;

/**
 * Base class for all AST nodes in the Cascade language.
 */
public abstract class Ast<T extends Ast<T>> extends Iced<T> {

  public abstract String str();

  /**
   * Execute the AST node within the provided {@code scope}.
   */
  public abstract Val exec(CascadeScope scope);

  /**
   * Override of the standard {@link Object#toString()}, used primarily for
   * the debugging output.
   */
  public final String toString() {
    return str();
  }
}
