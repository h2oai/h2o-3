package ai.h2o.cascade;

import ai.h2o.cascade.vals.Val;

import java.util.HashMap;
import java.util.Map;

/**
 * Lexical scope for the Cascade execution environment.
 *
 * <p>Each scope consists of an id->value map, and a reference to the parent
 * scope. The job of a scope is to keep track of what variables are currently
 * defined, and what their values are. If some variable is not found in the
 * current scope, we will search the parent scope.
 *
 * <p>Cascade has a single global scope bound to a session. Additionally,
 * new scopes may be opened by certain commands, such as {@code for} and
 * {@code def}.
 */
public class CascadeScope {
  private CascadeScope parent;
  private Map<String, Val> symbolTable;

  public CascadeScope(CascadeScope parentScope) {
    parent = parentScope;
    symbolTable = new HashMap<>(5);
  }

  public Val lookup(String id) {
    if (symbolTable.containsKey(id))
      return symbolTable.get(id);
    if (parent != null)
      return parent.lookup(id);
    throw new IllegalArgumentException("Name lookup of " + id + " failed");
  }

  public void importFrom(ICascadeLibrary lib) {
    symbolTable.putAll(lib.members());
  }

}
