package ai.h2o.cascade;

import ai.h2o.cascade.core.Scope;
import ai.h2o.cascade.stdlib.StandardLibrary;
import water.Key;
import water.Keyed;
import water.fvec.Vec;

import java.io.Closeable;
import java.util.HashMap;
import java.util.Map;

/**
 * A session is a Cascade execution environment that spans multiple REST API
 * calls. The job of a session is to keep around the variables defined by the
 * user in any previous API call. Thus, a session is essentially a global
 * {@link Scope} with an attached {@code session_id}.
 * <p>
 * A session also carries a user name, which is an arbitrary user-supplied
 * string whose purpose is to help the user find their session after they
 * were disconnected.
 *
 * <hr/>
 *
 * In addition to that, the session also keeps track of {@link Vec}
 *
 */
public class CascadeSession implements Closeable {
  private String user;
  private String session_id;
  private Scope global;
  private Map<Vec, Integer> vecCopyCounts;
  private int idCounter;


  /**
   * Create a new session object.
   */
  public CascadeSession(String username) {
    idCounter = 0;
    user = username;
    session_id = Key.make().toString().substring(1, 7);
    global = new Scope(this, null);
    global.importFromLibrary(StandardLibrary.instance());
    vecCopyCounts = new HashMap<>(64);
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


  /**
   * Issue a new {@link Key} that can be used for storing an object in the DKV.
   * The key will be prefixed with a session id, making it recognizable as
   * being owned by this session.
   * @param <T> type of the {@code Key} to create.
   */
  public <T extends Keyed<T>> Key<T> mintKey() {
    return Key.make(session_id + "~cc" + (idCounter++));
  }


  @Override
  public void close() {

  }


  //--------------------------------------------------------------------------------------------------------------------
  //
  //--------------------------------------------------------------------------------------------------------------------


}
