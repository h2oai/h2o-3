package water;

/** Auto-serializer base-class using a delegator pattern.  
 *  (the faster option is to byte-code gen directly in all Iced classes, but
 *  this requires all Iced classes go through a ClassLoader).
 */
public interface Freezable extends Cloneable {
  AutoBuffer write(AutoBuffer ab);
  <T extends Freezable> T read(AutoBuffer ab);
  int frozenType();
  public <T extends Freezable> T clone();
}
