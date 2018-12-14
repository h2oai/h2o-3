package water;

import jsr166y.ForkJoinPool;
import jsr166y.ForkJoinPool.ManagedBlocker;
import water.util.Log;
import water.util.PrettyPrint;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import java.lang.management.*;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages memory assigned to key/value pairs. All byte arrays used in
 * keys/values should be allocated through this class - otherwise we risking
 * running out of java memory, and throw unexpected OutOfMemory errors. The
 * theory here is that *most* allocated bytes are allocated in large chunks by
 * allocating new Values - with large backing arrays. If we intercept these
 * allocation points, we cover most Java allocations. If such an allocation
 * might trigger an OOM error we first free up some other memory.
 *
 * MemoryManager monitors memory used by the K/V store (by walking through the
 * store (see Cleaner) and overall heap usage by hooking into gc.
 *
 * Memory is freed if either the cached memory is above the limit or if the
 * overall heap usage is too high (in which case we want to use less mem for
 * cache). There is also a lower limit on the amount of cache so that we never
 * delete all the cache and therefore some computation should always be able to
 * progress.
 *
 * The amount of memory to be freed is determined as the max of cached mem above
 * the limit and heap usage above the limit.
 *
 * One of the primary control inputs is FullGC cycles: we check heap usage and
 * set guidance for cache levels. We assume after a FullGC that the heap only
 * has POJOs (Plain Old Java Objects, unknown size) and K/V Cached stuff
 * (counted by us). We compute the free heap as MEM_MAX-heapUsage (after GC),
 * and we compute POJO size as (heapUsage - K/V cache usage).
 *
 * @author tomas
 * @author cliffc
 */
abstract public class MemoryManager {
  // Track timestamp of last oom log to avoid spamming the logs with junk.
  private static volatile long oomLastLogTimestamp = 0;
  private static final long SIXTY_SECONDS_IN_MILLIS = 60 * 1000;

  // max heap memory
  public static final long MEM_MAX = Runtime.getRuntime().maxMemory();

  // Callbacks from GC
  static final HeapUsageMonitor HEAP_USAGE_MONITOR = new HeapUsageMonitor();

  // Keep the K/V store below this threshold AND this is the FullGC call-back
  // threshold - which is limited in size to the old-gen pool size.
  static long MEM_CRITICAL;

  // Block allocations?
  static volatile boolean CAN_ALLOC = true;
  private static volatile boolean MEM_LOW_CRITICAL = false;

  // Lock for blocking on allocations
  private static final Object _lock = new Object();

  // A monotonically increasing total count memory allocated via MemoryManager.
  // Useful in tracking total memory consumed by algorithms - just ask for the
  // before & after amounts and diff them.

  static void setMemGood() {
    if( CAN_ALLOC ) return;
    synchronized(_lock) { CAN_ALLOC = true; _lock.notifyAll(); }
    // NO LOGGING UNDER LOCK!
    Log.warn("Continuing after swapping");
  }
  static void setMemLow() {
    if( !H2O.ARGS.cleaner ) return; // Cleaner turned off
    if( !CAN_ALLOC ) return;
    synchronized(_lock) { CAN_ALLOC = false; }
    // NO LOGGING UNDER LOCK!
    Log.warn("Pausing to swap to disk; more memory may help");
  }
  static boolean canAlloc() { return CAN_ALLOC; }

  static void set_goals( String msg, boolean oom){
    set_goals(msg, oom, 0);
  }
  // Set K/V cache goals.
  // Allow (or disallow) allocations.
  // Called from the Cleaner, when "cacheUsed" has changed significantly.
  // Called from any FullGC notification, and HEAP/POJO_USED changed.
  // Called on any OOM allocation
  static void set_goals( String msg, boolean oom , long bytes) {
    // Our best guess of free memory, as of the last GC cycle
    final long heapUsedGC = Cleaner.HEAP_USED_AT_LAST_GC;
    final long timeGC = Cleaner.TIME_AT_LAST_GC;
    final long freeHeap = MEM_MAX - heapUsedGC;
    assert freeHeap >= 0 : "I am really confused about the heap usage; MEM_MAX="+MEM_MAX+" heapUsedGC="+heapUsedGC;
    // Current memory held in the K/V store.
    final long cacheUsageGC = Cleaner.KV_USED_AT_LAST_GC;
    // Our best guess of POJO object usage: Heap_used minus cache used
    final long pojoUsedGC = Math.max(heapUsedGC - cacheUsageGC,0);

    // Block allocations if:
    // the cache is > 7/8 MEM_MAX, OR
    // we cannot allocate an equal amount of POJOs, pojoUsedGC > freeHeap.
    // Decay POJOS_USED by 1/8th every 5 sec: assume we got hit with a single
    // large allocation which is not repeating - so we do not need to have
    // double the POJO amount.
    // Keep at least 1/8th heap for caching.
    // Emergency-clean the cache down to the blocking level.
    long d = MEM_CRITICAL;      // Block-allocation level; cache can grow till this
    // Decay POJO amount
    long p = pojoUsedGC;
    long age = (System.currentTimeMillis() - timeGC); // Age since last FullGC
    age = Math.min(age,10*60*1000 ); // Clip at 10mins
    while( (age-=5000) > 0 ) p = p-(p>>3); // Decay effective POJO by 1/8th every 5sec
    d -= 2*p - bytes; // Allow for the effective POJO, and again to throttle GC rate (and allow for this allocation)
    d = Math.max(d,MEM_MAX>>3); // Keep at least 1/8th heap
    if( Cleaner.DESIRED != -1 ) // Set to -1 only for OOM/Cleaner testing.  Never negative normally
      Cleaner.DESIRED = d;      // Desired caching level
    final long cacheUsageNow = Cleaner.Histo.cached();

    boolean skipThisLogMessageToAvoidSpammingTheLogs = false;
    String m="";
    if( cacheUsageNow > Cleaner.DESIRED ) {
      m = (CAN_ALLOC?"Swapping!  ":"blocked:   ");
      if( oom ) setMemLow(); // Stop allocations; trigger emergency clean
      Cleaner.kick_store_cleaner();
    } else { // Else we are not *emergency* cleaning, but may be lazily cleaning.
      setMemGood();             // Cache is below desired level; unblock allocations
      if( oom ) {               // But still have an OOM?
        m = "Unblock allocations; cache below desired, but also OOM: ";
        // Means the heap is full of uncached POJO's - which cannot be spilled.
        // Here we enter the zone of possibly dieing for OOM.  There's no point
        // in blocking allocations, as no more memory can be freed by more
        // cache-flushing.  Might as well proceed on a "best effort" basis.

        long now = System.currentTimeMillis();
        if ((now - oomLastLogTimestamp) >= SIXTY_SECONDS_IN_MILLIS) {
          oomLastLogTimestamp = now;
        }
        else {
          skipThisLogMessageToAvoidSpammingTheLogs = true;
        }
      } else { 
        m = "MemGood:   "; // Cache is low enough, room for POJO allocation - full steam ahead!
      }
    }

    if (skipThisLogMessageToAvoidSpammingTheLogs) {
      return;
    }

    // No logging if under memory pressure: can deadlock the cleaner thread
    String s = m+msg+", (K/V:"+PrettyPrint.bytes(cacheUsageGC)+" + POJO:"+PrettyPrint.bytes(pojoUsedGC)+" + FREE:"+PrettyPrint.bytes(freeHeap)+" == MEM_MAX:"+PrettyPrint.bytes(MEM_MAX)+"), desiredKV="+PrettyPrint.bytes(Cleaner.DESIRED)+(oom?" OOM!":" NO-OOM");
    if( CAN_ALLOC ) { if( oom ) Log.warn(s); else Log.debug(s); }
    else            System.err.println(s);
  }

  /** Monitors the heap usage after full gc run and tells Cleaner to free memory
   *  if mem usage is too high.  Stops new allocation if mem usage is critical.
   *  @author tomas   */
  private static class HeapUsageMonitor implements javax.management.NotificationListener {
    MemoryMXBean _allMemBean = ManagementFactory.getMemoryMXBean(); // general

    // Determine the OldGen GC pool size - which is saved in MEM_CRITICAL as
    // the max desirable K/V store size.
    HeapUsageMonitor() {
      int c = 0;
      for( MemoryPoolMXBean m : ManagementFactory.getMemoryPoolMXBeans() ) {
        if( m.getType() != MemoryType.HEAP ) // only interested in HEAP
          continue;
        if( m.isCollectionUsageThresholdSupported()
            && m.isUsageThresholdSupported()) {
          // Really idiotic API: no idea what the usageThreshold is, so I have
          // to guess.  Start high, catch IAE & lower by 1/8th and try again.
          long gc_callback = MEM_MAX;
          while( true ) {
            try {
              m.setCollectionUsageThreshold(gc_callback);
              break;
            } catch( IllegalArgumentException iae ) {
              // Expected IAE: means we used too high a callback level
              gc_callback -= (gc_callback>>3);
            }
          }
          m.setCollectionUsageThreshold(1); // Call back for every fullgc
          NotificationEmitter emitter = (NotificationEmitter) _allMemBean;
          emitter.addNotificationListener(this, null, m);
          ++c;
          MEM_CRITICAL = gc_callback; // Set old-gen heap level
        }
      }
      assert c == 1;
    }

    /** Callback routine called by JVM after full gc run. Has two functions:
     *  1) sets the amount of memory to be cleaned from the cache by the Cleaner
     *  2) sets the CAN_ALLOC flag to false if memory level is critical  */
    @Override public void handleNotification(Notification notification, Object handback) {
      String notifType = notification.getType();
      if( !notifType.equals(MemoryNotificationInfo.MEMORY_COLLECTION_THRESHOLD_EXCEEDED)) return;
      // Memory used after this FullGC
      Cleaner.TIME_AT_LAST_GC = System.currentTimeMillis();
      Cleaner.HEAP_USED_AT_LAST_GC = _allMemBean.getHeapMemoryUsage().getUsed();
      Cleaner.KV_USED_AT_LAST_GC = Cleaner.Histo.cached();
      MEM_LOW_CRITICAL = Cleaner.HEAP_USED_AT_LAST_GC > 0.75*MEM_MAX;
      Log.debug("GC CALLBACK: "+Cleaner.TIME_AT_LAST_GC+", USED:"+PrettyPrint.bytes(Cleaner.HEAP_USED_AT_LAST_GC)+", CRIT: "+MEM_LOW_CRITICAL);
      set_goals("GC CALLBACK",MEM_LOW_CRITICAL);
      //if( MEM_LOW_CRITICAL ) { // emergency measure - really low on memory, stop allocations right now!
      //  setMemLow();           // In-use memory is > 3/4 heap; block allocations
      //} else if( Cleaner.HEAP_USED_AT_LAST_GC < (MEM_MAX - (MEM_MAX >> 1)) )
      //  setMemGood(); // In use memory is < 1/2 heap; allow allocations even if Cleaner is still running
    }
  }


  // Allocates memory with cache management
  // Will block until there is enough available memory.
  // Catches OutOfMemory, clears cache & retries.
  static Object malloc(int elems, long bytes, int type, Object orig, int from ) {
    return malloc(elems,bytes,type,orig,from,false);
  }
  static Object malloc(int elems, long bytes, int type, Object orig, int from , boolean force) {
    assert elems >= 0 : "Bad size " + elems; // is 0 okay?!
    // Do not assert on large-size here.  RF's temp internal datastructures are
    // single very large arrays.
    //assert bytes < Value.MAX : "malloc size=0x"+Long.toHexString(bytes);
    while( true ) {
      if( (!MEM_LOW_CRITICAL && !force) && !CAN_ALLOC && // Not allowing allocations?
          bytes > 256 &&        // Allow tiny ones in any case
          // To prevent deadlock, we cannot block the cleaner thread in any
          // case.  This is probably an allocation for logging (ouch! shades of
          // logging-induced deadlock!) which will probably be recycled quickly.
          !(Thread.currentThread() instanceof Cleaner) ) {
        synchronized(_lock) {
          try { _lock.wait(300*1000); } catch (InterruptedException ex) { }
        }
      }
      try {
        switch( type ) {
        case  1: return new byte   [elems];
        case  2: return new short  [elems];
        case  4: return new int    [elems];
        case  8: return new long   [elems];
        case  5: return new float  [elems];
        case  9: return new double [elems];
        case  0: return new boolean[elems];
        case 10: return new Object [elems];
        case -1: return Arrays.copyOfRange((byte  [])orig,from,elems);
        case -4: return Arrays.copyOfRange((int   [])orig,from,elems);
        case -5: return Arrays.copyOfRange((float [])orig,from,elems);
        case -8: return Arrays.copyOfRange((long  [])orig,from,elems);
        case -9: return Arrays.copyOfRange((double[])orig,from,elems);
        default: throw H2O.fail();
        }
      }
      catch( OutOfMemoryError e ) {
        // Do NOT log OutOfMemory, it is expected and unavoidable and handled
        // in most cases by spilling to disk.
        if( Cleaner.isDiskFull() ) {
          Log.err("Disk full, space left = " + Cleaner.availableDiskSpace());
          UDPRebooted.suicide(UDPRebooted.T.oom, H2O.SELF);
        }
      }
      set_goals("OOM",true, bytes); // Low memory; block for swapping
    }
  }

  // Allocates memory with cache management
  public static byte   [] malloc1 (int size) { return malloc1(size,false); }
  public static byte   [] malloc1 (int size, boolean force) 
                                             { return (byte   [])malloc(size,size*1, 1,null,0,force); }
  public static short  [] malloc2 (int size) { return (short  [])malloc(size,size*2L, 2,null,0); }
  public static int    [] malloc4 (int size) { return (int    [])malloc(size,size*4L, 4,null,0); }
  public static long   [] malloc8 (int size) { return (long   [])malloc(size,size*8L, 8,null,0); }
  public static float  [] malloc4f(int size) { return (float  [])malloc(size,size*4L, 5,null,0); }
  public static double [] malloc8d(int size) {
    if(size < 32) try { // fast path for small arrays (e.g. histograms in gbm)
      return new double [size];
    } catch (OutOfMemoryError oom){/* fall through */}
    return (double [])malloc(size,size*8L, 9,null,0);
  }
  public static double [][] malloc8d(int m, int n) {
    double [][] res = new double[m][];
    for(int i = 0; i < m; ++i)
      res[i] = malloc8d(n);
    return res;
  }
  public static boolean[] mallocZ (int size) { return (boolean[])malloc(size,size  , 0,null,0); }
  public static Object [] mallocObj(int size){ return (Object [])malloc(size,size*8L,10,null,0,false); }
  public static byte   [] arrayCopyOfRange(byte  [] orig, int from, int sz) { return (byte  []) malloc(sz,(sz-from)  ,-1,orig,from); }
  public static int    [] arrayCopyOfRange(int   [] orig, int from, int sz) { return (int   []) malloc(sz,(sz-from)*4,-4,orig,from); }
  public static long   [] arrayCopyOfRange(long  [] orig, int from, int sz) { return (long  []) malloc(sz,(sz-from)*8,-8,orig,from); }
  public static float  [] arrayCopyOfRange(float  [] orig, int from, int sz){ return (float []) malloc(sz,(sz-from)*8,-5,orig,from); }
  public static double [] arrayCopyOfRange(double[] orig, int from, int sz) { return (double[]) malloc(sz,(sz-from)*8,-9,orig,from); }
  public static byte   [] arrayCopyOf( byte  [] orig, int sz) { return arrayCopyOfRange(orig,0,sz); }
  public static int    [] arrayCopyOf( int   [] orig, int sz) { return arrayCopyOfRange(orig,0,sz); }
  public static long   [] arrayCopyOf( long  [] orig, int sz) { return arrayCopyOfRange(orig,0,sz); }
  public static float  [] arrayCopyOf( float [] orig, int sz) { return arrayCopyOfRange(orig,0,sz); }
  public static double [] arrayCopyOf( double[] orig, int sz) { return arrayCopyOfRange(orig,0,sz); }

  // Memory available for tasks (we assume 3/4 of the heap is available for tasks)
  static final AtomicLong _taskMem = new AtomicLong(MEM_MAX-(MEM_MAX>>2));

  /**
   * Try to reserve memory needed for task execution and return true if
   * succeeded.  Tasks have a shared pool of memory which they should ask for
   * in advance before they even try to allocate it.
   *
   * This method is another backpressure mechanism to make sure we do not
   * exhaust system's resources by running too many tasks at the same time.
   * Tasks are expected to reserve memory before proceeding with their
   * execution and making sure they release it when done.
   *
   * @param m - requested number of bytes
   * @return true if there is enough free memory
   */
  static boolean tryReserveTaskMem(long m){
    if(!CAN_ALLOC)return false;
    if( m == 0 ) return true;
    assert m >= 0:"m < 0: " + m;
    long current = _taskMem.addAndGet(-m);
    if(current < 0){
      _taskMem.addAndGet(m);
      return false;
    }
    return true;
  }
  private static Object _taskMemLock = new Object();
  static void reserveTaskMem(long m){
    final long bytes = m;
    while(!tryReserveTaskMem(bytes)){
      try {
        ForkJoinPool.managedBlock(new ManagedBlocker() {
          @Override public boolean isReleasable() {return _taskMem.get() >= bytes;}
          @Override public boolean block() throws InterruptedException {
            synchronized(_taskMemLock){
              try {_taskMemLock.wait();} catch( InterruptedException e ) {}
            }
            return isReleasable();
          }
        });
      } catch (InterruptedException e){ Log.throwErr(e); }
    }
  }

  /**
   * Free the memory successfully reserved by task.
   * @param m
   */
  static void freeTaskMem(long m){
    if(m == 0)return;
    _taskMem.addAndGet(m);
    synchronized(_taskMemLock){
      _taskMemLock.notifyAll();
    }
  }
}
