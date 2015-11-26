package water;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import water.fvec.Chunk;
import water.util.Log;
import water.util.PrettyPrint;

/** Store Cleaner: User-Mode Swap-To-Disk */

class Cleaner extends Thread {
  // msec time at which the STORE was dirtied.
  // Long.MAX_VALUE if clean.
  static private volatile long _dirty; // When was store dirtied
  static long dirty() { return _dirty; } // exposed for testing only
  static void dirty_store() { dirty_store(System.currentTimeMillis()); }
  static void dirty_store( long x ) {
    // Keep earliest dirty time seen
    if( x < _dirty ) _dirty = x;
  }

  static volatile long HEAP_USED_AT_LAST_GC;
  static volatile long KV_USED_AT_LAST_GC;
  static volatile long TIME_AT_LAST_GC=System.currentTimeMillis();
  static final Cleaner THE_CLEANER = new Cleaner();
  static void kick_store_cleaner() {
    synchronized(THE_CLEANER) { THE_CLEANER.notifyAll(); }
  }
  private static void block_store_cleaner() {
    synchronized(THE_CLEANER) { try { THE_CLEANER.wait(5000); } catch (InterruptedException ignore) { } }
  }
  volatile boolean _did_sweep;
  static void block_for_test() throws InterruptedException {
    THE_CLEANER._did_sweep = false;
    synchronized(THE_CLEANER) {
      while( !THE_CLEANER._did_sweep )
        THE_CLEANER.wait();
    }
  }


  // Desired cache level. Set by the MemoryManager asynchronously.
  static volatile long DESIRED;

  Cleaner() {
    super("MemCleaner");
    setDaemon(true);
    setPriority(MAX_PRIORITY-2);
    _dirty = Long.MAX_VALUE;  // Set to clean-store
    Histo.current(true);      // Build/allocate a first histogram
    Histo.current(true);      // Force a recompute with a good eldest
    MemoryManager.set_goals("init",false);
  }

  static boolean isDiskFull(){ // free disk space < 5K?
    long space = H2O.getPM().getIce().getUsableSpace();
    return space >= 0 && space < (5 << 10);
  }

