package water;

import java.io.IOException;
import java.util.Arrays;
import water.persist.Persist;
import water.util.Log;

/** Store Cleaner: User-Mode Swap-To-Disk */

class Cleaner extends Thread {
  // msec time at which the STORE was dirtied.
  // Long.MAX_VALUE if clean.
  static private volatile long _dirty; // When was store dirtied

  static void dirty_store() { dirty_store(System.currentTimeMillis()); }
  static void dirty_store( long x ) {
    // Keep earliest dirty time seen
    if( x < _dirty ) _dirty = x;
  }

  static volatile long HEAP_USED_AT_LAST_GC;
  static volatile long TIME_AT_LAST_GC=System.currentTimeMillis();
  static private final Object _store_cleaner_lock = new Object();
  static void kick_store_cleaner() {
    synchronized(_store_cleaner_lock) { _store_cleaner_lock.notifyAll(); }
  }
  static void block_store_cleaner() {
    synchronized( _store_cleaner_lock ) {
      try { _store_cleaner_lock.wait(5000); } catch (InterruptedException ie) { }
    }
  }

  // Desired cache level. Set by the MemoryManager asynchronously.
  static volatile long DESIRED;
  // Histogram used by the Cleaner
  private final Histo _myHisto;

  boolean _diskFull = false;

  Cleaner() {
    super("MemCleaner");
    setDaemon(true);
    setPriority(MAX_PRIORITY-2);
    _dirty = Long.MAX_VALUE;  // Set to clean-store
    _myHisto = new Histo();   // Build/allocate a first histogram
    _myHisto.compute(0);      // Compute lousy histogram; find eldest
    H = _myHisto;             // Force to be the most recent
    _myHisto.histo(true);     // Force a recompute with a good eldest
    MemoryManager.set_goals("init",false);
  }

  static boolean lazyPersist(){ // free disk > our DRAM?
    return H2O.SELF._heartbeat.get_free_disk() > MemoryManager.MEM_MAX;
  }
  static boolean isDiskFull(){ // free disk space < 5K?
    long space = Persist.getIce().getUsableSpace();
    return space != Persist.UNKNOWN && space < (5 << 10);
  }

  @Override public void run() {
    boolean diskFull = false;
    while (true) {
      // Sweep the K/V store, writing out Values (cleaning) and free'ing
      // - Clean all "old" values (lazily, optimistically)
      // - Clean and free old values if above the desired cache level
      // Do not let optimistic cleaning get in the way of emergency cleaning.

      // Get a recent histogram, computing one as needed
      Histo h = _myHisto.histo(false);
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
      boolean force = (h._cached >= DESIRED); // Forced to clean
      if(force && diskFull)
        diskFull = isDiskFull();
      long clean_to_age = h.clean_to(force ? DESIRED : (DESIRED>>1));
      // If not forced cleaning, expand the cleaning age to allows Values
      // more than 5sec old
      if( !force ) clean_to_age = Math.max(clean_to_age,now-5000);

      // No logging if under memory pressure: can deadlock the cleaner thread
      String s = h+" DESIRED="+(DESIRED>>20)+"M dirtysince="+(now-dirty)+" force="+force+" clean2age="+(now-clean_to_age);
      if( MemoryManager.canAlloc() ) Log.debug(s);
      else                           System.err.println(s);
      long cleaned = 0;
      long freed = 0;

      // For faster K/V store walking get the NBHM raw backing array,
      // and walk it directly.
      Object[] kvs = H2O.STORE.raw_array();

      // Start the walk at slot 2, because slots 0,1 hold meta-data
      for( int i=2; i<kvs.length; i += 2 ) {
        // In the raw backing array, Keys and Values alternate in slots
        Object ok = kvs[i+0], ov = kvs[i+1];
        if( !(ok instanceof Key  ) ) continue; // Ignore tombstones and Primes and null's
        Key key = (Key )ok;
        if( !(ov instanceof Value) ) continue; // Ignore tombstones and Primes and null's
        Value val = (Value)ov;
        byte[] m = val.rawMem();
        Object p = val.rawPOJO();
        if( m == null && p == null ) continue; // Nothing to throw out

        if( val.isLockable() ) continue; // we do not want to throw out Lockables.

        // If not Iced, we can only toss out 1 of 2 forms, since we cannot simply reload from disk.
        if( !val.onICE() ) {
          // But can toss out a byte-array if already deserialized
          // (no need for both forms).
          if( m != null && p != null ) { val.freeMem(); freed += val._max; }
          continue; // Cannot throw out
        }

        // Ignore things younger than the required age.  In particular, do
        // not spill-to-disk all dirty things we find.
        long touched = val._lastAccessedTime;
        if( touched > clean_to_age ) {
          // But can toss out a byte-array if already deserialized & on disk
          // (no need for both forms).
          if( val.isPersisted() &&
              m != null && p != null ) { val.freeMem(); freed += val._max; }
          dirty_store(touched); // But may write it out later
          continue;
        }

        // Should I write this value out to disk?
        // Should I further force it from memory?
        if(!val.isPersisted() && !diskFull && (force || (lazyPersist() && lazy_clean(key)))) {
          try {
            val.storePersist(); // Write to disk
            if( m == null ) m = val.rawMem();
            if( m != null ) cleaned += m.length;
          } catch(IOException e) {
            if( isDiskFull() )
              Log.warn("Disk full! Disabling swapping to disk." + (force?" Memory low! Please free some space in " + Persist.getIce().getPath() + "!":""));
            else
              Log.warn("Disk swapping failed! " + e.getMessage());
            // Something is wrong so mark disk as full anyways so we do not
            // attempt to write again.  (will retry next run when memory is low)
            diskFull = true;
          }
        }
        // And, under pressure, free all
        if( force && val.isPersisted() ) {
          val.freeMem ();  if( m != null ) freed += val._max;  m = null;
          val.freePOJO();  if( p != null ) freed += val._max;  p = null;
        }
        // If we have both forms, toss the byte[] form - can be had by
        // serializing again.
        if( m != null && p != null ) {
          val.freeMem();
          freed += val._max;
        }
      }

      h = _myHisto.histo(true); // Force a new histogram
      MemoryManager.set_goals("postclean",false);
      // No logging if under memory pressure: can deadlock the cleaner thread
      String s2 = h+" cleaned="+(cleaned>>20)+"M, freed="+(freed>>20)+"M, DESIRED="+(DESIRED>>20)+"M";
      if( MemoryManager.canAlloc() ) Log.debug(s2);
      else                           System.err.println(s2);
    }
  }

