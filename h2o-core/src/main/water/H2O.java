package water;

import java.lang.management.ManagementFactory;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import jsr166y.*;
import water.init.*;
import water.nbhm.NonBlockingHashMap;
import water.util.*;

/**
* Start point for creating or joining an <code>H2O</code> Cloud.
*
* @author <a href="mailto:cliffc@0xdata.com"></a>
* @version 1.0
*/
final public class H2O {

  static final AbstractBuildVersion ABV;
  static {
    AbstractBuildVersion abv = AbstractBuildVersion.UNKNOWN_VERSION;
    try {
      Class klass = Class.forName("water.BuildVersion");
      java.lang.reflect.Constructor constructor = klass.getConstructor();
      abv = (AbstractBuildVersion) constructor.newInstance();
    } catch (Exception ignore) { }
    ABV = abv;
  }

  // Atomically set once during startup.  Guards against repeated startups.
  static AtomicLong START_TIME_MILLIS = new AtomicLong(); // When did main() run

  // Used to gate default worker threadpool sizes
  static final int NUMCPUS = Runtime.getRuntime().availableProcessors();
  // Best-guess process ID
  public static long PID = -1L;

  // Convenience error
  public static RuntimeException unimpl() { return new RuntimeException("unimplemented"); }
  public static RuntimeException fail() { return new RuntimeException("do not call"); }

  // --------------------------------------------------------------------------
  // The worker pools - F/J pools with different priorities.

  // These priorities are carefully ordered and asserted for... modify with
  // care.  The real problem here is that we can get into cyclic deadlock
  // unless we spawn a thread of priority "X+1" in order to allow progress
  // on a queue which might be flooded with a large number of "<=X" tasks.
  //
  // Example of deadlock: suppose TaskPutKey and the Invalidate ran at the same
  // priority on a 2-node cluster.  Both nodes flood their own queues with
  // writes to unique keys, which require invalidates to run on the other node.
  // Suppose the flooding depth exceeds the thread-limit (e.g. 99); then each
  // node might have all 99 worker threads blocked in TaskPutKey, awaiting
  // remote invalidates - but the other nodes' threads are also all blocked
  // awaiting invalidates!
  //
  // We fix this by being willing to always spawn a thread working on jobs at
  // priority X+1, and guaranteeing there are no jobs above MAX_PRIORITY -
  // i.e., jobs running at MAX_PRIORITY cannot block, and when those jobs are
  // done, the next lower level jobs get unblocked, etc.
  static final byte        MAX_PRIORITY = Byte.MAX_VALUE-1;
  static final byte    ACK_ACK_PRIORITY = MAX_PRIORITY-0;
  static final byte        ACK_PRIORITY = MAX_PRIORITY-1;
  static final byte   DESERIAL_PRIORITY = MAX_PRIORITY-2;
  static final byte INVALIDATE_PRIORITY = MAX_PRIORITY-2;
  static final byte    GET_KEY_PRIORITY = MAX_PRIORITY-3;
  static final byte    PUT_KEY_PRIORITY = MAX_PRIORITY-4;
  static final byte     ATOMIC_PRIORITY = MAX_PRIORITY-5;
  static final byte        GUI_PRIORITY = MAX_PRIORITY-6;
  static final byte     MIN_HI_PRIORITY = MAX_PRIORITY-6;
  static final byte        MIN_PRIORITY = 0;