  // Cleaner thread runs in a forever loop.  (This call cannot be synchronized,
  // lest we hold the lock during a (very long) clean process - and various
  // async callbacks attempt to "kick" the Cleaner awake - which will require
  // taking the lock... blocking the kicking thread for the duration.
  @Override /*synchronized*/ public void run() {
    boolean diskFull = false;
    while( true ) {
      // Sweep the K/V store, writing out Values (cleaning) and free'ing
      // - Clean all "old" values (lazily, optimistically)
      // - Clean and free old values if above the desired cache level
      // Do not let optimistic cleaning get in the way of emergency cleaning.

      // Get a recent histogram, computing one as needed
      Histo h = Histo.current(false);
      long now = System.currentTimeMillis();
      long dirty = _dirty; // When things first got dirtied

      // Start cleaning if: "dirty" was set a "long" time ago, or we beyond
      // the desired cache levels. Inverse: go back to sleep if the cache
      // is below desired levels & nothing has been dirty awhile.
      if( h._cached < DESIRED && // Cache is low and
          (now-dirty < 5000) ) { // not dirty a long time
        // Block asleep, waking every 5 secs to check for stuff, or when poked
        block_store_cleaner();
        continue; // Awoke; loop back and re-check histogram.
      }

      now = System.currentTimeMillis();
      _dirty = Long.MAX_VALUE; // Reset, since we are going write stuff out
      MemoryManager.set_goals("preclean",false);

      // The age beyond which we need to toss out things to hit the desired
      // caching levels. If forced, be exact (toss out the minimal amount).
      // If lazy, store-to-disk things down to 1/2 the desired cache level
      // and anything older than 5 secs.
      boolean force = (h._cached >= DESIRED || !MemoryManager.CAN_ALLOC); // Forced to clean
      if( force && diskFull )   // Try to clean the diskFull flag
        diskFull = isDiskFull();
      long clean_to_age = h.clean_to(force ? DESIRED : (DESIRED>>1));
      // If not forced cleaning, expand the cleaning age to allows Values
      // more than 5sec old
      if( !force ) clean_to_age = Math.max(clean_to_age,now-5000);
      if( DESIRED == -1 ) clean_to_age = now;  // Test mode: clean all

      // No logging if under memory pressure: can deadlock the cleaner thread
      String s = h+" DESIRED="+(DESIRED>>20)+"M dirtysince="+(now-dirty)+" force="+force+" clean2age="+(now-clean_to_age);
      if( MemoryManager.canAlloc() ) Log.debug(s);
      else                           System.err.println(s);
      long cleaned = 0;         // Disk i/o bytes
      long freed = 0;           // memory freed bytes
      long io_ns = 0;           // i/o ns writing

      // For faster K/V store walking get the NBHM raw backing array,
      // and walk it directly.
      Object[] kvs = H2O.STORE.raw_array();

      // Start the walk at slot 2, because slots 0,1 hold meta-data
      for( int i=2; i<kvs.length; i += 2 ) {
        // In the raw backing array, Keys and Values alternate in slots
        Object ok = kvs[i], ov = kvs[i+1];
        if( !(ok instanceof Key  ) ) continue; // Ignore tombstones and Primes and null's
        if( !(ov instanceof Value) ) continue; // Ignore tombstones and Primes and null's
        Value val = (Value)ov;
        byte[] m = val.rawMem();
        Object p = val.rawPOJO();
        if( m == null && p == null ) continue; // Nothing to throw out

        if( val.isLockable() ) continue; // we do not want to throw out Lockables.
        boolean isChunk = p instanceof Chunk;

        // Ignore things younger than the required age.  In particular, do
        // not spill-to-disk all dirty things we find.
        long touched = val._lastAccessedTime;
        if( touched > clean_to_age ) { // Too recently touched?
          // But can toss out a byte-array if already deserialized & on disk
          // (no need for both forms).  Note no savings for Chunks, for which m==p._mem
          if( val.isPersisted() && m != null && p != null && !isChunk ) {
            val.freeMem();      // Toss serialized form, since can rebuild from POJO
            freed += val._max;
          }
          dirty_store(touched); // But may write it out later
          continue;             // Too young
        }
        // Spiller turned off?
        if( !H2O.ARGS.cleaner ) continue;

        // CNC - Memory cleaning turned off, except for Chunks
        // Too many POJOs are written to dynamically; cannot spill & reload
        // them without losing changes.

        // Should I write this value out to disk?
        // Should I further force it from memory?
        if( isChunk && !val.isPersisted() && !diskFull && ((Key)ok).home() ) { // && (force || (lazyPersist() && lazy_clean(key)))) {
          long now_ns = System.nanoTime();
          try { val.storePersist(); } // Write to disk
          catch( FileNotFoundException fnfe ) { continue; } // Can happen due to racing key delete/remove
          catch( IOException e ) {
            Log.warn( isDiskFull()
                      ? "Disk full! Disabling swapping to disk." + (force?" Memory low! Please free some space in " + H2O.ICE_ROOT + "!":"")
                      : "Disk swapping failed! " + e.getMessage());
            // Something is wrong so mark disk as full anyways so we do not
            // attempt to write again.  (will retry next run when memory is low)
            diskFull = true;
          }
          if( m == null ) m = val.rawMem();
          if( m != null ) cleaned += m.length; // Accumulate i/o bytes
          io_ns += System.nanoTime() - now_ns; // Accumulate i/o time
        }
        // And, under pressure, free all
        if( isChunk && force && (val.isPersisted() || !((Key)ok).home()) ) {
          val.freeMem ();  if( m != null ) freed += val._max;  m = null;
          val.freePOJO();  if( p != null ) freed += val._max;  p = null;
          if( isChunk ) freed -= val._max; // Double-counted freed mem for Chunks since val._pojo._mem & val._mem are the same.
        }
        // If we have both forms, toss the byte[] form - can be had by
        // serializing again.
        if( m != null && p != null && !isChunk ) {
          val.freeMem();
          freed += val._max;
        }

        // If a GC cycle happened and we can no longer alloc, start forcing
        // from RAM as we go
        force = (h._cached >= DESIRED || !MemoryManager.CAN_ALLOC); // Forced to clean
      }

      String s1 = "Cleaner pass took: "+PrettyPrint.msecs(System.currentTimeMillis()-now,true)+
                  ", spilled "+PrettyPrint.bytes(cleaned)+" in "+PrettyPrint.usecs(io_ns>>10);
      h = Histo.current(true); // Force a new histogram
      MemoryManager.set_goals("postclean",false);
      // No logging if under memory pressure: can deadlock the cleaner thread
      String s2 = h+" diski_o="+PrettyPrint.bytes(cleaned)+", freed="+(freed>>20)+"M, DESIRED="+(DESIRED>>20)+"M";
      if( MemoryManager.canAlloc() ) Log.debug(s1,s2);
      else                           System.err.println(s1+"\n"+s2);
      // For testing thread
      synchronized(this) {
        _did_sweep = true;
        if( DESIRED == -1 ) DESIRED = 0; // Turn off test-mode after 1 sweep
        notifyAll(); // Wake up testing thread
      }
    }
  }


  // Histogram class
  static class Histo {
    // Current best histogram
    static private volatile Histo H;

