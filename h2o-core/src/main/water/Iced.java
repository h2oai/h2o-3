package water;

/** Auto-serializer base-class using a delegator pattern.  
 *  (the faster option is to byte-code gen directly in all Iced classes, but
 *  this requires all Iced classes go through a ClassLoader).
 */
public abstract class Iced<D extends Iced> implements Cloneable {

  public static final Icer<Iced> ICER = new Icer<Iced>();

  // The serialization flavor / delegate.  Lazily set on first use.
  private short _ice_id;

  // Return the icer for this instance+class.  Will set on 1st use.
  private final Icer<D> icer() { 
    int id = _ice_id;
    return TypeMap.getIcer(id!=0 ? id : (_ice_id=(short)TypeMap.onIce(this)),this); 
  }
  // Standard public "write thyself into the AutoBuffer" call.
  public final AutoBuffer write(AutoBuffer ab) { return icer().write(ab,(D)this); }
  public final D read(AutoBuffer ab) { return icer().read(ab,(D)this); }
  public int frozenType() { return icer().frozenType(); }
  public AutoBuffer writeJSONFields(AutoBuffer ab) { return icer().writeJSONFields(ab,(D)this); }
  public AutoBuffer writeJSON(AutoBuffer ab) { return writeJSONFields(ab.put1('{')).put1('}'); }
  //@Override public water.api.DocGen.FieldDoc[] toDocField() { return null; }
  @Override public D clone() {
    try { return (D)super.clone(); }
    catch( CloneNotSupportedException e ) { throw water.util.Log.throwErr(e); }
  }

  // Base Class for the "iced implementation" heirarchy.  Subclasses are all
  // auto-gen'd.  Since this is the base, it has no fields to read or write.
  public static class Icer<T extends Iced> { 
    public AutoBuffer write(AutoBuffer ab, T ice) { return ab; } 
    public AutoBuffer writeJSONFields(AutoBuffer ab, T ice) { return ab; }
    public T read(AutoBuffer ab, T ice) { return ice; } 
    public T newInstance() { throw fail(); }
    public int frozenType() { throw fail(); }
    private RuntimeException fail() {
      return new RuntimeException(getClass().toString()+" should be automatically overridden by the auto-serialization code");
    }
    // The generated delegate call-chain will bottom out with methods
    // that end in the TypeMap ID for "Iced" class - which is "2".
    protected AutoBuffer write2(AutoBuffer ab, Iced ice) { return ab; } 
  }
}
