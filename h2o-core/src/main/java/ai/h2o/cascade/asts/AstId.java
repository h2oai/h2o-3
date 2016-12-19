package ai.h2o.cascade.asts;

import ai.h2o.cascade.CascadeScope;
import ai.h2o.cascade.vals.Val;

/**
 * Identifier, will perform a name lookup in the local/session scopes.
 */
public class AstId extends Ast<AstId> {
  private String name;

  public AstId(String name) {
    this.name = name;
  }

  @Override
  public Val exec(CascadeScope scope) {
    return scope.lookup(name);
  }

  @Override
  public String str() {
    return name;
  }
}
