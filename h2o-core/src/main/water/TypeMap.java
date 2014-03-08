package water;

import java.util.Arrays;
import water.nbhm.NonBlockingHashMap;
import water.util.Log;
import water.H2O;

public class TypeMap {
  static public final short NULL = (short) -1;
  static public final short PRIM_B = 1;
  static public final short C1NCHUNK;
  static public final short FRAME;
  static final public String BOOTSTRAP_CLASSES[] = {
    " BAD",
    "[B",
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
  // String -> ID mapping
  static private final NonBlockingHashMap<String, Integer> MAP = new NonBlockingHashMap();
  // ID -> String mapping
  static private String[] CLAZZES;
  // ID -> pre-allocated Golden Instance of class
  static private Iced[] GOLD;
  // Unique ides
  static private int IDS;
  static {
    CLAZZES = BOOTSTRAP_CLASSES;
    GOLD = new Iced[BOOTSTRAP_CLASSES.length];
    int id=0;
    for( String s : CLAZZES )
      MAP.put(s,id++);
    IDS = id;
    C1NCHUNK    = (short)onIce("water.fvec.C1NChunk");
    FRAME       = (short)onIce("water.fvec.Frame");
  }

  // During first Icing, get a globally unique class ID for a className
  static public int onIce(String className) {
    Integer I = MAP.get(className);
    if( I != null ) return I;
    // Need to install a new cloud-wide type ID for className
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

  // During deserialization, figure out the mapping from a type ID to a type
  // String (and Class).  Mostly forced into another class to avoid circular
  // class-loading issues.
  static public void loadId(int id) {
    assert H2O.CLOUD.leader() != H2O.SELF; // Leaders always have the latest mapping already
    throw H2O.unimpl();
    //install( FetchClazz.fetchClazz(id), id );
  }

  static public Iced newInstance(int id) {
    if( id >= CLAZZES.length || CLAZZES[id] == null ) loadId(id);
    Iced f = GOLD[id];
    if( f == null ) {
      try { GOLD[id] = f = (Iced) Class.forName(CLAZZES[id]).newInstance(); }
      catch( Exception e ) { Log.throwErr(e); }
    }
    return f.newInstance();
  }

  static public String className(int id) {
    if( id >= CLAZZES.length || CLAZZES[id] == null ) loadId(id);
    assert CLAZZES[id] != null : "No class matching id "+id;
    return CLAZZES[id];
  }
  static public Class clazz(int id) {
    if( id >= CLAZZES.length || CLAZZES[id] == null ) loadId(id);
    if( GOLD[id] == null ) newInstance(id);
    return GOLD[id].getClass();
  }
}
