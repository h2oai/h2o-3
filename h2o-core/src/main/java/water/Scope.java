package water;

import com.google.common.collect.Sets;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.*;

/** A "scope" for tracking Key lifetimes; an experimental API.
 *
 *  <p>A Scope defines a <em>SINGLE THREADED</em> local lifetime management context,
 *  stored in Thread Local Storage.  Scopes can be explicitly entered or exited.
 *  User keys created by this thread are tracked, and deleted when the scope is
 *  exited.  Since enter &amp; exit are explicit, failure to exit means the Keys
 *  leak (there is no reliable thread-on-exit cleanup action).  You must call
 *  <code>Scope.exit()</code> at some point.  Only user keys &amp; Vec keys are tracked.</p>
 *
 *  <p>Scopes support nesting.  Scopes support partial cleanup: you can list Keys
 *  you'd like to keep in the exit() call.  These will be "bumped up" to the
 *  higher nested scope - or escaped and become untracked at the top-level.</p>
 */

public class Scope {
  // Thread-based Key lifetime tracking
  static private final ThreadLocal<Scope> _scope = new ThreadLocal<Scope>() {
    @Override protected Scope initialValue() { return new Scope(); }
  };
  private final Stack<HashSet<Key>> _keys = new Stack<>();

  /** Enter a new Scope */
  static public void enter() { _scope.get()._keys.push(new HashSet<Key>()); }

  /** Exit the inner-most Scope, remove all Keys created since the matching
   *  enter call except for the listed Keys.
   *  @return Returns the list of kept keys. */
  static public void exit() {
    exit(Collections.EMPTY_LIST);
  }

  public static <T extends Keyed<T>> Iterable<Key<T>> exit(Collection<Key<T>> keep) {
    Set<Key> mustKeep = new HashSet<Key>(keep);
    Stack<HashSet<Key>> keys = _scope.get()._keys;
    Set<Key> keysToDrop = keys.isEmpty() ? Collections.EMPTY_SET : keys.pop();
    if (!keysToDrop.isEmpty()) {
      Futures fs = new Futures();
      for (Key key : keysToDrop) {
        if (key != null && !mustKeep.contains(key)) Keyed.remove(key, fs);
      }
      fs.blockForPending();
    }
    return keep;
  }

  /** Pop-scope (same as exit-scope) but return all keys that are tracked (and
   *  would have been deleted). */
  static public Key[] pop() {
    Stack<HashSet<Key>> keys = _scope.get()._keys;
    return keys.size() > 0 ? keys.pop().toArray(new Key[0]) : null;
  }

  static void track_internal( Key k ) {
    if( k.user_allowed() || !k.isVec() ) return; // Not tracked
    Scope scope = _scope.get();                   // Pay the price of T.L.S. lookup
    if (scope == null) return;
    track_impl(scope, k);
  }

  static public Vec track( Vec vec ) {
    Scope scope = _scope.get();                   // Pay the price of T.L.S. lookup
    assert scope != null;
    track_impl(scope, vec._key);
    return vec;
  }

  static public Frame track( Frame fr ) {
    Scope scope = _scope.get();                   // Pay the price of T.L.S. lookup
    assert scope != null;
    for( Vec vec: fr.vecs() ) track_impl(scope, vec._key);
    track_impl(scope, fr._key);
    return fr;
  }

  static private void track_impl(Scope scope, Key key) {
    // key size is 0 when tracked in the past, but no scope now
    if (scope._keys.size() > 0 && !scope._keys.peek().contains(key))
      scope._keys.peek().add(key);            // Track key
  }

  static public void untrack(Iterable<Key<Vec>> keys) {
    Scope scope = _scope.get();           // Pay the price of T.L.S. lookup
    if (scope == null) return;           // Not tracking this thread
    if (scope._keys.size() == 0) return; // Tracked in the past, but no scope now
    HashSet<Key> xkeys = scope._keys.peek();
    for (Key<Vec> key : keys) xkeys.remove(key); // Untrack key
  }
}
