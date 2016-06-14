package water;

import java.io.*;

/**
 * H2O uses Iced classes as the primary means of moving Java Objects around
 * the cluster.
 * <p>
 * Auto-serializer base-class using a delegator pattern (the faster option is
 * to byte-code gen directly in all Iced classes, but this requires all Iced
 * classes go through a ClassLoader).
 * <p>
 * Iced is a marker class, and {@link Freezable} is the companion marker
 * interface.  Marked classes have 2-byte integer type associated with them,
 * and an auto-genned delegate class created to actually do byte-stream and
 * JSON serialization and deserialization.  Byte-stream serialization is
 * extremely dense (includes various compressions), and typically memory-bandwidth
 * bound to generate.
 * <p>
 * During startup time the Weaver creates a parallel set of classes called
 * (classname)$Icer.  These provide bytestream and JSON serializers
 * and deserializers which get called by AutoBuffer.write* and AutoBuffer.read*.
 * <p>
 * To debug the automagic serialization code create a transient field in your Iced
 * class called DEBUG_WEAVER.  The generated source code will get written to STDOUT:
 * <p>
 * {@code
 * transient int DEBUG_WEAVER = 1;
 * }
 * @see Freezable
 * @see water.Weaver
 * @see water.AutoBuffer
 */
abstract public class Iced<D extends Iced> implements Freezable<D>, Externalizable {

  // The serialization flavor / delegate.  Lazily set on first use.
  transient private volatile short _ice_id = 0;


  @Override
  public byte [] asBytes(){
    return write(new AutoBuffer()).buf();
  }

  @Override
  public D reloadFromBytes(byte [] ary){
    return read(new AutoBuffer(ary));
  }

  // Return the icer for this instance+class.  Will set on 1st use.
  private Icer<D> icer() {
    int id = _ice_id;
    int tyid;
    if(id != 0) assert id == (tyid =TypeMap.onIce(this)):"incorrectly cashed id " + id + ", typemap has " + tyid + ", type = " + getClass().getName();
    return TypeMap.getIcer(id!=0 ? id : (_ice_id=(short)TypeMap.onIce(this)),this); 
  }

  /** Standard "write thyself into the AutoBuffer" call, using the fast Iced
   *  protocol.  Real work is in the delegate {@link Icer} classes.
   *  @return Returns the original {@link AutoBuffer} for flow-coding. */
  @Override final public AutoBuffer write    (AutoBuffer ab) { return icer().write    (ab,(D)this); }
  /** Standard "write thyself into the AutoBuffer" call, using JSON.  Real work
   *  is in the delegate {@link Icer} classes.
   *  @return Returns the original {@link AutoBuffer} for flow-coding. */
  @Override public AutoBuffer writeJSON(AutoBuffer ab) { return icer().writeJSON(ab,(D)this); }
  /** Standard "read thyself from the AutoBuffer" call, using the fast Iced protocol.  Real work
   *  is in the delegate {@link Icer} classes.
   *  @return Returns the original {@link AutoBuffer} for flow-coding. */
  @Override final public D read    (AutoBuffer ab) { return icer().read    (ab,(D)this); }
  /** Standard "read thyself from the AutoBuffer" call, using JSON.  Real work
   *  is in the delegate {@link Icer} classes.
   *  @return Returns the original {@link AutoBuffer} for flow-coding. */
  @Override final public D readJSON(AutoBuffer ab) { return icer().readJSON(ab,(D)this); }

  /** Helper for folks that want a JSON String for this object. */
  final public String toJsonString() {
    return new String(this.writeJSON(new AutoBuffer()).buf());
  }

  /** Returns a small dense integer, which is cluster-wide unique per-class.
   *  Useful as an array index.
   *  @return Small integer, unique per-type */
  @Override final public int frozenType() { return icer().frozenType(); }
  /** Clone, without the annoying exception */
  @Override public final D clone() {
    try { return (D)super.clone(); }
    catch( CloneNotSupportedException e ) { throw water.util.Log.throwErr(e); }
  }

  /** Copy over cloned instance 'src' over 'this', field by field. */
  protected void copyOver( D src ) { icer().copyOver((D)this,src); }

  ///////////////////////////////////
  // TODO: make all of these protected!
  ///////////////////////////////////

  /** Implementation of the {@link Iced} serialization protocol, only called by
   *  auto-genned code.  Not intended to be called by user code.  Override only
   *  for custom Iced serializers. */
  //noninspection UnusedDeclaration
//  @Override public AutoBuffer write_impl( AutoBuffer ab ) { return ab; }
  /** Implementation of the {@link Iced} serialization protocol, only called by
   *  auto-genned code.  Not intended to be called by user code.  Override only
   *  for custom Iced serializers. */
  //noninspection UnusedDeclaration
//  @Override public D read_impl( AutoBuffer ab ) { return (D)this; }
  /** Implementation of the {@link Iced} serialization protocol, only called by
   *  auto-genned code.  Not intended to be called by user code.  Override only
   *  for custom Iced serializers. */
  //noninspection UnusedDeclaration
//  public AutoBuffer writeJSON_impl( AutoBuffer ab ) { return ab; }
  /** Implementation of the {@link Iced} serialization protocol, only called by
   *  auto-genned code.  Not intended to be called by user code.  Override only
   *  for custom Iced serializers. */
  //noninspection UnusedDeclaration
//  @Override public D readJSON_impl( AutoBuffer ab ) { return (D)this; }

  // Java serializers use H2Os Icing
  @Override public void readExternal( ObjectInput ois )  throws IOException, ClassNotFoundException {
    int x = ois.readInt();
    byte[] buf = MemoryManager.malloc1(x);
    ois.readFully(buf);
    read(new AutoBuffer(buf));
  }

  @Override public void writeExternal( ObjectOutput oos ) throws IOException {
    byte[] buf = write(new AutoBuffer()).buf();
    oos.writeInt(buf.length);
    oos.write(buf);
  }

}
