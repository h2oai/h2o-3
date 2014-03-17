package water;

/** Auto-serializer base-class */
public abstract class Iced implements Cloneable {
 
  // The serialization flavor.  0 means "not set".  Lazily set on first write.
  private short _frozen_type;

  private static IcedImpl SERIAL[] = new IcedImpl[] { new IcedImplUnset() };

  // Delegator pattern: 
  //  - FROZEN_TYPE is set to zero for new classes
  //  - 1st read/write sets FROZEN_TYPE to non-zero type id
  //  - Iced.SERIAL[FROZEN_TYPE] is auto-gen class implements read_impl/write_impl

  // Fast read path: 
  //  - call Iced.write(ab,this)
  //  - if( this._frozen_type != 0 )
  //  - load x=Iced.SERIAL[froz]
  //  - call x.write_impl(this)
  //  - cast Object to subtype
  //  - write subtype.fields to AutoBuffer


  public final AutoBuffer write(AutoBuffer bb) { return SERIAL[_frozen_type].write(bb,this); }

  public <T extends Iced> T read(AutoBuffer bb) { return (T)this; }
  public <T extends Iced> T newInstance() { throw barf(); }
  public int frozenType() { throw barf(); }
  public AutoBuffer writeJSONFields(AutoBuffer bb) { return bb; }
  public AutoBuffer writeJSON(AutoBuffer bb) { return writeJSONFields(bb.put1('{')).put1('}'); }
  //@Override public water.api.DocGen.FieldDoc[] toDocField() { return null; }
  //
  //public Iced init( Key k ) { return this; }
  @Override public Iced clone() {
    try { return (Iced)super.clone(); }
    catch( CloneNotSupportedException e ) { throw water.util.Log.throwErr(e); }
  }

  private RuntimeException barf() {
    return new RuntimeException(getClass().toString()+" should be automatically overridden in the subclass by the auto-serialization code");
  }
}

abstract class IcedImpl { public abstract AutoBuffer write(AutoBuffer bb, Iced ice); }

// Class for first-time writers.
class IcedImplUnset extends IcedImpl { 
  public AutoBuffer write(AutoBuffer bb, Iced ice) {
    throw H2O.unimpl();
  }
}
