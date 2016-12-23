package ai.h2o.cascade.asts;

import ai.h2o.cascade.core.Scope;
import ai.h2o.cascade.vals.Val;
import water.Iced;

/**
 * Base class for all AST nodes in the Cascade language.
 */
public abstract class Ast<T extends Ast<T>> extends Iced<T> {
  protected int start = -1;
  protected int length;


  public abstract String str();

  /**
   * Execute the AST node within the provided {@code scope}.
   */
  public abstract Val exec(Scope scope);

  /**
   * Override of the standard {@link Object#toString()}, used primarily for
   * the debugging output.
   */
  public final String toString() {
    return str();
  }

  /**
   * Save coordinates of this AST node, for subsequent error reporting.
   * @param start Position of the AST within the parsed cascade expression
   * @param end End position of the AST.
   */
  public final void setPos(int start, int end) {
    this.start = start;
    length = end - start;
  }
}
