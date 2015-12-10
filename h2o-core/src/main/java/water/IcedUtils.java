package water;

/**
 * Utility class to support Iced objects.
 */
public class IcedUtils {

  /** Deep-copy clone given iced object. */
  static public <T extends Iced> T deepCopy(T iced) {
    AutoBuffer ab = new AutoBuffer();
    iced.write(ab);
    ab.flipForReading();
    // Create a new instance
    return (T) TypeMap.newInstance(iced.frozenType()).read(ab);
  }
}
