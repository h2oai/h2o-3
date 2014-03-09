package water;

import java.lang.management.ManagementFactory;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import water.init.*;
import water.nbhm.NonBlockingHashMap;
import water.util.*;

/**
* Start point for creating or joining an <code>H2O</code> Cloud.
*
* @author <a href="mailto:cliffc@0xdata.com"></a>
* @version 1.0
*/
public final class H2O {

  public static final AbstractBuildVersion ABV;
  static {
    AbstractBuildVersion abv = AbstractBuildVersion.UNKNOWN_VERSION;
    try {
      Class klass = Class.forName("water.BuildVersion");
      java.lang.reflect.Constructor constructor = klass.getConstructor();
      abv = (AbstractBuildVersion) constructor.newInstance();
    } catch (Exception _) { }
    ABV = abv;
  }

  // Atomically set once during startup.  Guards against repeated startups.
  public static AtomicLong START_TIME_MILLIS = new AtomicLong(); // When did main() run

  // Used to gate default worker threadpool sizes
  public static final int NUMCPUS = Runtime.getRuntime().availableProcessors();
  // Best-guess process ID
  public static long PID = -1L;

  // Convenience error
  public static final RuntimeException unimpl() { return new RuntimeException("unimplemented"); }
  public static final RuntimeException fail() { return new RuntimeException("do not call"); }

  // List of arguments.
  public static OptArgs ARGS = new OptArgs();
  public static class OptArgs extends Arguments.Opt {
    public boolean h = false;
    public boolean help = false;
    public boolean version = false;

    // Common config options
    public String name = System.getProperty("user.name"); // Cloud name
    public String flatfile;     // List of cluster IP addresses
    public int    port;         // Browser/API/HTML port
    public int    h2o_port;     // port+1
    public String ip;           // Named IP4/IP6 address instead of the default
    public String network;      // Network specification for acceptable interfaces to bind to.
    public String ice_root;     // ice root directory; where temp files go
    public String log_level;    // One of DEBUG, INFO, WARN, ERRR.  Null is INFO.

    // Less common config options
    public int nthreads=Math.max(99,10*NUMCPUS); // Max number of F/J threads in the low-priority batch queue
    public boolean random_udp_drop; // test only, randomly drop udp incoming
    public boolean requests_log = true; // logging of Web requests
    public boolean check_rest_params = true; // enable checking unused/unknown REST params

    // HDFS & AWS
    public String hdfs; // HDFS backend
    public String hdfs_version; // version of the filesystem
    public String hdfs_config; // configuration file of the HDFS
    public String hdfs_skip = null; // used by hadoop driver to not unpack and load any hdfs jar file at runtime.
    public String aws_credentials; // properties file for aws credentials
  }

  public static int H2O_PORT; // Both TCP & UDP cluster ports
  public static int API_PORT; // RequestServer and the API HTTP port

  // The multicast discovery port
  static public MulticastSocket  CLOUD_MULTICAST_SOCKET;
  static public NetworkInterface CLOUD_MULTICAST_IF;
  static public InetAddress      CLOUD_MULTICAST_GROUP;
  static public int              CLOUD_MULTICAST_PORT ;

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
  public static final String DEBUG_ARG = "h2o.debug";
  public static final boolean DEBUG = System.getProperty(DEBUG_ARG) != null;

