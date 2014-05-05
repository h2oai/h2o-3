package water;

/** Auto-serializer base-class using a delegator pattern.  
 *  (the faster option is to byte-code gen directly in all Iced classes, but
 *  this requires all Iced classes go through a ClassLoader).
 */
abstract public class Iced<D extends Iced> implements Freezable {

  // The serialization flavor / delegate.  Lazily set on first use.
  private short _ice_id;

  // Return the icer for this instance+class.  Will set on 1st use.
  private Icer<D> icer() {
    int id = _ice_id;
    return TypeMap.getIcer(id!=0 ? id : (_ice_id=(short)TypeMap.onIce(this)),this); 
  }
  // Real work is the delegate "Icer" classes.
  // Standard "write thyself into the AutoBuffer" call.
  @Override final public AutoBuffer write    (AutoBuffer ab) { return icer().write    (ab,(D)this); }
  @Override final public AutoBuffer writeJSON(AutoBuffer ab) { return icer().writeJSON(ab,(D)this); }
  // Standard "read thyself from the AutoBuffer" call.
  @Override final public D read    (AutoBuffer ab) { return icer().read    (ab,(D)this); }
  @Override final public D readJSON(AutoBuffer ab) { return icer().readJSON(ab,(D)this); }

  // Return a unique small dense integer for the type, picking the integer if needed.
  @Override final public int frozenType() { return icer().frozenType(); }
  //@Override water.api.DocGen.FieldDoc[] toDocField() { return null; }
  @Override public final D clone() {
    try { return (D)super.clone(); }
    catch( CloneNotSupportedException e ) { throw water.util.Log.throwErr(e); }
  }
  // Override for a custom serializer.  Else the auto-gen one will read & write
  // all non-transient fields.
  //noninspection UnusedDeclaration
  @Override public AutoBuffer write_impl( AutoBuffer ab ) { return ab; }
  //noninspection UnusedDeclaration
  @Override public D read_impl( AutoBuffer ab ) { return (D)this; }
}
