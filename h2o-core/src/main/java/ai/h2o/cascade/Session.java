package ai.h2o.cascade;

import water.Key;

import java.io.Closeable;

/**
 * Session is a long-lasting environment...
 */
public class Session implements Closeable {
  private String user;
  private String session_id;

  /**
   * Create a new session object.
   */
  public Session(String username) {
    user = username;
    session_id = Key.make().toString().substring(0, 6);
  }

  public String id() {
    return session_id;
  }

  public String user() {
    return user;
  }


  @Override
  public void close() {

  }
}
