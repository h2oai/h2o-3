package water.rapids;

import water.DKV;
import water.Futures;
import water.Key;
import water.MRTask;
import water.fvec.Frame;
import water.fvec.Vec;
import water.nbhm.*;
import water.rapids.ast.AstFunction;
import water.rapids.ast.AstRoot;
import water.rapids.ast.prims.operators.AstPlus;
import water.util.Log;

import java.util.Collections;
import java.util.Map;

/**
 * Session is a long-lasting environment supporting caching and Copy-On-Write optimization of Vecs.  This session may
 * last over many different Rapids calls (provided they refer to the same session).  When the session ends, all the
 * cached Vecs will be deleted (except those in user facing Frames).
 **/
public class Session {
  // How often to perform sanity checks. 0 means disable checks, 1000 is to always check.
  private final static int sanityChecksFrequency = 1000;
  private static int sanityChecksCounter = 0;

  // --------------------------------------------------------------------------
  // Copy On Write optimization
  // --------------------------------------------------------------------------
  // COW optimization: instead of copying Vecs, they are "virtually" copied by
  // simply pointer-sharing, and raising the ref-cnt here.  Losing a copy can
  // lower the ref-cnt, and when it goes to zero the Vec can be removed.  If
  // the Vec needs to be modified, and the ref-cnt is 1 - an update-in-place
  // can happen.  Otherwise a true data copy is made, and the private copy is
  // modified.

  // Ref-counts per Vec.  Always positive; zero is removed from the table;
  // negative is an error.  At the end of any given Rapids expression the
  // counts should match all the Vecs in the FRAMES set.
  private NonBlockingHashMap<Vec, Integer> REFCNTS = new NonBlockingHashMap<>();

  // Frames tracked by this Session and alive to the next Rapids call.  When
  // the whole session ends, these frames can be removed from the DKV.  These
  // Frames can share Vecs amongst themselves (tracked by the REFCNTS) and also
  // with other global frames.
  private NonBlockingHashMap<Key, Frame> FRAMES = new NonBlockingHashMap<>();

  // Vec that came from global frames, and are considered immutable.  Rapids
  // will always copy these Vecs before mutating or deleting.  Total visible
  // refcnts are effectively the normal refcnts plus 1 for being in the GLOBALS
  // set.
  private NonBlockingHashSet<Vec> GLOBALS = new NonBlockingHashSet<>();


  /**
   * Constructor
   */
  public Session() {
    cluster_init();
  }

  /**
   * Execute an AstRoot in the current Session with much assertion-checking
   * @param ast Rapids expression to execute
   * @param scope ?
   * @return the result from the Rapids expression
   */
  public Val exec(AstRoot ast, AstFunction scope) {
    sanity_check_refs(null);

    // Execute
    Env env = new Env(this);
    env._scope = scope;
    Val val = ast.exec(env);    // Execute
    assert env.sp() == 0;         // Stack balanced at end
    sanity_check_refs(val);
    return val;                 // Can return a frame, which may point to session-shared Vecs
  }

  /**
   * Normal session exit.  Returned Frames are fully deep-copied, and are responsibility of the caller to delete.
   * Returned Frames have their refcnts currently up by 1 (for the returned value itself).
   */
  public Val end(Val returning) {
    sanity_check_refs(returning);
    // Remove all temp frames
    Futures fs = new Futures();
    for (Frame fr : FRAMES.values()) {
      fs = downRefCnt(fr, fs);   // Remove internal Vecs one by one
      DKV.remove(fr._key, fs);   // Shallow remove, internal Vecs removed 1-by-1
    }
    fs.blockForPending();
    FRAMES.clear();             // No more temp frames
    // Copy (as needed) so the returning Frame is completely independent of the
    // (disappearing) session.
    if (returning != null && returning.isFrame()) {
      Frame fr = returning.getFrame();
      Vec[] vecs = fr.vecs();
      for (int i = 0; i < vecs.length; i++) {
        _addRefCnt(vecs[i], -1); // Returning frame has refcnt +1, lower it now; should go to zero internal refcnts.
        if (GLOBALS.contains(vecs[i])) // Copy if shared with globals
          fr.replace(i, vecs[i].makeCopy());
      }
    }
    GLOBALS.clear();            // No longer tracking globals
    sanity_check_refs(null);
    REFCNTS.clear();
    return returning;
  }