  // Rules on when to write & free a Key, when not under memory pressure.
  boolean lazy_clean( Key key ) {
    // Only data chunks are worth tossing out even lazily.
    if( !key.isChunkKey() ) // Not arraylet?
      return false; // Not enough savings to write it with mem-pressure to force us
    // If this is a chunk of a system-defined array, then assume it has
    // short lifetime, and we do not want to spin the disk writing it
    // unless we're under memory pressure.
    Key veckey = key.getVecKey();
    return veckey.user_allowed(); // Write user keys but not system keys
  }

  // Current best histogram
  static private volatile Histo H;

  // Histogram class
  static class Histo {
    final long[] _hs = new long[128];
    long _oldest; // Time of the oldest K/V discovered this pass
    long _eldest; // Time of the eldest K/V found in some prior pass
    long _hStep; // Histogram step: (now-eldest)/histogram.length
    long _cached; // Total alive data in the histogram
    long _when; // When was this histogram computed
    Value _vold; // For assertions: record the oldest Value
    boolean _clean; // Was "clean" K/V when built?

    // Return the current best histogram
    static Histo best_histo() { return H; }

    // Return the current best histogram, recomputing in-place if it is
    // getting stale. Synchronized so the same histogram can be called into
    // here and will be only computed into one-at-a-time.
    synchronized Histo histo( boolean force ) {
      final Histo h = H; // Grab current best histogram
      if( !force && System.currentTimeMillis() < h._when+100 )
        return h; // It is recent; use it
      if( h._clean && _dirty==Long.MAX_VALUE )
        return h; // No change to the K/V store, so no point
      compute(h._oldest); // Use last oldest value for computing the next histogram in-place
      return (H = this);      // Record current best histogram & return it
    }

    // Compute a histogram
    void compute( long eldest ) {
      Arrays.fill(_hs, 0);
      _when = System.currentTimeMillis();
      _eldest = eldest; // Eldest seen in some prior pass
      _hStep = Math.max(1,(_when-eldest)/_hs.length);
      boolean clean = _dirty==Long.MAX_VALUE;
      // Compute the hard way
      Object[] kvs = H2O.STORE.raw_array();
      long cached = 0; // Total K/V cached in ram
      long oldest = Long.MAX_VALUE; // K/V with the longest time since being touched
      Value vold = null;
      // Start the walk at slot 2, because slots 0,1 hold meta-data
      for( int i=2; i<kvs.length; i += 2 ) {
        // In the raw backing array, Keys and Values alternate in slots
        Object ok = kvs[i+0], ov = kvs[i+1];
        if( !(ok instanceof Key  ) ) continue; // Ignore tombstones and Primes and null's
        if( !(ov instanceof Value) ) continue; // Ignore tombstones and Primes and null's
        Value val = (Value)ov;
        int len = 0;
        if( val.rawMem () != null ) len += val._max;
        if( val.rawPOJO() != null ) len += val._max;
        if( len == 0 ) continue;
        cached += len; // Accumulate total amount of cached keys

        if( !val.onICE() )
          continue;           // Cannot throw out (so not in histogram buckets)

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
      _oldest = oldest; // Oldest seen in this pass
      _vold = vold;
      _clean = clean && _dirty==Long.MAX_VALUE; // Looks like a clean K/V the whole time?
    }

    // Compute the time (in msec) for which we need to throw out things
    // to throw out enough things to hit the desired cached memory level.
    long clean_to( long desired ) {
      if( _cached < desired ) return Long.MAX_VALUE; // Already there; nothing to remove
      long age = _eldest; // Age of bucket zero
      long s = 0; // Total amount toss out
      for( long t : _hs ) { // For all buckets...
        s += t; // Raise amount tossed out
        age += _hStep; // Raise age beyond which you need to go
        if( _cached - s < desired ) break;
      }
      return age;
    }

    // Pretty print
    @Override public String toString() {
      long x = _eldest;
      long now = System.currentTimeMillis();
      return "H("+(_cached>>20)+"M, "+x+"ms < +"+(_oldest-x)+"ms <...{"+_hStep+"ms}...< +"+(_hStep*128)+"ms < +"+(now-x)+")";
    }
  }
}
