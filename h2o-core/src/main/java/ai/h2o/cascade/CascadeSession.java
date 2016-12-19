package ai.h2o.cascade;

import water.Key;

import java.io.Closeable;

/**
 * Session is a long-lasting environment...
 */
public class CascadeSession implements Closeable {
  private String user;
  private String session_id;
  private CascadeScope global;

  /**
   * Create a new session object.
   */
  public CascadeSession(String username) {
    user = username;
    session_id = Key.make().toString().substring(1, 7);
    global = new CascadeScope(null);
  }

  public String id() {
    return session_id;
  }

  public String user() {
    return user;
  }

  public CascadeScope globalScope() {
    return global;
  }


  @Override
  public void close() {

  }
}
