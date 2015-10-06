package water;

/**
 * Utility class to support Iced objects.
 */
public class IcedUtils {

  /** Deep-copy clone given iced object. */
  static public <T extends Iced> T clone(T iced) {
    AutoBuffer ab = new AutoBuffer();
    iced.write(ab);
    ab.flipForReading();
    // Create a new instance
    return (T) TypeMap.newInstance(iced.frozenType()).read(ab);
  }

  /** Deep-copy clone given keyed object and replace its key by given key.
   *
   * The call does not save the new object into DKV!
   *
   * @param keyed  keyed object to be cloned
   * @param newKey  key for cloned object.
   * @param <T>   the type of the object
   * @return
   */
  static public <T extends Keyed> T clone(T keyed, Key<T> newKey) {
    T clonedCopy = clone(keyed); // Deep copy clone
    clonedCopy._key = newKey;
    return clonedCopy;
  }
}
