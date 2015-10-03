package water;

/**
 * Utility class to support Iced objects.
 */
public class IcedUtils {

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
    T clonedCopy = (T)keyed.clone();
    clonedCopy._key = newKey;
    return clonedCopy;
  }
}
