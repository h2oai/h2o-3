package water;

import java.util.Arrays;
import water.nbhm.NonBlockingHashMap;
import water.H2O;

public class TypeMap {
  static public final short NULL, PRIM_B, C1NCHUNK, FRAME;
  static final public String BOOTSTRAP_CLASSES[] = {
    " BAD",
    "[B",
    "water.HeartBeat",    // Used to Paxos up a cloud & leader
    "water.FetchClazz",   // used to fetch IDs from leader
    "water.FetchId",      // used to fetch IDs from leader
    "water.fvec.C1NChunk",// used as constant in parser
    "water.fvec.Frame",   // used in TypeaheadKeys & Exec2
    "water.TaskPutKey",   // Needed to write that first Key
    "water.Key",          // Needed to write that first Key
    "water.Value",        // Needed to write that first Key
    "water.TaskGetKey",   // Read that first Key
    "water.Job$List",     // First Key which locks the cloud for all JUnit tests
  };
  // Class name -> ID mapping
  static private final NonBlockingHashMap<String, Integer> MAP = new NonBlockingHashMap();
  // ID -> Class name mapping
  static private String[] CLAZZES;
  // ID -> pre-allocated Golden Instance of IcedImpl
  static private IcedImpl[] GOLD;
  // Unique ides
  static private int IDS;
  static {
    CLAZZES = BOOTSTRAP_CLASSES;
    GOLD = new IcedImpl[BOOTSTRAP_CLASSES.length];
    int id=0;                   // The initial set of Type IDs to boot with
    for( String s : CLAZZES ) MAP.put(s,id++);
    IDS = id;
    // Some statically known names, to make life easier during e.g. parse
    NULL        = (short) -1;
    PRIM_B      = (short)onIce("[B");
    C1NCHUNK    = (short)onIce("water.fvec.C1NChunk");
    FRAME       = (short)onIce("water.fvec.Frame");
  }

  // PATHS
  // (1) <clinit>; no remote, just map(clazz->id) & set static globals
  // (2) new code; fetch remote clazz->id; set CLAZZES & GOLD; no IcedImpl gen
  // (3) new code; leader sets  clazz->id; set CLAZZES & GOLD; no IcedImpl gen
  // (4) print; map id->clazz; fetch remote id->clazz
  // (5) many; leader returns known id->clazz 


  // During first Icing, get a globally unique class ID for a className
  static public int onIce(String className) {
    Integer I = MAP.get(className);
    if( I != null ) return I;
    // Need to install a new cloud-wide type ID for className.
    assert H2O.CLOUD.size() > 0 : "No cloud when getting type id for "+className;
    int id = -1;
    if( H2O.CLOUD.leader() != H2O.SELF ) // Not leader?
      //id = FetchId.fetchId(className);
      throw H2O.unimpl();
    return install(className,id);
  }

  // Install the type mapping under lock, and grow all the arrays as needed.
  // The grow-step is not obviously race-safe: readers of all the arrays will
  // get either the old or new arrays.  However readers are all reader with
  // smaller type ids, and these will work fine in either old or new arrays.
  synchronized static private int install( String className, int id ) {
    Paxos.lockCloud();
    if( id == -1 ) id = IDS++;  // Leader will get an ID under lock
    MAP.put(className,id);       // No race on insert, since under lock
    // Expand lists to handle new ID, as needed
    if( id >= CLAZZES.length ) CLAZZES = Arrays.copyOf(CLAZZES,Math.max(CLAZZES.length<<1,id+1));
    if( id >= GOLD   .length ) GOLD    = Arrays.copyOf(GOLD   ,Math.max(CLAZZES.length<<1,id+1));
    CLAZZES[id] = className;
    return id;
  }

  // Get Icer from a Ice class name.  Used during 1st-time serialization
  static IcedImpl getIcer( String clazz ) { return getIcer(onIce(clazz),clazz); }

  // Figure out the mapping from a type ID to a Class.  Happens many places,
  // including during deserialization when a Node will be presented with a
  // fresh new ID with no idea what it stands for.
  static IcedImpl getIcer( int id ) {
    IcedImpl f = id < GOLD.length ? GOLD[id] : null;
    if( f != null ) return f;

    String clazz = className(id);

    // lock on Iced class during auto-gen
    sync( Iced something ) {
    return GOLD[id]=autogen(clazz,id);
    }

    throw H2O.unimpl();
  }

  static public String className(int id) {
    throw H2O.unimpl();
    //if( id >= CLAZZES.length || CLAZZES[id] == null ) loadId(id);
    //assert CLAZZES[id] != null : "No class matching id "+id;
    //return CLAZZES[id];
    //assert H2O.CLOUD.leader() != H2O.SELF; // Leaders always have the latest mapping already
    //install( FetchClazz.fetchClazz(id), id );
  }

  //static public Iced newInstance(int id) {
  //  if( id >= CLAZZES.length || CLAZZES[id] == null ) loadId(id);
  //  IcedImpl f = GOLD[id];
  //  if( f == null ) {
  //    try { GOLD[id] = f = (IcedImpl) Class.forName(CLAZZES[id]).newInstance(); }
  //    catch( Exception e ) { System.err.println(e); throw new RuntimeException(e); }
  //  }
  //  return f.newInstance();
  //}

}
