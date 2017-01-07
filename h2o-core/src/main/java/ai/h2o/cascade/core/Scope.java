package ai.h2o.cascade.core;

import ai.h2o.cascade.CascadeSession;
import water.Key;
import water.Keyed;

import java.util.HashMap;
import java.util.Map;

/**
 * Lexical scope for the Cascade execution environment.
 *
 * <p>Each scope contains a symbol table (an id->value map), and a reference
 * to the parent scope. The job of a scope is to keep track of what variables
 * are currently defined, and what their values are. If some variable is not
 * found in the current scope, we will search the parent scope.
 *
 * <p>Cascade has a single global scope bound to a session. Additionally,
 * new scopes may be opened by certain commands, such as {@code for},
 * {@code def} and {@code scope}.
 *
 */
public class Scope {
  private Scope parent;
  private CascadeSession session;
  private Map<String, Val> symbolTable;


  public Scope(CascadeSession sess, Scope parentScope) {
    session = sess;
    parent = parentScope;
    symbolTable = new HashMap<>(5);
  }


  public CascadeSession session() {
    return session;
  }


  public Val lookupVariable(String id) {
    if (symbolTable.containsKey(id))
      return symbolTable.get(id);
    if (parent != null)
      return parent.lookupVariable(id);
    return null;
  }


  public void importFromLibrary(ICascadeLibrary lib) {
    symbolTable.putAll(lib.members());
  }


  public void addVariable(String name, Val value) {
    symbolTable.put(name, value);
  }


  /**
   * Issue a new {@link Key} that can be used for storing an object in the DKV.
   * The key will be prefixed with a session id, making it recognizable as
   * being owned by this session.
   *
   * @param <T> type of the {@code Key} to create.
   */
  public <T extends Keyed<T>> Key<T> mintKey() {
    return session.mintKey();
  }


}
