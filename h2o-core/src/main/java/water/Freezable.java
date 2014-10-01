package water;

import water.util.DocGen.HTML;

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
public interface Freezable extends Cloneable {
  /** Standard "write thyself into the AutoBuffer" call, using the fast Iced
   *  protocol.  Real work is in the delegate {@link Icer} classes.
   *  @return Returns the original {@link AutoBuffer} for flow-coding. */
  AutoBuffer write(AutoBuffer ab);
  /** Standard "read thyself from the AutoBuffer" call, using the fast Iced protocol.  Real work
   *  is in the delegate {@link Icer} classes.
   *  @return Returns the original {@link AutoBuffer} for flow-coding. */
  <T extends Freezable> T read(AutoBuffer ab);
  /** Standard "write thyself into the AutoBuffer" call, using JSON.  Real work
   *  is in the delegate {@link Icer} classes.
   *  @return Returns the original {@link AutoBuffer} for flow-coding. */
  AutoBuffer writeJSON(AutoBuffer ab);
  /** Standard "read thyself from the AutoBuffer" call, using JSON.  Real work
   *  is in the delegate {@link Icer} classes.
   *  @return Returns the original {@link AutoBuffer} for flow-coding. */
  <T extends Freezable> T readJSON(AutoBuffer ab);
  /** Standard "write thyself into the AutoBuffer" call, using HTML.  Real work
   *  is in the delegate {@link Icer} classes.
   *  @return Returns the original {@link AutoBuffer} for flow-coding. */
  HTML writeHTML(HTML sb);
  /** Returns a small dense integer, which is cluster-wide unique per-class.
   *  Useful as an array index.
   *  @return Small integer, unique per-type */
  int frozenType();
  /** Clone, without the annoying exception*/
  public <T extends Freezable> T clone();
  /** Implementation of the {@link Iced} serialization protocol, only called by
   *  auto-genned code.  Not intended to be called by user code.  Override only
   *  for custom Iced serializers. */
  //noninspection UnusedDeclaration
  AutoBuffer write_impl( AutoBuffer ab );
  /** Implementation of the {@link Iced} serialization protocol, only called by
   *  auto-genned code.  Not intended to be called by user code.  Override only
   *  for custom Iced serializers. */
  //noninspection UnusedDeclaration
  <D extends Freezable> D read_impl( AutoBuffer ab );
  /** Implementation of the {@link Iced} serialization protocol, only called by
   *  auto-genned code.  Not intended to be called by user code.  Override only
   *  for custom Iced serializers. */
  //noninspection UnusedDeclaration
  AutoBuffer writeJSON_impl( AutoBuffer ab );
  /** Implementation of the {@link Iced} serialization protocol, only called by
   *  auto-genned code.  Not intended to be called by user code.  Override only
   *  for custom Iced serializers. */
  //noninspection UnusedDeclaration
  <D extends Freezable> D readJSON_impl( AutoBuffer ab );
  /** Implementation of the {@link Iced} serialization protocol, only called by
   *  auto-genned code.  Not intended to be called by user code.  Override only
   *  for custom Iced serializers. */
  //noninspection UnusedDeclaration
  HTML writeHTML_impl( HTML ab );
}
