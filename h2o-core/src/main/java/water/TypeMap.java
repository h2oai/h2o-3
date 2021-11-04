package water;

import water.api.schemas3.*;
import water.nbhm.NonBlockingHashMap;
import water.util.*;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.ServiceLoader;

/** Internal H2O class used to build and maintain the cloud-wide type mapping.
 *  Only public to expose a few constants to subpackages.  No exposed user
 *  calls. */
public class TypeMap {
  static public final short NULL, PRIM_B, ICED, H2OCC, C1NCHUNK, FRAME, VECGROUP, ESPCGROUP;

  // This list contains all classes that are needed at cloud initialization time.
  private static final String[] BUILTIN_BOOTSTRAP_CLASSES = {
    " BAD",
    "[B",                               // 1 -
    water.Iced.class.getName(),         // 2 - Base serialization class
    water.H2O.H2OCountedCompleter.class.getName(),  // 3 - Base serialization class
    water.HeartBeat.class.getName(),    // Used to Paxos up a cloud & leader
    water.H2ONode.class.getName(),      // Needed to write H2ONode target/sources
    water.FetchClazz.class.getName(),   // used to fetch IDs from leader
    water.FetchId.class.getName(),      // used to fetch IDs from leader
    water.DTask.class.getName(),        // Needed for those first Tasks

    water.fvec.Chunk.class.getName(),   // parent of Chunk
    water.fvec.C1NChunk.class.getName(),// used as constant in parser
    water.fvec.Frame.class.getName(),   // used in TypeaheadKeys & Exec2
    water.fvec.Vec.VectorGroup.class.getName(), // Used in TestUtil
    water.fvec.Vec.ESPC.class.getName(), // Used in TestUtil

    // Status pages looked at without locking the cloud
    water.api.Schema.class.getName(),
    RequestSchemaV3.class.getName(),
    SchemaV3.Meta.class.getName(),
    SchemaV3.class.getName(),
    CloudV3.class.getName(),
    CloudV3.NodeV3.class.getName(),
    AboutV3.class.getName(),
    AboutEntryV3.class.getName(),
    water.UDPRebooted.ShutdownTsk.class.getName(),

    // Mistyped hack URLs
    H2OErrorV3.class.getName(),

    // Ask for ModelBuilders list
    RouteV3.class.getName(),
    ModelBuildersV3.class.getName(),  // So Flow can ask about possible Model Builders without locking
    water.util.IcedSortedHashMap.class.getName(), // Seems wildly not-needed
    hex.schemas.ModelBuilderSchema.IcedHashMapStringModelBuilderSchema.class.getName(),

    // Checking for Flow clips
    NodePersistentStorageV3.class.getName(),
    NodePersistentStorageV3.NodePersistentStorageEntryV3.class.getName(),

    // Beginning to hunt for files
    water.util.IcedLong.class.getName(),
    water.util.IcedAtomicInt.class.getName(),
    water.util.IcedDouble.class.getName(),
    water.util.IcedBitSet.class.getName(),
    water.util.IcedHashSet.class.getName(),
    water.util.IcedHashMap.class.getName(),
    water.util.IcedHashMapBase.class.getName(),
    water.util.IcedHashMapGeneric.class.getName(),
    water.util.IcedHashMapGeneric.IcedHashMapStringString.class.getName(),
    water.util.IcedHashMapGeneric.IcedHashMapStringObject.class.getName(),
    TypeaheadV3.class.getName(),    // Allow typeahead without locking

  };
  // Class name -> ID mapping
  static private final NonBlockingHashMap<String, Integer> MAP = new NonBlockingHashMap<>();
  // ID -> Class name mapping
  static String[] CLAZZES;
  // ID -> pre-allocated Golden Instance of Icer
  static private Icer[] GOLD;
  // Unique IDs
  static private int IDS;
  // Number of bootstrap classes
  static final int BOOTSTRAP_SIZE;
  // JUnit helper flag
  static public volatile boolean _check_no_locking; // ONLY TOUCH IN AAA_PreCloudLock!
  static {
    CLAZZES = findAllBootstrapClasses();
    BOOTSTRAP_SIZE = CLAZZES.length;
    GOLD = new Icer[BOOTSTRAP_SIZE];
    int id=0;                   // The initial set of Type IDs to boot with
    for( String s : CLAZZES ) MAP.put(s,id++);
    IDS = id;
    assert IDS == BOOTSTRAP_SIZE;
    // Some statically known names, to make life easier during e.g. bootup & parse
    NULL         = (short) -1;
    PRIM_B       = (short)onIce("[B");
    ICED         = (short)onIce("water.Iced");  assert ICED ==2; // Matches Iced  customer serializer
    H2OCC        = (short)onIce("water.H2O$H2OCountedCompleter"); assert H2OCC==3; // Matches customer serializer
    C1NCHUNK     = (short)onIce("water.fvec.C1NChunk"); // Used in water.fvec.FileVec
    FRAME        = (short)onIce("water.fvec.Frame");    // Used in water.Value
    VECGROUP     = (short)onIce("water.fvec.Vec$VectorGroup"); // Used in TestUtil
    ESPCGROUP    = (short)onIce("water.fvec.Vec$ESPC"); // Used in TestUtil
  }

