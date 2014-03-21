package water;

import sun.misc.Unsafe;
import water.nbhm.UtilUnsafe;

/** Auto-serializer base-class using a delegator pattern.  
 *  (the faster option is to byte-code gen directly in all Iced classes, but
 *  this requires all Iced classes go through a ClassLoader).
 */
abstract public class Iced<D extends Iced> implements Cloneable {

  static final Icer<Iced> ICER = new Icer<Iced>();

  // The serialization flavor / delegate.  Lazily set on first use.
  private short _ice_id;

  // Return the icer for this instance+class.  Will set on 1st use.
  private Icer<D> icer() {
    int id = _ice_id;
    return TypeMap.getIcer(id!=0 ? id : (_ice_id=(short)TypeMap.onIce(this)),this); 
  }
  // Standard "write thyself into the AutoBuffer" call.
  final AutoBuffer write(AutoBuffer ab) { return icer().write(ab,(D)this); }
  final D read(AutoBuffer ab) { return (D)icer().read(ab,this); }
  int frozenType() { return icer().frozenType(); }
  AutoBuffer writeJSONFields(AutoBuffer ab) { return icer().writeJSONFields(ab,(D)this); }
  AutoBuffer writeJSON(AutoBuffer ab) { return writeJSONFields(ab.put1('{')).put1('}'); }
  //@Override water.api.DocGen.FieldDoc[] toDocField() { return null; }
  @Override public D clone() {
    try { return (D)super.clone(); }
    catch( CloneNotSupportedException e ) { throw water.util.Log.throwErr(e); }
  }

  // Base Class for the "iced implementation" heirarchy.  Subclasses are all
  // auto-gen'd.  Since this is the base, it has no fields to read or write.
  public static class Icer<T extends Iced> { 
    protected static final Unsafe _unsafe = UtilUnsafe.getUnsafe();
    AutoBuffer write(AutoBuffer ab, T ice) { return write2(ab,ice); } 
    AutoBuffer writeJSONFields(AutoBuffer ab, T ice) { return ab; }
    Iced read(AutoBuffer ab, Iced ice) { return read2(ab,ice); }
    T newInstance() { throw fail(); }
    int frozenType() { throw fail(); } // TypeMap.ICED.... but always overridden, since no one makes a bare Iced object
    private RuntimeException fail() {
      return new RuntimeException(getClass().toString()+" should be automatically overridden by the auto-serialization code");
    }
    // The generated delegate call-chain will bottom out with final methods
    // that end in the TypeMap ID for "Iced" class - which is "2".
    protected final AutoBuffer write2(AutoBuffer ab, Iced ice) { return ab; } 
    protected final Iced read2(AutoBuffer ab, Iced ice) { return ice; }
  }
}
