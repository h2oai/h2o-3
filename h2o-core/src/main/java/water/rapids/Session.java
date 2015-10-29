package water.rapids;

import java.util.concurrent.atomic.AtomicInteger;
import water.MRTask;
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
  static NonBlockingHashMap<Vec,AtomicInteger> REFCNTS = new NonBlockingHashMap<>();
  
  Val exec(String rapids) {
    throw water.H2O.unimpl();
  }

  void end() {
    water.H2O.unimpl();
  }
}
