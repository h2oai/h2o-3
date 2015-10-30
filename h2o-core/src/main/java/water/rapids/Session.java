package water.rapids;

import water.Key;
import water.Futures;
import water.DKV;
import water.fvec.Frame;
import water.fvec.Vec;
import water.nbhm.*;

/**
 * Session is a long lasting session supporting caching and Copy-On-Write
 * optimization of Vecs.  This session may last over many different Rapids
 * calls in the same session.  When the session ends, all the cached Vecs will
 * be deleted (except those in user facing Frames).  
 **/
public class Session {
  // --------------------------------------------------------------------------
  // Copy On Write optimization
  // --------------------------------------------------------------------------
  // COW optimization: instead of copying Vecs, they are "virtually" copied by
  // simply pointer-sharing, and raising the ref-cnt here.  Losing a copy can
  // lower the ref-cnt, and when it goes to zero the Vec can be removed.  If
  // the Vec needs to be modified, and the ref-cnt is 1 - an update-in-place
  // can happen.  Otherwise a true data copy is made, and the private copy is
  // modified.

  // TODO: Make this private per-session, along with all temp keys

  // Ref-cnts per-Vec.  Always positive; zero is removed from the table;
  // negative is an error.  At the end of any given Rapids expression the
  // counts should match all the Vecs in the FRAMES set.
  static NonBlockingHashMap<Vec,Integer> REFCNTS = new NonBlockingHashMap<>();

  // Frames tracked by this Session and alive to the next Rapids call.  When
  // the whole session ends, these frames can be removed from the DKV.  These
  // Frames can share Vecs amongst themselves (tracked by the REFCNTS) and also
  // with other global frames.
  static NonBlockingHashMap<Key,Frame> FRAMES = new NonBlockingHashMap<>();

  // Vec that came from global frames, and are considered immutable.  Rapids
  // will always copy these Vecs before mutating or deleting.
  static NonBlockingHashSet<Vec> GLOBALS = new NonBlockingHashSet<>();
  
  Val exec(String rapids) { 
    String sane ;
    assert (sane=sanity_check_refs())==null : sane;

    Val val = Exec.exec(rapids,this); 
    // This value is being returned of session scope; we are no longer tracking
    // it so on the internal ref-cnts, so lower them - but do not delete if
    // cnts go to zero, this is the return value and it's lifetime is the
    // responsibility of the caller... AND the caller is not allowed to
    // directly delete this frame, but must ask the session to delete - since
    // the frame contains session-shared Vecs.
    if( val.isFrame() ) addRefCnt(val.getFrame(),-1);
    assert (sane=sanity_check_refs())==null : sane;
    return val;
  }

  void end() {
    String sane;
    assert (sane=sanity_check_refs())==null : sane;
    GLOBALS.clear();
    Futures fs = new Futures();
    for( Frame fr : FRAMES.values() ) {
      fs = downRefCnt(fr,fs);   // Remove internal Vecs one by one
      DKV.remove(fr._key,fs);   // Shallow remove, internal Vecs removed 1-by-1
    }
    fs.blockForPending();
    FRAMES.clear();
    assert (sane=sanity_check_refs())==null : sane;
    REFCNTS.clear();
  }

  // Internal ref cnts (not counting globals - which only ever keep things
  // alive, and have a virtual +1 to refcnts always)
  private int _getRefCnt( Vec vec ) {
    Integer I = REFCNTS.get(vec);
    assert I==null || I > 0;   // No zero or negative counts
    return I==null ? 0 : I;
  }
  private int _putRefCnt( Vec vec, int i ) {
    assert i >= 0;              // No negative counts
    if( i > 0 ) REFCNTS.put(vec,i);
    else REFCNTS.remove(vec);
    return i;
  }
  // Bump internal count, not counting globals
  private int _addRefCnt( Vec vec, int i ) { return _putRefCnt(vec,_getRefCnt(vec)+i); }

  // External refcnt: internal refcnt plus 1 for being global
  int getRefCnt( Vec vec ) {
    return _getRefCnt(vec)+ (GLOBALS.contains(vec) ? 1 : 0);
  }

  // RefCnt +i this Vec; Global Refs can be alive with zero internal counts
  int addRefCnt( Vec vec, int i ) { return _addRefCnt(vec,i) + (GLOBALS.contains(vec) ? 1 : 0); }

  // RefCnt +i all Vecs this Frame.
  Frame addRefCnt( Frame fr, int i ) {
    if( fr != null )  // Allow and ignore null Frame, easier calling convention
      for( Vec vec : fr.vecs() )  _addRefCnt(vec,i);
    return fr;                  // Flow coding
  }

  // Found in the DKV, if not a tracked TEMP make it a global
  Frame addGlobals( Frame fr ) {
    if( !FRAMES.contains(fr._key) )
      for( Vec vec : fr.vecs() ) GLOBALS.add(vec);
    return fr;                  // Flow coding
  }

  // Track a freshly minted tmp frame.  This frame can be removed when the
  // session ends (unlike global frames), or anytime during the session when
  // the client removes it.
  Frame track_tmp( Frame fr ) {
    assert fr._key != null;     // Temps have names
    FRAMES.put(fr._key,fr);     // Track for session
    addRefCnt(fr,1);            // Refcnt is also up: these Vecs stick around after single Rapids call for the next one
    DKV.put(fr);                // Into DKV, so e.g. Flow can view for debugging
    return fr;                  // Flow coding
  }

  // Remove from all session tracking spaces.
  // Remove any newly-unshared Vecs.
  void remove( Frame fr ) {
    if( fr == null ) return;
    Futures fs = new Futures();
    if( !FRAMES.containsKey(fr._key) ) { // In globals and not temps?
      for( Vec vec : fr.vecs() ) { 
        GLOBALS.remove(vec);         // Not a global anymore
        if( REFCNTS.get(vec)==null ) // And not shared with temps
          vec.remove(fs);            // Remove unshared dead global
      }
    } else {                    // Else a temp and not a global
      fs = downRefCnt(fr,fs);   // Standard down-ref counting of all Vecs
      FRAMES.remove(fr._key);   // And remove from temps
    }
    DKV.remove(fr._key,fs);     // Shallow remove, internal Vecs removed 1-by-1
    fs.blockForPending();
  }

  // Lower refcnt of all Vecs in frame, deleting Vecs that go to zero refs.
  // Passed in a Futures which is returned, and set to non-null if something
  // gets deleted.
  Futures downRefCnt( Frame fr, Futures fs ) {
    for( Vec vec : fr.vecs() )    // Refcnt -1 all Vecs
      if( addRefCnt(vec,-1) == 0 ) {
        if( fs == null ) fs = new Futures();
        vec.remove(fs);
      }
    return fs;
  }


  // Check that ref cnts are sane.  Only callable between calls to Rapids
  // expressions (otherwise may blow false-positives).
  String sanity_check_refs() {
    NonBlockingHashMap<Vec,Integer> refcnts = new NonBlockingHashMap<>();
    for( Frame fr : FRAMES.values() )
      for( Vec vec : fr.vecs() ) {
        Integer I = refcnts.get(vec);
        refcnts.put(vec,I==null ? 1 : I+1);
      }
    for( Vec vec : refcnts.keySet() ) {
      Integer I = REFCNTS.get(vec);
      if( I==null ) return "REFCNTS missing vec "+vec;
      Integer II = refcnts.get(vec);
      if( (int)II != (int)I ) return "Mismatch vec "+vec+", computed refcnts: "+II+", cached REFCNTS: "+I;
    }
    return null;                // OK
  }
}
