package water;

/** Auto-serializer interface using a delegator pattern (the faster option is
 *  to byte-code gen directly in all Iced classes, but this requires all Iced
 *  classes go through a ClassLoader).
 *  <p>
 *  Freeazable is a marker interface, and {@link Iced} is the companion marker
 *  class.  Marked classes have 2-byte integer type associated with them, and
 *  an auto-genned delegate class created to actually do serialization.
 *  Serialization is extremely dense (includes various compressions), and
 *  typically memory-bandwidth bound to generate.
 *  <p>
 *  H2O uses Iced classes as the primary means of moving Java Objects around
 *  the cluster. */
public interface Freezable<T extends Freezable> extends Cloneable {
  /** Standard "write thyself into the AutoBuffer" call, using the fast Iced
   *  protocol.  Real work is in the delegate {@link Icer} classes.
   *  @param ab <code>AutoBuffer</code> to write this object to.
   *  @return Returns the original {@link AutoBuffer} for flow-coding. */
  AutoBuffer write(AutoBuffer ab);
  /** Standard "read thyself from the AutoBuffer" call, using the fast Iced protocol.  Real work
   *  is in the delegate {@link Icer} classes.
   *  @param ab <code>AutoBuffer</code> to read this object from.
   *  @return Returns a new instance of object reconstructed from AutoBuffer. */
  T read(AutoBuffer ab);
  /** Standard "write thyself into the AutoBuffer" call, using JSON.  Real work
   *  is in the delegate {@link Icer} classes.
   *  @param ab <code>AutoBuffer</code> to write this object to.
   *  @return Returns the original {@link AutoBuffer} for flow-coding. */
  AutoBuffer writeJSON(AutoBuffer ab);
  /** Standard "read thyself from the AutoBuffer" call, using JSON.  Real work
   *  is in the delegate {@link Icer} classes.
   *  @param ab <code>AutoBuffer</code> to read this object from.
   *  @return Returns an instance of object reconstructed from JSON data. */
  T readJSON(AutoBuffer ab);
  /** Returns a small dense integer, which is cluster-wide unique per-class.
   *  Useful as an array index.
   *  @return Small integer, unique per-type */
  int frozenType();
  /** Make clone public, but without the annoying exception.
   *  @return Returns this object cloned. */
  public Object clone();
  /** Implementation of the {@link Iced} serialization protocol, only called by
   *  auto-genned code.  Not intended to be called by user code.  Override only
   *  for custom Iced serializers.
   *  @param ab <code>AutoBuffer</code> to write this object to.
   *  @return Returns the original {@link AutoBuffer} for flow-coding. */
  //noninspection UnusedDeclaration
  AutoBuffer write_impl( AutoBuffer ab );
  /** Implementation of the {@link Iced} serialization protocol, only called by
   *  auto-genned code.  Not intended to be called by user code.  Override only
   *  for custom Iced serializers.
   *  @param ab <code>AutoBuffer</code> to read this object from.
   *  @return Returns a new instance of object reconstructed from AutoBuffer.
   */
  //noninspection UnusedDeclaration
  T read_impl( AutoBuffer ab );
  /** Implementation of the {@link Iced} serialization protocol, only called by
   *  auto-genned code.  Not intended to be called by user code.  Override only
   *  for custom Iced serializers.
   *  @param ab <code>AutoBuffer</code> to write this object to.
   *  @return Returns the original {@link AutoBuffer} for flow-coding. */
  //noninspection UnusedDeclaration
  AutoBuffer writeJSON_impl( AutoBuffer ab );
  /** Implementation of the {@link Iced} serialization protocol, only called by
   *  auto-genned code.  Not intended to be called by user code.  Override only
   *  for custom Iced serializers.
   *  @param ab <code>AutoBuffer</code> to read this object from.
   *  @param <D> Type of returned object
   *  @return Returns an instance of object reconstructed from JSON data. */
  //noninspection UnusedDeclaration
  T readJSON_impl( AutoBuffer ab );
}