  // F/J threads that remember the priority of the last task they started
  // working on.
  static class FJWThr extends ForkJoinWorkerThread {
    int _priority;
    FJWThr(ForkJoinPool pool) {
      super(pool);
      setPriority( ((ForkJoinPool2)pool)._priority == Thread.MIN_PRIORITY
                   ? Thread.NORM_PRIORITY-1
                   : Thread. MAX_PRIORITY-1 );
    }
  }
  // Factory for F/J threads, with cap's that vary with priority.
  static class FJWThrFact implements ForkJoinPool.ForkJoinWorkerThreadFactory {
    private final int _cap;
    FJWThrFact( int cap ) { _cap = cap; }
    @Override public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
      int cap = _cap==-1 ? ARGS.nthreads : _cap;
      return pool.getPoolSize() <= cap ? new FJWThr(pool) : null;
    }
  }

  // A standard FJ Pool, with an expected priority level.
  private static class ForkJoinPool2 extends ForkJoinPool {
    final int _priority;
    ForkJoinPool2(int p, int cap) { super(NUMCPUS,new FJWThrFact(cap),null,p!=MIN_PRIORITY); _priority = p; }
    H2OCountedCompleter poll() { return (H2OCountedCompleter)pollSubmission(); }
  }

  // Normal-priority work is generally directly-requested user ops.
  private static final ForkJoinPool2 FJP_NORM = new ForkJoinPool2(MIN_PRIORITY,-1);
  // Hi-priority work, sorted into individual queues per-priority.
  // Capped at a small number of threads per pool.
  private static final ForkJoinPool2 FJPS[] = new ForkJoinPool2[MAX_PRIORITY+1];
  static {
    // Only need 1 thread for the AckAck work, as it cannot block
    FJPS[ACK_ACK_PRIORITY] = new ForkJoinPool2(ACK_ACK_PRIORITY,1);
    for( int i=MIN_HI_PRIORITY+1; i<MAX_PRIORITY; i++ )
      FJPS[i] = new ForkJoinPool2(i,NUMCPUS); // All CPUs, but no more for blocking purposes
    FJPS[GUI_PRIORITY] = new ForkJoinPool2(GUI_PRIORITY,2);
    FJPS[0] = FJP_NORM;
  }

  // Easy peeks at the low FJ queue
  static int getLoQueue (     ) { return FJP_NORM.getQueuedSubmissionCount();}
  static int loQPoolSize(     ) { return FJP_NORM.getPoolSize();             }
  static int getHiQueue (int i) { return FJPS[i+MIN_HI_PRIORITY].getQueuedSubmissionCount();}
  static int hiQPoolSize(int i) { return FJPS[i+MIN_HI_PRIORITY].getPoolSize();             }

  // Submit to the correct priority queue
  static public H2OCountedCompleter submitTask( H2OCountedCompleter task ) {
    int priority = task.priority();
    assert MIN_PRIORITY <= priority && priority <= MAX_PRIORITY;
    FJPS[priority].submit(task);
    return task;
  }

  // Simple wrapper over F/J CountedCompleter to support priority queues.  F/J
  // queues are simple unordered (and extremely light weight) queues.  However,
  // we frequently need priorities to avoid deadlock and to promote efficient
  // throughput (e.g. failure to respond quickly to TaskGetKey can block an
  // entire node for lack of some small piece of data).  So each attempt to do
  // lower-priority F/J work starts with an attempt to work & drain the
  // higher-priority queues.
  static public abstract class H2OCountedCompleter<T extends H2OCountedCompleter> extends CountedCompleter implements Cloneable, Freezable {
    H2OCountedCompleter(){}
    H2OCountedCompleter(H2OCountedCompleter completer){super(completer);}

    // Once per F/J task, drain the high priority queue before doing any low
    // priority work.
    @Override final public void compute() {
      FJWThr t = (FJWThr)Thread.currentThread();
      int pp = ((ForkJoinPool2)t.getPool())._priority;
      assert  priority() == pp; // Job went to the correct queue?
      assert t._priority <= pp; // Thread attempting the job is only a low-priority?
      // Drain the high priority queues before the normal F/J queue
      try {
        for( int p = MAX_PRIORITY; p > pp; p-- ) {
          if( FJPS[p] == null ) break;
          H2OCountedCompleter h2o = FJPS[p].poll();
          if( h2o != null ) {     // Got a hi-priority job?
            t._priority = p;      // Set & do it now!
            Thread.currentThread().setPriority(Thread.MAX_PRIORITY-1);
            h2o.compute2();       // Do it ahead of normal F/J work
            p++;                  // Check again the same queue
          }
        }
      } finally {
        t._priority = pp;
        if( pp == MIN_PRIORITY ) Thread.currentThread().setPriority(Thread.NORM_PRIORITY-1);
      }
      // Now run the task as planned
      compute2();
    }
    // Do the actually intended work
    protected abstract void compute2();
    @Override public boolean onExceptionalCompletion(Throwable ex, CountedCompleter caller){
      if(!(ex instanceof Job.JobCancelledException) && this.getCompleter() == null)
        ex.printStackTrace();
      return true;
    }
    // In order to prevent deadlock, threads that block waiting for a reply
    // from a remote node, need the remote task to run at a higher priority
    // than themselves.  This field tracks the required priority.
    byte priority() { return MIN_PRIORITY; }
    @Override final public T clone(){
      try { return (T)super.clone(); }
      catch( CloneNotSupportedException e ) { throw Log.throwErr(e); }
    }

    // The serialization flavor / delegate.  Lazily set on first use.
    private transient short _ice_id;

    // Return the icer for this instance+class.  Will set on 1st use.
    protected Icer<T> icer() {
      int id = _ice_id;
      return TypeMap.getIcer(id!=0 ? id : (_ice_id=(short)TypeMap.onIce(this)),this); 
    }
    @Override final public AutoBuffer write(AutoBuffer ab) { return icer().write(ab,(T)this); }
    @Override final public T read (AutoBuffer ab) { return icer().read (ab,(T)this); }
    @Override final public int frozenType() { return icer().frozenType();   }
    @Override       public AutoBuffer write_impl( AutoBuffer ab ) { return ab; }
    @Override       public T read_impl( AutoBuffer ab ) { return (T)this; }
  }


  // --------------------------------------------------------------------------
  // List of arguments.
  public static OptArgs ARGS = new OptArgs();
  public static class OptArgs extends Arguments.Opt {
    boolean h = false;
    boolean help = false;
    boolean version = false;

    // Common config options
    public String name = System.getProperty("user.name"); // Cloud name
    public String flatfile;     // List of cluster IP addresses
    public int    port;         // Browser/API/HTML port
    public int    h2o_port;     // port+1
    public String ip;           // Named IP4/IP6 address instead of the default
    public String network;      // Network specification for acceptable interfaces to bind to.
    String ice_root;     // ice root directory; where temp files go
    String log_level;    // One of DEBUG, INFO, WARN, ERRR.  Null is INFO.

    // Less common config options
    int nthreads=Math.max(99,10*NUMCPUS); // Max number of F/J threads in the low-priority batch queue
    boolean random_udp_drop; // test only, randomly drop udp incoming
    boolean requests_log = true; // logging of Web requests
    boolean check_rest_params = true; // enable checking unused/unknown REST params

    // HDFS & AWS
    public String hdfs; // HDFS backend
    String hdfs_version; // version of the filesystem
    public String hdfs_config; // configuration file of the HDFS
    String hdfs_skip = null; // used by hadoop driver to not unpack and load any hdfs jar file at runtime.
    public String aws_credentials; // properties file for aws credentials
  }

  public static int H2O_PORT; // Both TCP & UDP cluster ports
  public static int API_PORT; // RequestServer and the API HTTP port

  // The multicast discovery port
  public static MulticastSocket  CLOUD_MULTICAST_SOCKET;
  public static NetworkInterface CLOUD_MULTICAST_IF;
  public static InetAddress      CLOUD_MULTICAST_GROUP;
  public static int              CLOUD_MULTICAST_PORT ;

  // Myself, as a Node in the Cloud
  public static H2ONode SELF = null;
  public static InetAddress SELF_ADDRESS;

  // Place to store temp/swap files
  public static URI ICE_ROOT;
  public static String DEFAULT_ICE_ROOT() {
    String username = System.getProperty("user.name");
    if (username == null) username = "";
    String u2 = username.replaceAll(" ", "_");
    if (u2.length() == 0) u2 = "unknown";
    return "/tmp/h2o-" + u2;
  }

  // Static list of acceptable Cloud members
  public static HashSet<H2ONode> STATIC_H2OS = null;

  // Reverse cloud index to a cloud; limit of 256 old clouds.
  static private final H2O[] CLOUDS = new H2O[256];

  // Enables debug features like more logging and multiple instances per JVM
  static final String DEBUG_ARG = "h2o.debug";
  static final boolean DEBUG = System.getProperty(DEBUG_ARG) != null;

  static void printHelp() {
    String s =
    "Start an H2O node.\n" +
    "\n" +
    "Usage:  java [-Xmx<size>] -jar h2o.jar [options]\n" +
    "        (Note that every option has a default and is optional.)\n" +
    "\n" +
    "    -h | -help\n" +
    "          Print this help.\n" +
    "\n" +
    "    -version\n" +
    "          Print version info and exit.\n" +
    "\n" +
    "    -name <h2oCloudName>\n" +
    "          Cloud name used for discovery of other nodes.\n" +
    "          Nodes with the same cloud name will form an H2O cloud\n" +
    "          (also known as an H2O cluster).\n" +
    "\n" +
    "    -flatfile <flatFileName>\n" +
    "          Configuration file explicitly listing H2O cloud node members.\n" +
    "\n" +
    "    -ip <ipAddressOfNode>\n" +
    "          IP address of this node.\n" +
    "\n" +
    "    -port <port>\n" +
    "          Port number for this node (note: port+1 is also used).\n" +
    "          (The default port is " + ARGS.port + ".)\n" +
    "\n" +
    "    -network <IPv4network1Specification>[,<IPv4network2Specification> ...]\n" +
    "          The IP address discovery code will bind to the first interface\n" +
    "          that matches one of the networks in the comma-separated list.\n" +
    "          Use instead of -ip when a broad range of addresses is legal.\n" +
    "          (Example network specification: '10.1.2.0/24' allows 256 legal\n" +
    "          possibilities.)\n" +
    "\n" +
    "    -ice_root <fileSystemPath>\n" +
    "          The directory where H2O spills temporary data to disk.\n" +
    "          (The default is '" + ARGS.port + "'.)\n" +
    "\n" +
    "    -nthreads <#threads>\n" +
    "          Maximum number of threads in the low priority batch-work queue.\n" +
    "          (The default is 99.)\n" +
    "\n" +
    "Cloud formation behavior:\n" +
    "\n" +
    "    New H2O nodes join together to form a cloud at startup time.\n" +
    "    Once a cloud is given work to perform, it locks out new members\n" +
    "    from joining.\n" +
    "\n" +
    "Examples:\n" +
    "\n" +
    "    Start an H2O node with 4GB of memory and a default cloud name:\n" +
    "        $ java -Xmx4g -jar h2o.jar\n" +
    "\n" +
    "    Start an H2O node with 6GB of memory and a specify the cloud name:\n" +
    "        $ java -Xmx6g -jar h2o.jar -name MyCloud\n" +
    "\n" +
    "    Start an H2O cloud with three 2GB nodes and a default cloud name:\n" +
    "        $ java -Xmx2g -jar h2o.jar &\n" +
    "        $ java -Xmx2g -jar h2o.jar &\n" +
    "        $ java -Xmx2g -jar h2o.jar &\n" +
    "\n";

    System.out.print(s);
  }

  /** If logging has not been setup yet, then Log.info will only print to
   *  stdout.  This allows for early processing of the '-version' option
   *  without unpacking the jar file and other startup stuff.  */
  static void printAndLogVersion() {
    Log.init(ARGS.log_level);
    Log.info("----- H2O started -----");
    Log.info("Build git branch: " + ABV.branchName());
    Log.info("Build git hash: " + ABV.lastCommitHash());
    Log.info("Build git describe: " + ABV.describe());
    Log.info("Build project version: " + ABV.projectVersion());
    Log.info("Built by: '" + ABV.compiledBy() + "'");
    Log.info("Built on: '" + ABV.compiledOn() + "'");

    Runtime runtime = Runtime.getRuntime();
    Log.info("Java availableProcessors: " + runtime.availableProcessors());
    Log.info("Java heap totalMemory: " + PrettyPrint.bytes(runtime.totalMemory()));
    Log.info("Java heap maxMemory: " + PrettyPrint.bytes(runtime.maxMemory()));
    Log.info("Java version: Java "+System.getProperty("java.version")+" (from "+System.getProperty("java.vendor")+")");
    Log.info("OS   version: "+System.getProperty("os.name")+" "+System.getProperty("os.version")+" ("+System.getProperty("os.arch")+")");
  }

  /** Initializes the local node and the local cloud with itself as the only member. */
  private static void startLocalNode() {
    PID = -1L;
    try {
      String n = ManagementFactory.getRuntimeMXBean().getName();
      int i = n.indexOf('@');
      if( i != -1 ) PID = Long.parseLong(n.substring(0, i));
    } catch( Throwable ignore ) { }
  
    // Figure self out; this is surprisingly hard
    NetworkInit.initializeNetworkSockets();
    // Do not forget to put SELF into the static configuration (to simulate
    // proper multicast behavior)
    if( STATIC_H2OS != null && !STATIC_H2OS.contains(SELF)) {
      Log.warn("Flatfile configuration does not include self: " + SELF+ " but contains " + STATIC_H2OS);
      STATIC_H2OS.add(SELF);
    }

    Log.info ("H2O cloud name: '" + ARGS.name + "' on " + SELF+
              (ARGS.flatfile==null
               ? (", discovery address "+CLOUD_MULTICAST_GROUP+":"+CLOUD_MULTICAST_PORT)
               : ", static configuration based on -flatfile "+ARGS.flatfile));

    Log.info("If you have trouble connecting, try SSH tunneling from your local machine (e.g., via port 55555):\n" +
            "  1. Open a terminal and run 'ssh -L 55555:localhost:"
            + API_PORT + " " + System.getProperty("user.name") + "@" + SELF_ADDRESS.getHostAddress() + "'\n" +
            "  2. Point your browser to http://localhost:55555");


    // Create the starter Cloud with 1 member
    SELF._heartbeat._jar_md5 = JarHash.JARHASH;
    Paxos.doHeartbeat(SELF);
    assert SELF._heartbeat._cloud_hash != 0;
  }

  /** Starts the worker threads, receiver threads, heartbeats and all other
   *  network related services.  */
  private static void startNetworkServices() {
    // We've rebooted the JVM recently. Tell other Nodes they can ignore task
    // prior tasks by us. Do this before we receive any packets
    UDPRebooted.T.reboot.broadcast();

    // Start the UDPReceiverThread, to listen for requests from other Cloud
    // Nodes. There should be only 1 of these, and it never shuts down.
    // Started first, so we can start parsing UDP packets
    new UDPReceiverThread().start();

    // Start the MultiReceiverThread, to listen for multi-cast requests from
    // other Cloud Nodes. There should be only 1 of these, and it never shuts
    // down. Started soon, so we can start parsing multicast UDP packets
    new MultiReceiverThread().start();

    // Start the Persistent meta-data cleaner thread, which updates the K/V
    // mappings periodically to disk. There should be only 1 of these, and it
    // never shuts down.  Needs to start BEFORE the HeartBeatThread to build
    // an initial histogram state.
    new Cleaner().start();

    // Start the heartbeat thread, to publish the Clouds' existence to other
    // Clouds. This will typically trigger a round of Paxos voting so we can
    // join an existing Cloud.
    new HeartBeatThread().start();

    // Start a UDP timeout worker thread. This guy only handles requests for
    // which we have not recieved a timely response and probably need to
    // arrange for a re-send to cover a dropped UDP packet.
    new UDPTimeOutThread().start();
    new H2ONode.AckAckTimeOutThread().start();

    // Start the TCPReceiverThread, to listen for TCP requests from other Cloud
    // Nodes. There should be only 1 of these, and it never shuts down.
    new TCPReceiverThread().start();
    // Start the Nano HTTP server thread
    //water.api.RequestServer.start();
  }

  // --------------------------------------------------------------------------
  // The Current Cloud. A list of all the Nodes in the Cloud. Changes if we
  // decide to change Clouds via atomic Cloud update.
  static volatile H2O CLOUD = new H2O(new H2ONode[0],0,0);

  // ---
  // A dense array indexing all Cloud members. Fast reversal from "member#" to
  // Node.  No holes.  Cloud size is _members.length.
  final H2ONode[] _memary;
  final int _hash;

  // A dense integer identifier that rolls over rarely. Rollover limits the
  // number of simultaneous nested Clouds we are operating on in-parallel.
  // Really capped to 1 byte, under the assumption we won't have 256 nested
  // Clouds. Capped at 1 byte so it can be part of an atomically-assigned
  // 'long' holding info specific to this Cloud.
  final char _idx; // no unsigned byte, so unsigned char instead

  // Construct a new H2O Cloud from the member list
  H2O( H2ONode[] h2os, int hash, int idx ) {
    _memary = h2os;             // Need to clone?
    java.util.Arrays.sort(_memary);       // ... sorted!
    _hash = hash;               // And record hash for cloud rollover
    _idx = (char)(idx&0x0ff);   // Roll-over at 256
  }

  // One-shot atomic setting of the next Cloud, with an empty K/V store.
  // Called single-threaded from Paxos. Constructs the new H2O Cloud from a
  // member list.
  void set_next_Cloud( H2ONode[] h2os, int hash ) {
    synchronized(this) {
      int idx = _idx+1; // Unique 1-byte Cloud index
      if( idx == 256 ) idx=1; // wrap, avoiding zero
      CLOUDS[idx] = CLOUD = new H2O(h2os,hash,idx);
    }
    SELF._heartbeat._cloud_size=(char)CLOUD.size();
  }

  // Is nnn larger than old (counting for wrap around)? Gets confused if we
  // start seeing a mix of more than 128 unique clouds at the same time. Used
  // to tell the order of Clouds appearing.
  static boolean larger( int nnn, int old ) {
    assert (0 <= nnn && nnn <= 255);
    assert (0 <= old && old <= 255);
    return ((nnn-old)&0xFF) < 64;
  }

  final int size() { return _memary.length; }
  final H2ONode leader() { return _memary[0]; }

  // Find the node index for this H2ONode, or a negative number on a miss
  int nidx( H2ONode h2o ) { return java.util.Arrays.binarySearch(_memary,h2o); }
  boolean contains( H2ONode h2o ) { return nidx(h2o) >= 0; }
  @Override public String toString() {
    return java.util.Arrays.toString(_memary);
  }

  static void notifyAboutCloudSize(InetAddress ip, int port, int size) { }

  static void waitForCloudSize(int x, long ms) {
    long start = System.currentTimeMillis();
    while( System.currentTimeMillis() - start < ms ) {
      if( CLOUD.size() >= x && Paxos._commonKnowledge )
        break;
      try { Thread.sleep(100); } catch( InterruptedException ignore ) { }
    }
    if( H2O.CLOUD.size() < x )
      throw new RuntimeException("Cloud size under " + x);
  }

  // --------------------------------------------------------------------------
  static void initializePersistence() {
    // Need to figure out the multi-jar HDFS story here
    //water.persist.HdfsLoader.loadJars();
    if( ARGS.aws_credentials != null ) {
      try { water.persist.PersistS3.getClient(); } 
      catch( IllegalArgumentException e ) { Log.err(e); }
    }
    water.persist.Persist.initialize();
  }

  // --------------------------------------------------------------------------
  // The (local) set of Key/Value mappings.
  static final NonBlockingHashMap<Key,Value> STORE = new NonBlockingHashMap<>();

  // PutIfMatch
  // - Atomically update the STORE, returning the old Value on success
  // - Kick the persistence engine as needed
  // - Return existing Value on fail, no change.
  //
  // Keys are interned here: I always keep the existing Key, if any. The
  // existing Key is blind jammed into the Value prior to atomically inserting
  // it into the STORE and interning.
  //
  // Because of the blind jam, there is a narrow unusual race where the Key
  // might exist but be stale (deleted, mapped to a TOMBSTONE), a fresh put()
  // can find it and jam it into the Value, then the Key can be deleted
  // completely (e.g. via an invalidate), the table can resize flushing the
  // stale Key, an unrelated weak-put can re-insert a matching Key (but as a
  // new Java object), and delete it, and then the original thread can do a
  // successful put_if_later over the missing Key and blow the invariant that a
  // stored Value always points to the physically equal Key that maps to it
  // from the STORE. If this happens, some of replication management bits in
  // the Key will be set in the wrong Key copy... leading to extra rounds of
  // replication.

  static Value putIfMatch( Key key, Value val, Value old ) {
    if( old != null ) // Have an old value?
      key = old._key; // Use prior key
    if( val != null )
      val._key = key;
  
    // Insert into the K/V store
    Value res = STORE.putIfMatchUnlocked(key,val,old);
    if( res != old ) return res; // Return the failure cause
    // Persistence-tickle.
    // If the K/V mapping is going away, remove the old guy.
    // If the K/V mapping is changing, let the store cleaner just overwrite.
    // If the K/V mapping is new, let the store cleaner just create
    if( old != null && val == null ) old.removePersist(); // Remove the old guy
    if( val != null ) Cleaner.dirty_store(); // Start storing the new guy
    return old; // Return success
  }
  
  // Get the value from the store
  static Value get( Key key ) { return STORE.get(key); }
  static Value raw_get( Key key ) { return STORE.get(key); }
  static Key getk( Key key ) { return STORE.getk(key); }
  static Set<Key> localKeySet( ) { return STORE.keySet(); }
  static Collection<Value> values( ) { return STORE.values(); }
  static int store_size() { return STORE.size(); }


  // --------------------------------------------------------------------------
  public static void main( String[] args ) {

    // Record system start-time.
    if( !START_TIME_MILLIS.compareAndSet(0L, System.currentTimeMillis()) )
      return;                   // Already started

    // Parse args
    new Arguments(args).extract(ARGS);

    // Get ice path before loading Log or Persist class
    String ice = DEFAULT_ICE_ROOT();
    if( ARGS.ice_root != null ) ice = ARGS.ice_root.replace("\\", "/");
    try {
      ICE_ROOT = new URI(ice);
    } catch(URISyntaxException ex) {
      throw new RuntimeException("Invalid ice_root: " + ice + ", " + ex.getMessage());
    }

    // Always print version, whether asked-for or not!
    printAndLogVersion();
    if( ARGS.version ) { exit(0); }
    // Print help & exit
    if( ARGS.help || ARGS.h ) { printHelp(); exit(0); }

    // Epic Hunt for the correct self InetAddress
    NetworkInit.findInetAddressForSelf();

    // Start the local node.  Needed before starting logging.
    startLocalNode();

    String logDir = Log.getLogDir();
    Log.info("Log dir: '"+(logDir==null ? "(unknown)" : Log.getLogDir())+"'");

    // Load up from disk and initialize the persistence layer
    initializePersistence();
    
    // Start network services, including heartbeats & Paxos
    startNetworkServices();   // start server services

    //System.out.println("Sleep 2 sec");
    //try { Thread.sleep(2000); } catch( InterruptedException _ ) { }
    //System.out.println("Slept, killing self");
    //exit(0);
  }

  /** Notify embedding software instance H2O wants to exit.
   *  @param status H2O's requested process exit value.  */
  public static void exit(int status) {
    System.exit(status);
  }
  // Die horribly
  public static void die(String s) {
    Log.err(s);
    exit(-1);
  }
}
