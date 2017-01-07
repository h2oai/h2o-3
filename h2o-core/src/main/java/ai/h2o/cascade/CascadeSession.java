package ai.h2o.cascade;

import ai.h2o.cascade.core.CorporealFrame;
import ai.h2o.cascade.core.Scope;
import ai.h2o.cascade.core.ValNull;
import ai.h2o.cascade.stdlib.StandardLibrary;
import water.DKV;
import water.Futures;
import water.Key;
import water.Keyed;
import water.fvec.Frame;
import water.fvec.Vec;

import java.io.Closeable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
  private int idCounter;
  private Map<Keyed, Integer> keyedRefCounts;
  private Set<CorporealFrame> corporealFrameRegistry;
  private Map<Vec, Integer> vecCopyCounts;


  /**
   * Create a new session object.
   */
  public CascadeSession(String username) {
    idCounter = 0;
    user = username;
    session_id = Key.make().toString().substring(1, 7);
    global = new Scope(this, null);
    StandardLibrary.importAll(global);
    global.addVariable("_", new ValNull());
    vecCopyCounts = new HashMap<>(64);
    keyedRefCounts = new HashMap<>(16);
    corporealFrameRegistry = new HashSet<>(16);
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
   *
   * @param <T> type of the {@code Key} to create.
   */
  public <T extends Keyed<T>> Key<T> mintKey() {
    return Key.make(session_id + "~cc" + (idCounter++));
  }


  @Override
  public void close() {

  }


  //--------------------------------------------------------------------------------------------------------------------
  // Keyed Ref Counts (KRC)
  //--------------------------------------------------------------------------------------------------------------------

  public void increaseRefCount(Keyed keyed) {
    Integer currentCount = keyedRefCounts.get(keyed);
    if (currentCount == null) currentCount = 0;
    keyedRefCounts.put(keyed, currentCount + 1);
  }

  public void decreaseRefCount(Keyed keyed) {
    Integer currentCount = keyedRefCounts.get(keyed);
    if (currentCount == null || currentCount == 0)
      throw new RuntimeException("Trying to remove a non-tracked object: " + keyed._key);
    if (currentCount == 1) {
      if (keyed instanceof Frame) {
        Frame frame = (Frame) keyed;
        Futures fs = new Futures();
        for (Vec vec : frame.vecs()) {
          // If vec is shared, then don't need to remove it just reduce the
          // vec copy count -- exactly what the method isVecCopyNeeded() does.
          // However if the vec is not shared, it must me deleted together
          // with the frame.
          if (!isVecCopyNeeded(vec))
            vec.remove(fs);
        }
        DKV.remove(frame._key, fs);
        fs.blockForPending();
      } else {
        keyed.remove();
      }
      keyedRefCounts.remove(keyed);
    } else {
      keyedRefCounts.put(keyed, currentCount - 1);
    }
  }


  //--------------------------------------------------------------------------------------------------------------------
  // Corporeal Frame Registry (CFR)
  //--------------------------------------------------------------------------------------------------------------------

  public void trackCorporealFrame(CorporealFrame frame) {
    corporealFrameRegistry.add(frame);
  }

  public void untrackCorporealFrame(CorporealFrame frame) {
    corporealFrameRegistry.remove(frame);
  }

  public void cleanCorporealFrameRegistry() {
    for (CorporealFrame frame : corporealFrameRegistry) {
      frame.dispose(globalScope());
    }
    corporealFrameRegistry.clear();
  }


  //--------------------------------------------------------------------------------------------------------------------
  // Registry of Vec Copies (RVC)
  //--------------------------------------------------------------------------------------------------------------------

  public void registerVecCopy(Vec vec) {
    Integer current = vecCopyCounts.get(vec);
    if (current == null) current = 0;
    vecCopyCounts.put(vec, current + 1);
  }

  /**
   * Test whether the given {@code vec} is shared across multiple frames. This
   * returns true if the vec is shared and therefore needs to be copied for
   * modification; or false if the vec is not shared. In addition, the method
   * <b>assumes</b> you will copy the vec, and therefore under this assumption
   * it will reduce the {@code vec}'s copy count.
   */
  public boolean isVecCopyNeeded(Vec vec) {
    Integer current = vecCopyCounts.get(vec);
    if (current == null) {
      return false;
    }
    if (current == 1) {
      vecCopyCounts.remove(vec);
    } else {
      vecCopyCounts.put(vec, current - 1);
    }
    return true;
  }

}