  /**
   * The Rapids call threw an exception.  Best-effort cleanup, no more exceptions
   */
  public RuntimeException endQuietly(Throwable ex) {
    try {
      GLOBALS.clear();
      Futures fs = new Futures();
      for (Frame fr : FRAMES.values()) {
        for (Vec vec : fr.vecs()) {
          Integer I = REFCNTS.get(vec);
          int i = (I == null ? 0 : I) - 1;
          if (i > 0) REFCNTS.put(vec, i);
          else {
            REFCNTS.remove(vec);
            vec.remove(fs);
          }
        }
        DKV.remove(fr._key, fs);   // Shallow remove, internal Vecs removed 1-by-1
      }
      fs.blockForPending();
      FRAMES.clear();
      REFCNTS.clear();
    } catch (Exception ex2) {
      Log.warn("Exception " + ex2 + " suppressed while cleaning up Rapids Session after already throwing " + ex);
    }
    return ex instanceof RuntimeException ? (RuntimeException) ex : new RuntimeException(ex);
  }

  /**
   * Internal ref cnts (not counting globals - which only ever keep things alive, and have a virtual +1 to refcnts
   * always).
   */
  private int _getRefCnt(Vec vec) {
    Integer I = REFCNTS.get(vec);
    assert I == null || I > 0;   // No zero or negative counts
    return I == null ? 0 : I;
  }

  private int _putRefCnt(Vec vec, int i) {
    assert i >= 0;              // No negative counts
    if (i > 0) REFCNTS.put(vec, i);
    else REFCNTS.remove(vec);
    return i;
  }

  /**
   * Bump internal count, not counting globals
   */
  private int _addRefCnt(Vec vec, int i) {
    return _putRefCnt(vec, _getRefCnt(vec) + i);
  }

  /**
   * External refcnt: internal refcnt plus 1 for being global
   */
  private int getRefCnt(Vec vec) {
    return _getRefCnt(vec) + (GLOBALS.contains(vec) ? 1 : 0);
  }

  /**
   * RefCnt +i this Vec; Global Refs can be alive with zero internal counts
   */
  private int addRefCnt(Vec vec, int i) {
    return _addRefCnt(vec, i) + (GLOBALS.contains(vec) ? 1 : 0);
  }

  /**
   * RefCnt +i all Vecs this Frame.
   */
  Frame addRefCnt(Frame fr, int i) {
    if (fr != null)  // Allow and ignore null Frame, easier calling convention
      for (Vec vec : fr.vecs()) _addRefCnt(vec, i);
    return fr;                  // Flow coding
  }

  /**
   * Found in the DKV, if not a tracked TEMP make it a global
   */
  Frame addGlobals(Frame fr) {
    if (!FRAMES.containsKey(fr._key))
      Collections.addAll(GLOBALS, fr.vecs());
    return fr;                  // Flow coding
  }

  /**
   * Track a freshly minted tmp frame.  This frame can be removed when the session ends (unlike global frames), or
   * anytime during the session when the client removes it.
   */
  public Frame track_tmp(Frame fr) {
    assert fr._key != null;     // Temps have names
    FRAMES.put(fr._key, fr);     // Track for session
    addRefCnt(fr, 1);            // Refcnt is also up: these Vecs stick around after single Rapids call for the next one
    DKV.put(fr);                // Into DKV, so e.g. Flow can view for debugging
    return fr;                  // Flow coding
  }

  /**
   * Remove and delete a session-tracked frame.
   * Remove from all session tracking spaces.
   * Remove any newly-unshared Vecs, but keep the shared ones.
   */
  public void remove(Frame fr) {
    if (fr == null) return;
    Futures fs = new Futures();
    if (!FRAMES.containsKey(fr._key)) { // In globals and not temps?
      for (Vec vec : fr.vecs()) {
        GLOBALS.remove(vec);         // Not a global anymore
        if (REFCNTS.get(vec) == null) // If not shared with temps
          vec.remove(fs);            // Remove unshared dead global
      }
    } else {                    // Else a temp and not a global
      fs = downRefCnt(fr, fs);   // Standard down-ref counting of all Vecs
      FRAMES.remove(fr._key);   // And remove from temps
    }
    DKV.remove(fr._key, fs);     // Shallow remove, internal were Vecs removed 1-by-1
    fs.blockForPending();
  }

  /**
   * Lower refcnt of all Vecs in frame, deleting Vecs that go to zero refs.
   * Passed in a Futures which is returned, and set to non-null if something gets deleted.
   */
  Futures downRefCnt(Frame fr, Futures fs) {
    for (Vec vec : fr.vecs())    // Refcnt -1 all Vecs
      if (addRefCnt(vec, -1) == 0) {
        if (fs == null) fs = new Futures();
        vec.remove(fs);
      }
    return fs;
  }