  /**
   * Collect built-in bootstrap classes and bootstrap classes from all extensions. 
   * @return array of class names, built-in classes are listed first followed by sorted list of classes from extensions
   */
  static synchronized String[] findAllBootstrapClasses() {
    String[] additionalBootstrapClasses = new String[0];
    ServiceLoader<TypeMapExtension> extensionsLoader = ServiceLoader.load(TypeMapExtension.class);
    for (TypeMapExtension ext : extensionsLoader) {
      additionalBootstrapClasses = ArrayUtils.append(additionalBootstrapClasses, ext.getBoostrapClasses());
    }
    Arrays.sort(additionalBootstrapClasses);
    return ArrayUtils.append(BUILTIN_BOOTSTRAP_CLASSES, additionalBootstrapClasses);
  }

  /**
   * Retrieves the collection of bootstrap classes.
   * @return array of class names
   */
  public static String[] bootstrapClasses() {
    return Arrays.copyOf(CLAZZES, BOOTSTRAP_SIZE);
  }

  // The major complexity of this code is that the are FOUR major data forms
  // which get converted to one another.  At various times the code is
  // presented with one of the forms, and asked for another form, sometimes
  // forcing first to the other form.
  //
  // (1) Type ID - 2 byte shortcut for an Iced type
  // (2) String clazz name - the class name for an Iced type
  // (3) Iced POJO - an instance of Iced, the distributable workhorse object
  // (4) Icer POJO - an instance of Icer, the serializing delegate for Iced
  //
  // Some sample code paths:
  // <clinit>: convert string -> ID (then set static globals)
  // new code: fetch remote string->ID mapping
  // new code: leader sets  string->ID mapping
  // printing: id -> string
  // deserial: id -> string -> Icer -> Iced (slow path)
  // deserial: id           -> Icer -> Iced (fath path)
  // lookup  : id -> string (on leader)
  //

  // returns the ID for an existing className (fails if class doesn't exist/cannot be loaded)
  public static int getIcedId(String className) {
    Integer I = MAP.get(className);
    if (I != null)
      return I;
    try {
      Class.forName(className);
    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException("Class " + className + " is not known to H2O.", e);
    }
    return onIce(className);
  }

  // During first Icing, get a globally unique class ID for a className
  static int onIce(Iced ice) { return onIce(ice.getClass().getName()); }
  static int onIce(Freezable ice) { return onIce(ice.getClass().getName()); }
  public static int onIce(String className) {
    Integer I = MAP.get(className);
    if( I != null ) return I;
    // Need to install a new cloud-wide type ID for className.
    assert H2O.CLOUD.size() > 0 : "No cloud when getting type id for "+className;
    // Am I leader, or not?  Lock the cloud to find out
    Paxos.lockCloud(className);
    // Leader: pick an ID.  Not-the-Leader: fetch ID from leader.
    int id = H2O.CLOUD.leader() == H2O.SELF ? -1 : FetchId.fetchId(className);
    return install(className,id);
  }

  // Quick check to see if cached
  private static Icer goForGold( int id ) {
    Icer gold[] = GOLD;     // Read once, in case resizing
    // Racily read the GOLD array
    return id < gold.length ? gold[id] : null;
  }

  static String classNameLocal(final int id) {
    if( id == PRIM_B ) return "[B";
    String[] clazs = CLAZZES;   // Read once, in case resizing
    if( id < clazs.length ) { // Might be installed as a className mapping no Icer (yet)
      return clazs[id];   // Racily read the CLAZZES array
    }
    return null;
  }

  // Reverse: convert an ID to a className possibly fetching it from leader.
  public static String className(final int id) {
    String s = classNameLocal(id);
    if (s != null)
      return s; // Has the className already

    Paxos.lockCloud("Class Id="+id); // If the leader is already selected, then the cloud is already locked; but we don't know -> lock now
    s = FetchClazz.fetchClazz(id); // Fetch class name string from leader

    if (s == null) {
      // this is bad - we are missing the mapping and cannot get it from anywhere
      if (H2O.isCI()) {
        // when running on CI - dump all local TypeMaps to get some idea of what happened
        new PrintTypeMap().doAllNodes();
      }
      throw new IllegalStateException("Leader has no mapping for id " + id);
    }

    install( s, id );                     // Install name<->id mapping
    return s;
  }

