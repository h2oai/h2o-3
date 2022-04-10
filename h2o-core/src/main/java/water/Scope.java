package water;

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
  
  /** debugging purpose */
  static public Scope current() {
    return _scope.get();
  }

  /** Enter a new Scope */
  static public void enter() { _scope.get()._keys.push(new HashSet<Key>()); }

  /** Exit the inner-most Scope, remove all Keys created since the matching
   *  enter call except for the listed Keys.
   *  @return Returns the list of kept keys. */
  static public Key[] exit(Key... keep) {
    List<Key> keylist = new ArrayList<>();
    if( keep != null )
      for( Key k : keep ) if (k != null) keylist.add(k);
    Object[] arrkeep = keylist.toArray();
    Arrays.sort(arrkeep);
    Stack<HashSet<Key>> keys = _scope.get()._keys;
    if (keys.size() > 0) {
      Futures fs = new Futures();
      for (Key key : keys.pop()) {
        int found = Arrays.binarySearch(arrkeep, key);
        if (arrkeep.length == 0 || found < 0) Keyed.remove(key, fs, true);
      }
      fs.blockForPending();
    }
    return keep;
  }

  /** Pop-scope (same as exit-scope) but return all keys that are tracked (and
   *  would have been deleted). */
  static public boolean isActive() {
    Stack<HashSet<Key>> keys = _scope.get()._keys;
    return keys.size() > 0;
  }

  static void track_internal( Key k ) {
    if( k.user_allowed() || !k.isVec() ) return; // Not tracked
    Scope scope = _scope.get();                   // Pay the price of T.L.S. lookup
    if (scope == null) return;
    track_impl(scope, k);
  }

  static public <T extends Keyed<T>> T track_generic(T keyed) {
    Scope scope = _scope.get();                   // Pay the price of T.L.S. lookup
    assert scope != null;
    track_impl(scope, keyed._key);
    return keyed;
  }

  static public Vec track( Vec vec ) {
    Scope scope = _scope.get();                   // Pay the price of T.L.S. lookup
    assert scope != null;
    track_impl(scope, vec._key);
    return vec;
  }

  /**
   * Track one or more {@link Frame}s, and return the first one. The tracked
   * frames will be removed from DKV when {@link Scope#exit(Key[])} is called.
   */
  public static Frame track(Frame... frames) {
    Scope scope = _scope.get();
    assert scope != null;
    for (Frame fr : frames) {
      track_impl(scope, fr._key);
      for (Vec vec : fr.vecs())
        track_impl(scope, vec._key);
    }
    return frames[0];
  }

  static private void track_impl(Scope scope, Key key) {
    if (key == null) return;
    // key size is 0 when tracked in the past, but no scope now
    if (scope._keys.size() > 0 && !scope._keys.peek().contains(key))
      scope._keys.peek().add(key);            // Track key
  }
  static public <K extends Keyed> void untrack(Key<K>... keys) {
    Scope scope = _scope.get();           // Pay the price of T.L.S. lookup
    if (scope == null) return;           // Not tracking this thread
    if (scope._keys.size() == 0) return; // Tracked in the past, but no scope now
    HashSet<Key> xkeys = scope._keys.peek();
    for (Key key : keys) xkeys.remove(key); // Untrack key
  }

  static public <K extends Keyed> void untrack(Iterable<Key<K>> keys) {
    Scope scope = _scope.get();           // Pay the price of T.L.S. lookup
    if (scope == null) return;           // Not tracking this thread
    if (scope._keys.size() == 0) return; // Tracked in the past, but no scope now
    HashSet<Key> xkeys = scope._keys.peek();
    for (Key key : keys) xkeys.remove(key); // Untrack key
  }
  
  static public void untrack(Frame... frames) {
    Scope scope = _scope.get();
    if (scope == null || scope._keys.empty()) return;
    Set<Key> xkeys = scope._keys.peek();
    for (Frame fr : frames) {
      xkeys.remove(fr._key);
      xkeys.removeAll(Arrays.asList(fr.keys()));
    }
  }

}