  public static void printHelp() {
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
  public static void printAndLogVersion() {
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
    } catch( Throwable _ ) { }
  
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

    throw H2O.unimpl();
    // Start the UDPReceiverThread, to listen for requests from other Cloud
    // Nodes. There should be only 1 of these, and it never shuts down.
    // Started first, so we can start parsing UDP packets
    //new UDPReceiverThread().start();

    // Start the MultiReceiverThread, to listen for multi-cast requests from
    // other Cloud Nodes. There should be only 1 of these, and it never shuts
    // down. Started soon, so we can start parsing multicast UDP packets
    //new MultiReceiverThread().start();

    // Start the Persistent meta-data cleaner thread, which updates the K/V
    // mappings periodically to disk. There should be only 1 of these, and it
    // never shuts down.  Needs to start BEFORE the HeartBeatThread to build
    // an initial histogram state.
    //new Cleaner().start();

    // Start the heartbeat thread, to publish the Clouds' existence to other
    // Clouds. This will typically trigger a round of Paxos voting so we can
    // join an existing Cloud.
    //new HeartBeatThread().start();

    // Start a UDP timeout worker thread. This guy only handles requests for
    // which we have not recieved a timely response and probably need to
    // arrange for a re-send to cover a dropped UDP packet.
    //new UDPTimeOutThread().start();
    //new H2ONode.AckAckTimeOutThread().start();

    // Start the TCPReceiverThread, to listen for TCP requests from other Cloud
    // Nodes. There should be only 1 of these, and it never shuts down.
    //new TCPReceiverThread().start();
    // Start the Nano HTTP server thread
    //water.api.RequestServer.start();
  }

  // --------------------------------------------------------------------------
  // The Current Cloud. A list of all the Nodes in the Cloud. Changes if we
  // decide to change Clouds via atomic Cloud update.
  static public volatile H2O CLOUD = new H2O(new H2ONode[0],0,0);

  // ---
  // A dense array indexing all Cloud members. Fast reversal from "member#" to
  // Node.  No holes.  Cloud size is _members.length.
  public final H2ONode[] _memary;
  public final int _hash;

  // A dense integer identifier that rolls over rarely. Rollover limits the
  // number of simultaneous nested Clouds we are operating on in-parallel.
  // Really capped to 1 byte, under the assumption we won't have 256 nested
  // Clouds. Capped at 1 byte so it can be part of an atomically-assigned
  // 'long' holding info specific to this Cloud.
  public final char _idx; // no unsigned byte, so unsigned char instead

  // Construct a new H2O Cloud from the member list
  public H2O( H2ONode[] h2os, int hash, int idx ) {
    _memary = h2os;             // Need to clone?
    Arrays.sort(_memary);       // ... sorted!
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
    //Paxos.print("Announcing new Cloud Membership: ",_memary);
  }

  public final int size() { return _memary.length; }
  public final H2ONode leader() { return _memary[0]; }

  // Find the node index for this H2ONode, or a negative number on a miss
  public int nidx( H2ONode h2o ) { return Arrays.binarySearch(_memary,h2o); }
  public boolean contains( H2ONode h2o ) { return nidx(h2o) >= 0; }
  @Override public String toString() {
    return Arrays.toString(_memary);
  }
  public static void notifyAboutCloudSize(InetAddress ip, int port, int size) { }


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
  static final NonBlockingHashMap<Key,Value> STORE = new NonBlockingHashMap<Key, Value>();

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

  public static final Value putIfMatch( Key key, Value val, Value old ) {
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
  
  // Raw put; no marking the memory as out-of-sync with disk. Used to import
  // initial keys from local storage, or to intern keys.
  public static final Value putIfAbsent_raw( Key key, Value val ) {
    Value res = STORE.putIfMatchUnlocked(key,val,null);
    assert res == null;
    return res;
  }
  
  //// Get the value from the store
  //public static Value get( Key key ) {
  //  Value v = STORE.get(key);
  //  // Lazily manifest array chunks, if the backing file exists.
  //  if( v == null ) {
  //    v = Value.lazyArrayChunk(key);
  //    if( v == null ) return null;
  //    // Insert the manifested value, as-if it existed all along
  //    Value res = putIfMatch(key,v,null);
  //    if( res != null ) v = res; // This happens racily, so take any prior result
  //  }
  //  if( v != null ) v.touch();
  //  return v;
  //}

  public static Value raw_get( Key key ) { return STORE.get(key); }
  public static Key getk( Key key ) { return STORE.getk(key); }
  public static Set<Key> localKeySet( ) { return STORE.keySet(); }
  public static Collection<Value> values( ) { return STORE.values(); }
  public static int store_size() { return STORE.size(); }


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
  public static void die(Throwable t) {
    Log.err(t);
    exit(-1);
  }
}
