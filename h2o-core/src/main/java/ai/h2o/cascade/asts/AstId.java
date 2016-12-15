package ai.h2o.cascade.asts;

/**
 * Identifier, will perform a name lookup in the local/session scopes.
 */
public class AstId extends Ast<AstId> {
  private String name;

  public AstId(String name) {
    this.name = name;
  }

  @Override
  public String str() {
    return name;
  }
}
