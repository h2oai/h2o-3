package ai.h2o.cascade.asts;

import ai.h2o.cascade.Cascade;
import ai.h2o.cascade.core.Scope;
import ai.h2o.cascade.core.Val;

/**
 * Identifier, will perform a name lookup in the local/session scopes.
 */
public class AstId extends AstNode<AstId> {
  private String name;

  public AstId(String name) {
    this.name = name;
  }

  @Override
  public Val exec(Scope scope) {
    Val result = scope.lookupVariable(name);
    if (result == null) {
      throw new Cascade.NameError(start, length, "Name lookup of " + name + " failed");
    }
    return result;
  }

  @Override
  public String str() {
    return name;
  }
}
