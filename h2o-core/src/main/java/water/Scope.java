package water;

import water.fvec.Frame;
import water.fvec.Vec;
import water.logging.Logger;
import water.logging.LoggerFactory;

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
  
  private static final Logger log = LoggerFactory.getLogger(Scope.class);
  
  // Thread-based Key lifetime tracking
  private static final ThreadLocal<Scope> _scope = new ThreadLocal<Scope>() {
    @Override protected Scope initialValue() { return new Scope(); }
  };
  private final Stack<Set<Key>> _keys = new Stack<>();
  
  private final Stack<Set<Key>> _protectedKeys = new Stack<>();
  
  /** debugging purpose */
  public static Scope current() {
    return _scope.get();
  }

  /** Enter a new Scope */
  public static void enter() { 
    Scope scope = _scope.get();
    scope._keys.push(new HashSet<>()); 
    Set<Key> outerProtected = scope._protectedKeys.empty() ? Collections.emptySet() : scope._protectedKeys.peek(); 
    scope._protectedKeys.push(new HashSet<>(outerProtected)); //inherit protected keys from outer scope
  }

  /** Exit the innermost Scope, remove all Keys created since the matching
   *  enter call except for the listed Keys.
   *  @return Returns the list of kept keys. */
  public static Key[] exit(Key... keep) {
    Scope scope = _scope.get();
    assert !(scope._keys.empty() || scope._protectedKeys.empty()): "Scope in inconsistent state: Scope.exit() called without a matching Scope.enter()";
    Set<Key> keepKeys = new HashSet<>();
    if (keep != null) {
      for (Key k : keep) {
        if (k != null) keepKeys.add(k);
      }
    }
    keepKeys.addAll(scope._protectedKeys.pop());
    Key[] arrkeep = keepKeys.toArray(new Key[0]);
    Arrays.sort(arrkeep);
    Set<Key> removeKeys = scope._keys.pop();
    Futures fs = new Futures();
    for (Key key : removeKeys) {
      boolean remove = arrkeep.length == 0 || Arrays.binarySearch(arrkeep, key) < 0;
      if (remove) {
        boolean cascade = !(key.get() instanceof Frame); //Frames are handled differently as we're explicitly also tracking their Vec keys...
        Keyed.remove(key, fs, cascade);
      }
      fs.blockForPending();
    }
    return keep;
  }

  /**
   * @return true iff we are inside a scope
   */
  public static boolean isActive() {
    return !_scope.get()._keys.empty();
  }

  static void track_internal(Key k) {
    if( k.user_allowed() || !k.isVec() ) return; // Not tracked
    Scope scope = _scope.get();                   // Pay the price of T.L.S. lookup
    if (scope == null) return;
    track_impl(scope, k);
  }

  public static <T extends Keyed<T>> T track_generic(T keyed) {
    if (keyed == null)
      return null;
    Scope scope = _scope.get();                   // Pay the price of T.L.S. lookup
    assert scope != null;
    track_impl(scope, keyed._key);
    return keyed;
  }

  public static Vec track( Vec vec ) {
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
      if (fr == null) continue;
      track_impl(scope, fr._key);
      for (Vec vec : fr.vecs())
        track_impl(scope, vec._key);
    }
    return frames[0];
  }

  private static void track_impl(Scope scope, Key key) {
    if (key == null) return;
    if (scope._keys.empty()) return;
    scope._keys.peek().add(key);            // Track key
  }
  
  public static <K extends Key> void untrack(K... keys) {
    Scope scope = _scope.get();           // Pay the price of T.L.S. lookup
    if (scope == null) return;           // Not tracking this thread
    if (scope._keys.empty()) return;     // Tracked in the past, but no scope now
    Set<Key> xkeys = scope._keys.peek();
    for (Key key : keys) xkeys.remove(key); // Untrack key
  }

  public static <K extends Key> void untrack(Iterable<K> keys) {
    Scope scope = _scope.get();           // Pay the price of T.L.S. lookup
    if (scope == null) return;           // Not tracking this thread
    if (scope._keys.empty()) return;     // Tracked in the past, but no scope now
    Set<Key> xkeys = scope._keys.peek();
    for (Key key : keys) xkeys.remove(key); // Untrack key
  }
  
  public static Frame untrack(Frame... frames) {
    Scope scope = _scope.get();
    if (scope == null || scope._keys.empty()) return frames[0];
    Set<Key> xkeys = scope._keys.peek();
    for (Frame fr : frames) {
      xkeys.remove(fr._key);
      xkeys.removeAll(Arrays.asList(fr.keys()));
    }
    return frames[0];
  }

  /**
   * Protects the listed frames and their vecs inside this scope and inner scopes so that they can't be removed, 
   * for example if an unprotected frame shares some Vecs.
   * @param frames
   * @return the first protected frame.
   */
  public static Frame protect(Frame... frames) {
    if (frames.length == 0) return null;
    Scope scope = _scope.get();
    assert scope != null;
    for (Frame fr : frames) {
      if (fr == null) continue;
      protect_impl(scope, fr._key);
      for (Vec vec : fr.vecs())
        protect_impl(scope, vec._key);
    }
    return frames[0];
  }
  
  private static void protect_impl(Scope scope, Key key) {
    if (key == null) return;
    if (scope._protectedKeys.empty()) return;
    scope._protectedKeys.peek().add(key);            // Track key
  }

  /**
   * Enters a new scope and protects the passed frames in that scope.
   * To be used as a resource in a try block: the new "safe" scope will then be auto-exited.
   */
  public static Safe safe(Frame... protectedFrames) {
    Safe scope = new Safe();
    Scope.protect(protectedFrames);
    return scope;
  }

  public static class Safe implements AutoCloseable {
    
    private Safe() {
      Scope.enter();
    }
    
    @Override
    public void close() {
      Scope.exit();
    }
  }

}
