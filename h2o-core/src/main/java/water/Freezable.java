package water;

import water.util.DocGen.HTML;

/** Auto-serializer base-class using a delegator pattern.  
 *  (the faster option is to byte-code gen directly in all Iced classes, but
 *  this requires all Iced classes go through a ClassLoader).
 */
public interface Freezable extends Cloneable {
  /** Write this Freezable onto the AutoBuffer.  Defaults to the Iced serializer. */
  AutoBuffer write(AutoBuffer ab);
  /** Read from the AutoBuffer overwriting your own fields, returning yourself 'this'. */
  <T extends Freezable> T read(AutoBuffer ab);
  /** Write this Freezable onto the AutoBuffer.  Defaults to the JSON serializer. */
  AutoBuffer writeJSON(AutoBuffer ab);
  /** Read from the AutoBuffer overwriting your own fields, returning yourself 'this'. */
  <T extends Freezable> T readJSON(AutoBuffer ab);
  /** Write this Freezable onto the AutoBuffer.  Defaults to the HTML serializer. */
  HTML writeHTML(HTML sb);
  /** Cluster-wide unique small dense integer specifying your class type.
   *  Totally suitable for an array index.  */
  int frozenType();
  /** Clone, without the annoying exception*/
  public <T extends Freezable> T clone();
  // Override for a custom serializer.  Else the auto-gen one will read & write
  // all non-transient fields.
  AutoBuffer write_impl( AutoBuffer ab );
  <D extends Freezable> D read_impl( AutoBuffer ab );
  AutoBuffer writeJSON_impl( AutoBuffer ab );
  <D extends Freezable> D readJSON_impl( AutoBuffer ab );
  HTML writeHTML_impl( HTML ab );
}
