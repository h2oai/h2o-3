package ai.h2o.cascade;

import ai.h2o.cascade.core.Scope;
import ai.h2o.cascade.stdlib.StandardLibrary;
import water.Key;

import java.io.Closeable;

/**
 * Session is a long-lasting environment...
 */
public class CascadeSession implements Closeable {
  private String user;
  private String session_id;
  private Scope global;

  /**
   * Create a new session object.
   */
  public CascadeSession(String username) {
    user = username;
    session_id = Key.make().toString().substring(1, 7);
    global = new Scope(null);
    global.importFromLibrary(StandardLibrary.instance());
  }

  public String id() {
    return session_id;
  }

  public String user() {
    return user;
  }

  public Scope globalScope() {
    return global;
  }


  @Override
  public void close() {

  }
}
