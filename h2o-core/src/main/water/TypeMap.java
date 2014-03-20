package water;

import java.util.Arrays;
import water.init.Weaver;
import water.nbhm.NonBlockingHashMap;

public class TypeMap {
  static public final short NULL, PRIM_B, ICED, C1NCHUNK, FRAME;
  static final public String BOOTSTRAP_CLASSES[] = {
    " BAD",
    "[B",
    "water.Iced",         // Based serialization class
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
  static private final NonBlockingHashMap<String, Integer> MAP = new NonBlockingHashMap<String,Integer>();
  // ID -> Class name mapping
  static private String[] CLAZZES;
  // ID -> pre-allocated Golden Instance of IcedImpl
  static private Iced.Icer[] GOLD;
  // Unique ides
  static private int IDS;
  static {
    CLAZZES = BOOTSTRAP_CLASSES;
    GOLD = new Iced.Icer[BOOTSTRAP_CLASSES.length];
    int id=0;                   // The initial set of Type IDs to boot with
    for( String s : CLAZZES ) MAP.put(s,id++);
    IDS = id;
    // Some statically known names, to make life easier during e.g. parse
    NULL        = (short) -1;
    PRIM_B      = (short)onIce("[B");
    ICED        = (short)onIce("water.Iced");
    C1NCHUNK    = (short)onIce("water.fvec.C1NChunk");
    FRAME       = (short)onIce("water.fvec.Frame");

    // Fill in some pre-cooked delegates so seralization has a base-case
    GOLD[ICED] = Iced.ICER;
  }

  // The major complexity of this code is that the are FOUR major data forms
  // which get converted to one another.  At various times the code is
  // presented with one of the forms, and asked for another form, sometimes
  // forcing first to the other form.
  //
  // (1) Type ID - 2 byte shortcut for an Iced type
  // (2) String clazz name - the class name for an Iced type
  // (3) Iced POJO - an instance of Iced, the distributable workhorse object
  // (4) IcedImpl POJO - an instance of IcedImpl, the serializing delegate for Iced
  //
  // Some sample code paths:
  // <clinit>: convert string -> ID (then set static globals)
  // new code: fetch remote string->ID mapping
  // new code: leader sets  string->ID mapping
  // printing: id -> string
  // deserial: id -> string -> IcedImpl -> Iced (slow path)
  // deserial: id           -> IcedImpl -> Iced (fath path)
  // lookup  : id -> string (on leader)
  //


  // During first Icing, get a globally unique class ID for a className
  static public int onIce(Iced ice) { return onIce(ice.getClass().getName()); }
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

  // Figure out the mapping from a type ID to a Class.  Happens many places,
  // including during deserialization when a Node will be presented with a
  // fresh new ID with no idea what it stands for.
  static Iced.Icer getIcer( int id, Iced ice ) {
    Iced.Icer gold[] = GOLD;     // Read once, in case resizing
    // Racily read the GOLD array
    Iced.Icer f = id < gold.length ? gold[id] : null;
    if( f != null ) return f;

    // Lock on the Iced class during auto-gen - so we only gen the IcedImpl for
    // a particular Iced class once.
    synchronized( ice.getClass() ) {
      gold = GOLD;              // Read again, in case resizing
      f = id < gold.length ? gold[id] : null;
      if( f != null ) return f; // Recheck under lock
      // Hard work: make a new delegate class
      f = Weaver.genDelegate(id,ice.getClass());
      // Now install until the TypeMap class lock, so the GOLD array is not
      // resized out from under the installation.
      synchronized( TypeMap.class ) {
        return GOLD[id]=f;
      }
    }
  }

  static public String className(int id) {
    throw H2O.unimpl();
    //if( id >= CLAZZES.length || CLAZZES[id] == null ) loadId(id);
    //assert CLAZZES[id] != null : "No class matching id "+id;
    //return CLAZZES[id];
    //assert H2O.CLOUD.leader() != H2O.SELF; // Leaders always have the latest mapping already
    //install( FetchClazz.fetchClazz(id), id );
  }

  static public Iced newInstance(int id) {
    throw H2O.unimpl();
  //  if( id >= CLAZZES.length || CLAZZES[id] == null ) loadId(id);
  //  IcedImpl f = GOLD[id];
  //  if( f == null ) {
  //    try { GOLD[id] = f = (IcedImpl) Class.forName(CLAZZES[id]).newInstance(); }
  //    catch( Exception e ) { System.err.println(e); throw new RuntimeException(e); }
  //  }
  //  return f.newInstance();
  }
}