    // Return the current best histogram, recomputing in-place if it is getting
    // stale.  Synchronized so the same histogram can be called into here and
    // will be only computed into one-at-a-time.
    synchronized static Histo current( boolean force ) {
      final Histo h = H; // Grab current best histogram
      if( !force && System.currentTimeMillis() < h._when+2000 )
        return h; // It is recent; use it
      if( h != null && h._clean && _dirty==Long.MAX_VALUE )
        return h; // No change to the K/V store, so no point
      // Use last oldest value for computing the next histogram in-place
      return (H = new Histo(h==null ? 0 : h._oldest)); // Record current best histogram & return it
    }

    // Latest best-effort cached amount, without forcing a histogram to be
    // built nor blocking for one being in-progress.
    static long cached() { return H._cached; }
    static long swapped(){ return H._swapped;}

    final long[] _hs = new long[128];
    long _oldest; // Time of the oldest K/V discovered this pass
    long _eldest; // Time of the eldest K/V found in some prior pass
    long _hStep;  // Histogram step: (now-eldest)/histogram.length
    long _cached; // Total alive data in the histogram
    long _total;  // Total data in local K/V
    long _when;   // When was this histogram computed
    long _swapped;// On-disk stuff
    Value _vold;  // For assertions: record the oldest Value
    boolean _clean; // Was "clean" K/V when built?

    // Compute a histogram
    Histo( long eldest ) {
      Arrays.fill(_hs, 0);
      _when = System.currentTimeMillis();
      _eldest = eldest; // Eldest seen in some prior pass
      _hStep = Math.max(1,(_when-eldest)/_hs.length);
      boolean clean = _dirty==Long.MAX_VALUE;
      // Compute the hard way
      Object[] kvs = H2O.STORE.raw_array();
      long cached = 0; // Total K/V cached in ram
      long total = 0;  // Total K/V in local node
      long swapped=0;  // Total K/V persisted
      long oldest = Long.MAX_VALUE; // K/V with the longest time since being touched
      Value vold = null;
      // Start the walk at slot 2, because slots 0,1 hold meta-data
      for( int i=2; i<kvs.length; i += 2 ) {
        // In the raw backing array, Keys and Values alternate in slots
        Object ok = kvs[i], ov = kvs[i+1];
        if( !(ok instanceof Key  ) ) continue; // Ignore tombstones and Primes and null's
        if( !(ov instanceof Value) ) continue; // Ignore tombstones and Primes and null's
        Value val = (Value)ov;
        if( val.isNull() ) { Value.STORE_get(val._key); continue; } // Another flavor of NULL
        total += val._max;
        if( val.isPersisted() ) swapped += val._max;
        int len = 0;
        byte[] m = val.rawMem();
        Object p = val.rawPOJO();
        if( m != null ) len += val._max;
        if( p != null ) len += val._max;
        if( m != null && p instanceof Chunk ) len -= val._max; // Do not double-count Chunks
        if( len == 0 ) continue;
        cached += len; // Accumulate total amount of cached keys

        if( val._lastAccessedTime < oldest ) { // Found an older Value?
          vold = val; // Record oldest Value seen
          oldest = val._lastAccessedTime;
        }
        // Compute histogram bucket
        int idx = (int)((val._lastAccessedTime - eldest)/_hStep);
        if( idx < 0 ) idx = 0;
        else if( idx >= _hs.length ) idx = _hs.length-1;
        _hs[idx] += len;      // Bump histogram bucket
      }
      _cached = cached; // Total cached; NOTE: larger than sum of histogram buckets
      _total = total;   // Total used data
      _swapped = swapped;
      _oldest = oldest; // Oldest seen in this pass
      _vold = vold;
      _clean = clean && _dirty==Long.MAX_VALUE; // Looks like a clean K/V the whole time?
    }

    // Compute the time (in msec) for which we need to throw out things
    // to throw out enough things to hit the desired cached memory level.
    long clean_to( long desired ) {
      long age = _eldest;       // Age of bucket zero
      if( _cached < desired ) return age; // Already there; nothing to remove
      long s = 0;               // Total amount toss out
      for( long t : _hs ) {     // For all buckets...
        s += t;                 // Raise amount tossed out
        age += _hStep;          // Raise age beyond which you need to go
        if( _cached - s < desired ) break;
      }
      return age;
    }

    // Pretty print
    @Override public String toString() {
      long x = _eldest;
      long now = System.currentTimeMillis();
      return "H(cached:"+(_cached>>20)+"M, eldest:"+x+"L < +"+(_oldest-x)+"ms <...{"+_hStep+"ms}...< +"+(_hStep*_hs.length)+"ms < +"+(now-x)+")";
    }
  }
}
