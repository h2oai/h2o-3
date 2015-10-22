package water;

/**
 * Utility class to support Iced objects.
 */
public class IcedUtils {

  /** Clone given iced object. */
  static public <T extends Iced> T clone(T iced) {
    AutoBuffer ab = new AutoBuffer();
    iced.write(ab);
    ab.flipForReading();
    // Create a new instance
    return (T) TypeMap.newInstance(iced.frozenType()).read(ab);
  }

  /** Clone given keyed object and replace its key by
   * given key.
   *
   * The call does not save the new object into DKV!
   *
   * @param keyed  keyed object to be cloned
   * @param newKey  key for cloned object.
   * @param <T>   the type of the object
   * @return
   */
  static public <T extends Keyed> T clone(T keyed, Key<T> newKey) {
    return clone(keyed, newKey, false);
  }

  /** Clone given keyed object and replace its key by
   * given key. Optionally it can save the object
   * into DKV.
   *
   * @param keyed  keyed object to be cloned
   * @param newKey  key for cloned object.
   * @param publish  publish object into DKV
   * @param <T>   the type of the object
   * @return
   */
  static public <T extends Keyed> T clone(T keyed, Key<T> newKey, boolean publish) {
    T clonedCopy = clone(keyed);
    clonedCopy._key = newKey;
    if (publish) DKV.put(newKey, clonedCopy);
    return clonedCopy;
  }
}
