package water;

/** Auto-serializer base-class */
public abstract class Iced implements Cloneable {
  // The abstract methods to be filled in by subclasses.  These are automatically
  // filled in by any subclass of Iced during class-load-time, unless one
  // is already defined.  These methods are NOT DECLARED ABSTRACT, because javac
  // thinks they will be called by subclasses relying on the auto-gen.
  private RuntimeException barf() {
    return new RuntimeException(getClass().toString()+" should be automatically overridden in the subclass by the auto-serialization code");
  }
  public AutoBuffer write(AutoBuffer bb) { return bb; }
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
}
