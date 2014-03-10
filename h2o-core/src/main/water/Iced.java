package water;

/** Auto-serializer base-class */
public abstract class Iced implements Cloneable {
 
  // Coding style for new classes: class needs "private static int FROZEN_TYPE;"

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


  public AutoBuffer write(AutoBuffer bb) { 
    return bb; 
  }

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