  /**
   * Update a global ID, maintaining sharing of Vecs
   */
  public Frame assign(Key<Frame> id, Frame src) {
    if (FRAMES.containsKey(id)) throw new IllegalArgumentException("Cannot reassign temp " + id);
    Futures fs = new Futures();
    // Vec lifetime invariant: Globals do not share with other globals (but can
    // share with temps).  All the src Vecs are about to become globals.  If
    // the ID already exists, and global Vecs within it are about to die, and thus
    // may be deleted.
    Frame fr = DKV.getGet(id);
    if (fr != null) {          // Prior frame exists
      for (Vec vec : fr.vecs()) {
        if (GLOBALS.remove(vec) && _getRefCnt(vec) == 0)
          vec.remove(fs);       // Remove unused global vec
      }
    }
    // Copy (defensive) the base vecs array.  Then copy any vecs which are
    // already globals - this new global must be independent of any other
    // global Vecs - because global Vecs get side-effected by unrelated
    // operations.
    Vec[] svecs = src.vecs().clone();
    for (int i = 0; i < svecs.length; i++)
      if (GLOBALS.contains(svecs[i]))
        svecs[i] = svecs[i].makeCopy();
    // Make and install new global Frame
    Frame fr2 = new Frame(id, src._names.clone(), svecs);
    DKV.put(fr2, fs);
    addGlobals(fr2);
    fs.blockForPending();
    return fr2;
  }

  /**
   * Support C-O-W optimizations: the following list of columns are about to be updated.  Copy them as-needed and
   * replace in the Frame.  Return the updated Frame vecs for flow-coding.
   */
  public Vec[] copyOnWrite(Frame fr, int[] cols) {
    Vec did_copy = null;        // Did a copy?
    Vec[] vecs = fr.vecs();
    for (int col : cols) {
      Vec vec = vecs[col];
      int refcnt = getRefCnt(vec);
      assert refcnt > 0;
      if (refcnt > 1)          // If refcnt is 1, we allow the update to take in-place
        fr.replace(col, (did_copy = vec.makeCopy()));
    }
    if (did_copy != null && fr._key != null) DKV.put(fr); // Then update frame in the DKV
    return vecs;
  }

  /**
   * Check that ref counts are in a consistent state.
   * This should only be called between calls to Rapids expressions (otherwise may blow false-positives).
   * @param returning If sanity check is done at the end of the session, there is a value being returned. This value
   *                  might be a Frame (which would not be in FRAMES). So we pass the "returning" value explicitly,
   *                  so that all its references can be properly accounted for.
   */
  private void sanity_check_refs(Val returning) {
    if ((sanityChecksCounter++) % 1000 >= sanityChecksFrequency) return;

    // Compute refcnts from tracked frames only.  Since we are between Rapids
    // calls the only tracked Vecs should be those from tracked frames.
    NonBlockingHashMap<Vec, Integer> refcnts = new NonBlockingHashMap<>(REFCNTS.size());
    for (Frame fr : FRAMES.values())
      for (Vec vec : fr.vecs()) {
        Integer count = refcnts.get(vec);
        refcnts.put(vec, count == null ? 1 : count + 1);
      }
    // Now account for the returning frame (if it is a Frame). Note that it is entirely possible that this frame is
    // already in the FRAMES list, however we need to account for it anyways -- this is how Env works...
    if (returning != null && returning.isFrame())
      for (Vec vec : returning.getFrame().vecs()) {
        Integer count = refcnts.get(vec);
        refcnts.put(vec, count == null ? 1 : count + 1);
      }

    // Now compare computed refcnts to cached REFCNTS.
    // First check that every Vec in computed refcnt is also in REFCNTS, with equal counts.
    for (Map.Entry<Vec,Integer> pair : refcnts.entrySet()) {
      Vec vec = pair.getKey();
      Integer count = pair.getValue();
      Integer savedCount = REFCNTS.get(vec);
      if (savedCount == null) throw new IllegalStateException("REFCNTS missing vec " + vec);
      if (count.intValue() != savedCount.intValue())
        throw new IllegalStateException(
            "Ref-count mismatch for vec " + vec + ": REFCNT = " + savedCount + ", should be " + count);
    }
    // Then check that every cached REFCNT is in the computed set as well.
    if (refcnts.size() != REFCNTS.size())
      for (Map.Entry<Vec,Integer> pair : REFCNTS.entrySet()) {
        if (!refcnts.containsKey(pair.getKey()))
          throw new IllegalStateException(
              "REFCNTs contains an extra vec " + pair.getKey() + ", count = " + pair.getValue());
      }
  }

  // To avoid a class-circularity hang, we need to force other members of the
  // cluster to load the Rapids & AstRoot classes BEFORE trying to execute code
  // remotely, because e.g. ddply runs functions on all nodes.
  private static volatile boolean _initialized; // One-shot init

  static void cluster_init() {
    if (_initialized) return;
    // Touch a common class to force loading
    new MRTask() {
      @Override
      public void setupLocal() {
        new AstPlus();
      }
    }.doAllNodes();
    _initialized = true;
  }
}