  // Install the type mapping under lock, and grow all the arrays as needed.
  // The grow-step is not obviously race-safe: readers of all the arrays will
  // get either the old or new arrays.  However readers are all reader with
  // smaller type ids, and these will work fine in either old or new arrays.
  synchronized static private int install( String className, int id ) {
    assert !_check_no_locking : "Locking cloud to assign typeid to "+className;
    if( id == -1 ) {            // Leader requesting a new ID
      assert H2O.CLOUD.leader() == H2O.SELF; // Only leaders get to pick new IDs
      Integer i = MAP.get(className);
      if( i != null ) return i; // Check again under lock for already having an ID
      id = IDS++;               // Leader gets an ID under lock
    } else {
      String localClassName = classNameLocal(id);
      if (localClassName != null) {
        if (localClassName.equals(className)) {
          return id; // Nothing to do - we already got the mapping
        } else {
          throw new IllegalStateException(
                  "Inconsistent mapping: id=" + id + " is already mapped to " + localClassName + 
                  "; was requested to be mapped to " + className + "!");
        }
      }
    }
    MAP.put(className,id);      // No race on insert, since under lock
    // Expand lists to handle new ID, as needed
    if( id >= CLAZZES.length ) CLAZZES = Arrays.copyOf(CLAZZES,Math.max(CLAZZES.length<<1,id+1));
    if( id >= GOLD   .length ) GOLD    = Arrays.copyOf(GOLD   ,Math.max(CLAZZES.length<<1,id+1));
    CLAZZES[id] = className;
    return id;
  }

  // Figure out the mapping from a type ID to a Class.  Happens many places,
  // including during deserialization when a Node will be presented with a
  // fresh new ID with no idea what it stands for.  Does NOT resize the GOLD
  // array, since the id->className mapping has already happened.
  public static Icer getIcer( Freezable ice ) { return getIcer(onIce(ice),ice.getClass()); }
  static Icer getIcer( int id, Iced ice )      { return getIcer(id,ice.getClass()); }
  static Icer getIcer( int id, Freezable ice ) { return getIcer(id,ice.getClass()); }
  static Icer getIcer( int id, Class ice_clz ) {
    Icer f = goForGold(id);
    if( f != null ) return f;

    // Lock on the Iced class during auto-gen - so we only gen the Icer for
    // a particular Iced class once.
    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized( ice_clz ) {
      f = goForGold(id);        // Recheck under lock
      if( f != null ) return f;
      // Hard work: make a new delegate class
      try { f = Weaver.genDelegate(id,ice_clz); }
      catch( Exception e ) {
        Log.err("Weaver generally only throws if classfiles are not found, e.g. IDE setups running test code from a remote node that is not in the classpath on this node.");
        throw Log.throwErr(e);
      }
      // Now install under the TypeMap class lock, so the GOLD array is not
      // resized out from under the installation.
      synchronized( TypeMap.class ) {
        assert id < BOOTSTRAP_SIZE || !(f.theFreezable() instanceof BootstrapFreezable) : "Class " + ice_clz + " is not BootstrapFreezable";
        return GOLD[id]=f;
      }
    }
  }
  static Iced newInstance(int id) { return (Iced) newFreezable(id); }

  static <T extends Freezable> T newFreezable(int id, Class<T> tc) {
    @SuppressWarnings("unchecked")
    T iced = (T) newFreezable(id);
    assert tc == null || tc.isInstance(iced) : tc.getName() + " != " + iced.getClass().getName() + ", id = " + id;
    return iced;
  }

  /** Create a new freezable object based on its unique ID.
   *
   * @param id  freezable unique id (provided by TypeMap)
   * @return new instance of Freezable object
   */
  public static Freezable newFreezable(int id) {
    Freezable iced = theFreezable(id);
    assert iced != null : "No instance of id "+id+", class="+CLAZZES[id];
    return iced.clone();
  }
  /** Create a new freezable object based on its className.
   *
   * @param className class name
   * @return new instance of Freezable object
   */
  public static Freezable newFreezable(String className) {
    return theFreezable(onIce(className)).clone();
  }
  /** The single golden instance of an Iced, used for cloning and instanceof
   *  tests, do-not-modify since it's The Golden Instance and shared. */
  public static Freezable theFreezable(int id) {
    try {
      Icer f = goForGold(id);
      return (f==null ? getIcer(id, Class.forName(className(id))) : f).theFreezable();
    } catch( ClassNotFoundException e ) {
      throw Log.throwErr(e);
    }
  }
  public static Freezable getTheFreezableOrThrow(int id) throws ClassNotFoundException {
    Icer f = goForGold(id);
    return (f==null ? getIcer(id, Class.forName(className(id))) : f).theFreezable();
  }

  static void printTypeMap(PrintStream ps) {
    final String[] clazzes = CLAZZES;
    for (int i = 0; i < clazzes.length; i++) {
      final String className = CLAZZES[i];
      ps.println(i + " -> " + className + " (map: " + (className != null ? MAP.get(className) : null) + ")");
    }
  }

  private static class PrintTypeMap extends MRTask<PrintTypeMap> {
    @Override
    protected void setupLocal() {
      System.err.println("TypeMap dump on node " + H2O.SELF);
      printTypeMap(System.err);
    }
  }

}
