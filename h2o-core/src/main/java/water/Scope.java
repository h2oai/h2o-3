package water;

import water.fvec.FileVec;
import water.fvec.Frame;
import water.fvec.Vec;
import water.logging.Logger;
import water.logging.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

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
  private final Stack<Level> _levels = new Stack<>();
  
  /** debugging purpose */
  public static Scope current() {
    return _scope.get();
  }
  
  /** for testing purpose */
  public static int nLevel() {
    Scope scope = current();
    return scope._levels.size();
  }
  
  /** for testing purpose */
  public static void reset() {
    Scope scope = _scope.get();
    scope._levels.clear();
  }
  
  /** Enter a new Scope */
  public static void enter() { 
    Scope scope = _scope.get();
    Level level = new Level();
    Level outer = scope._levels.empty() ? null : scope._levels.peek();
    if (outer != null) level._protectedKeys.addAll(outer._protectedKeys); //inherit protected keys from outer scope level
    scope._levels.push(level); 
  }

  /** Exit the innermost Scope, remove all Keys created since the matching
   *  enter call except for the listed Keys.
   *  @return Returns the list of kept keys. */
  public static Key[] exit(Key... keep) {
    Scope scope = _scope.get();
    assert !(scope._levels.empty()): "Scope in inconsistent state: Scope.exit() called without a matching Scope.enter()";
    final Set<Key> keepKeys = new HashSet<>();
    if (keep != null) {
      for (Key k : keep) {
        if (k != null) keepKeys.add(k);
      }
    }
    final Level exitingLevel = scope._levels.pop();
    keepKeys.addAll(exitingLevel._protectedKeys);
    Key[] arrkeep = keepKeys.toArray(new Key[0]);
    Arrays.sort(arrkeep);
    Futures fs = new Futures();
    
    final Map<Integer, List<Key<Vec>>> bulkRemovals = new HashMap<>();
    for (Key key : exitingLevel._keys) {
      boolean remove = arrkeep.length == 0 || Arrays.binarySearch(arrkeep, key) < 0;
      if (remove) {
        Value v = DKV.get(key);
        boolean cascade = !(v == null || v.isFrame()); //Frames are handled differently as we're explicitly also tracking their Vec keys...
        if (v != null && v.isVec() && exitingLevel._trackingInfo.containsKey(key)) {
          int nchunks = exitingLevel._trackingInfo.get(key)._nchunks;
          if (nchunks < 0) {
            Keyed.remove(key, fs, cascade); // don't bulk remove Vecs with unfilled _nchunks info.
          } else {
            if (!bulkRemovals.containsKey(nchunks)) bulkRemovals.put(nchunks, new ArrayList<>());
            bulkRemovals.get(nchunks).add(key);
          }
        } else {
          Keyed.remove(key, fs, cascade);
        }
      }
    }
    for (Map.Entry<Integer, List<Key<Vec>>> bulkRemoval : bulkRemovals.entrySet()) {
      Vec.bulk_remove(bulkRemoval.getValue().toArray(new Key[0]), bulkRemoval.getKey());
    }
    
    fs.blockForPending();
    exitingLevel.clear();
    return keep;
  }

  /**
   * @return true iff we are inside a scope
   */
  public static boolean isActive() {
    return !_scope.get()._levels.empty();
  }
  
  /**
   * get the current scope level in a context of modifying it, therefore requiring `Scope.enter()` to have been called first.
   * @return the current Scope.Level.
   */
  private static Level lget() {
    Scope scope = _scope.get();
//    assert !scope._levels.empty() : "Need to enter a Scope before modifying it.";  // would be nice to be able to enable this assertion, unfortunately too much code (tests?) don't fulfill this requirement currently.
    return scope._levels.empty() ? null : scope._levels.peek();
  }


  static void track_internal(Key k) {
    if (k.user_allowed() || !k.isVec()) return; // Not tracked
    Scope scope = _scope.get();                  // Pay the price of T.L.S. lookup
    if (scope._levels.empty()) return;           // track internal may currently be implicitly called when we're not inside a scope.
    track_impl(scope._levels.peek(), k);
  }

  public static <T extends Keyed<T>> T track_generic(T keyed) {
    if (keyed == null) return null;
    Level level = lget();                   // Pay the price of T.L.S. lookup
    track_impl(level, keyed._key);
    return keyed;
  }

  /**
   * Track a single Vec.
   * @param vec
   * @return
   */
  public static Vec track(Vec vec) {
    if (vec == null) return vec;
    Level level = lget();                   // Pay the price of T.L.S. lookup
    if (level == null) return vec;
    track_impl(level, vec._key);
    if (!(vec instanceof FileVec)) { // don't provide nchunks for individually tracked FileVecs as it is mutable for those (alternative is to fully disable this for all individually tracked Vecs) 
      final TrackingInfo vecInfo = new TrackingInfo();
      vecInfo._nchunks = vec.nChunks();  
      level._trackingInfo.put(vec._key, vecInfo);
    }
    return vec;
  }

  /**
   * Track one or more {@link Frame}s, as well as all their Vecs independently.
   * The tracked frames and vecs will be removed from DKV when {@link Scope#exit(Key[])} is called, 
   * but for {@link Frame}s, they will be removed without their Vecs as those are tracked independently, 
   * and we want to be able to {@link #untrack(Key[])} them (or spare them at {@link #exit(Key[])} 
   * without them being removed together with the {@link Frame} to which they're attached.
   * @param frames
   * @return the first Frame passed as param
   */
  public static Frame track(Frame... frames) {
    if (frames.length == 0) return null;
    Level level = lget();
    if (level == null) return frames[0];
    for (Frame fr : frames) {
      if (fr == null) continue;
      track_impl(level, fr._key);
      final TrackingInfo vecInfo = new TrackingInfo();
      vecInfo._source = Objects.toString(fr._key);
      for (Key<Vec> vkey : fr.keys()) {
        track_impl(level, vkey);
        if (vecInfo._nchunks < 0) {
          Vec vec = vkey.get();
          if (vec != null) vecInfo._nchunks = vec.nChunks();
        }
        if (vecInfo._nchunks > 0)
          level._trackingInfo.put(vkey, vecInfo);
      }
    }
    return frames[0];
  }


  private static void track_impl(Level level, Key key) {
    if (key == null) return;
    if (level == null) return;
    level._keys.add(key);            // Track key
  }

  /**
   * Untrack the specified keys.
   * Note that if a key corresponds to a {@Frame}, then only the frame key is untracked, not its vecs.
   * Use {@link #untrack(Frame...)} is you need a behaviour symmetrical to {@link #track(Frame...)}.
   * @param keys
   */
  public static <K extends Key> void untrack(K... keys) {
    if (keys.length == 0) return;
    Level level = lget();           // Pay the price of T.L.S. lookup
    if (level == null) return;      // should we allow calling `untrack` if we're not entered in a scope? (symmetry with `track` currently forces us to do so).
    Set<Key> xkeys = level._keys;
    for (Key key : keys) xkeys.remove(key); // Untrack key
  }

  /**
   * Untrack the specified keys.
   * Note that if a key corresponds to a {@Frame}, then only the frame key is untracked, not its vecs.
   * Use {@link #untrack(Frame...)} is you need a behaviour symmetrical to {@link #track(Frame...)}.
   * @param keys
   */
  public static <K extends Key> void untrack(Iterable<K> keys) {
    Level level = lget();           // Pay the price of T.L.S. lookup
    if (level == null) return;
    Set<Key> xkeys = level._keys;
    for (Key key : keys) xkeys.remove(key); // Untrack key
  }

  /**
   * 
   * @param frames
   * @return the first Frame passed as a param.
   */
  public static Frame untrack(Frame... frames) {
    if (frames.length == 0) return null;
    Level level = lget();           // Pay the price of T.L.S. lookup
    if (level == null) return frames[0];
    Set<Key> xkeys = level._keys;
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
    Level level = lget();           // Pay the price of T.L.S. lookup
    for (Frame fr : frames) {
      if (fr == null) continue;
      protect_impl(level, fr._key);
      for (Vec vec : fr.vecs())
        protect_impl(level, vec._key);
    }
    return frames[0];
  }
  
  private static void protect_impl(Level level, Key key) {
    if (key == null) return;
    if (level == null) return;
    level._protectedKeys.add(key);           // track-protect key
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
  
  static class Level {
    final Set<Key> _keys;
    final Set<Key> _protectedKeys;
    final Map<Key, TrackingInfo> _trackingInfo;

    Level() {
      _keys = new HashSet<>();
      _protectedKeys = new HashSet<>();
      _trackingInfo = new HashMap<>();
    }
    
    Level(Set<Key> keys, Set<Key> protectedKeys, Map<Key, TrackingInfo> trackingInfo) {
      _keys = keys;
      _protectedKeys = protectedKeys;
      _trackingInfo = trackingInfo;
    }

    void clear() {
      _keys.clear();
      _protectedKeys.clear();
      _trackingInfo.clear();
    }
  }

  /**
   * for debugging or test purpose
   */
  private static class ROLevel extends Level {
    public ROLevel(Level level) {
      super(
              Collections.unmodifiableSet(level._keys),
              Collections.unmodifiableSet(level._protectedKeys),
              Collections.unmodifiableMap(level._trackingInfo)
      );
    }
  }
  
  static class TrackingInfo {
    int _nchunks = -1; 
    String _source;
  }
  

  /**
   * @return a read-only view of scope levels
   */
  List<Level> levels() {
    return Collections.unmodifiableList(_levels.stream().map(ROLevel::new).collect(Collectors.toList()));
  }

}
