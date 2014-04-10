package water;

import java.util.Arrays;
import water.init.Weaver;
import water.nbhm.NonBlockingHashMap;
import water.util.Log;

public class TypeMap {
  static public final short NULL, PRIM_B, ICED, H2OCC, C1NCHUNK, FRAME;
  static final String BOOTSTRAP_CLASSES[] = {
    " BAD",
    "[B",                 // 1 - 
    "water.Iced",         // 2 - Base serialization class
    "water.H2O$H2OCountedCompleter",  // 3 - Base serialization class
    "water.HeartBeat",    // Used to Paxos up a cloud & leader
    "water.H2ONode",      // Needed to write H2ONode target/sources
    "water.FetchClazz",   // used to fetch IDs from leader
    "water.FetchId",      // used to fetch IDs from leader
    "water.DTask",        // Needed for those first Tasks
    "water.DException",   // Needed for those first Tasks: can pass exceptions

    "water.fvec.Chunk",   // parent of Chunk
    "water.fvec.C1NChunk",// used as constant in parser
    "water.fvec.Frame",   // used in TypeaheadKeys & Exec2
  };
  // Class name -> ID mapping
  static private final NonBlockingHashMap<String, Integer> MAP = new NonBlockingHashMap<>();
  // ID -> Class name mapping
  static private String[] CLAZZES;
  // ID -> pre-allocated Golden Instance of IcedImpl
  static private Icer[] GOLD;
  // Unique ides
  static private int IDS;
  static {
    CLAZZES = BOOTSTRAP_CLASSES;
    GOLD = new Icer[BOOTSTRAP_CLASSES.length];
    int id=0;                   // The initial set of Type IDs to boot with
    for( String s : CLAZZES ) MAP.put(s,id++);
    IDS = id;
    // Some statically known names, to make life easier during e.g. bootup & parse
    NULL        = (short) -1;
    PRIM_B      = (short)onIce("[B");
    ICED        = (short)onIce("water.Iced");  assert ICED ==2; // Matches Iced  customer serializer
    H2OCC       = (short)onIce("water.H2O$H2OCountedCompleter"); assert H2OCC==3; // Matches customer serializer
    C1NCHUNK    = (short)onIce("water.fvec.C1NChunk"); // Used in water.fvec.FileVec
    FRAME       = (short)onIce("water.fvec.Frame");    // Used in water.Value

    // Fill in some pre-cooked delegates so seralization has a base-case
    GOLD[ICED ] = Icer.ICER;
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
  static int onIce(Iced ice) { return onIce(ice.getClass().getName()); }
  static int onIce(Freezable ice) { return onIce(ice.getClass().getName()); }
  public static int onIce(String className) {
    Integer I = MAP.get(className);
    if( I != null ) return I;
    // Need to install a new cloud-wide type ID for className.
    assert H2O.CLOUD.size() > 0 : "No cloud when getting type id for "+className;
    int id = -1;
    if( H2O.CLOUD.leader() != H2O.SELF ) // Not leader?
      id = FetchId.fetchId(className);
    return install(className,id);
  }

  // Quick check to see if cached
  private static Icer goForGold( int id ) {
    Icer gold[] = GOLD;     // Read once, in case resizing
    // Racily read the GOLD array
    return id < gold.length ? gold[id] : null;
  }

  // Reverse: convert an ID to a className possibly fetching it from leader.
  static String className(int id) {
    if( id == PRIM_B ) return "[B";
    Icer f = goForGold(id);
    if( f != null ) return f.className();
    if( id < CLAZZES.length ) { // Might be installed as a className mapping no Icer (yet)
      String s = CLAZZES[id];
      if( s != null ) return s; // Has the className already
    }
    assert H2O.CLOUD.leader() != H2O.SELF : "Leader has no mapping for id "+id; // Leaders always have the latest mapping already
    String s = FetchClazz.fetchClazz(id);  // Fetch class name string from leader
    install( s, id );                      // Install name<->id mapping
    return s;
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
  // fresh new ID with no idea what it stands for.  Does NOT resize the GOLD
  // array, since the id->className mapping has already happened.
  static Icer getIcer( int id, Iced ice ) { return getIcer(id,ice.getClass()); }
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
      f = Weaver.genDelegate(id,ice_clz);
      // Now install until the TypeMap class lock, so the GOLD array is not
      // resized out from under the installation.
      synchronized( TypeMap.class ) {
        return GOLD[id]=f;
      }
    }
  }

  static Iced newInstance(int id) { return (Iced)newFreezable(id); }
  static Freezable newFreezable(int id) {
    try {
      Icer f = goForGold(id);
      return (f==null ? getIcer(id, Class.forName(className(id))) : f).newFreezable();
    } catch( ClassNotFoundException e ) { throw Log.throwErr(e); }
  }
}
