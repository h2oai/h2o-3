package water;

/**
 * Global constants used throughout the H2O-3 project.
 */
public class H2OConstants {

  /**
   * Maximum number of elements allocable for a single aray
   */
  public static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

  /**
   * Maximum size of an array, minus one more byte reserved a trailing zero.
   * Non-final for testing purpose.
   */
  public static final int MAX_STR_LEN = MAX_ARRAY_SIZE - 1;
}
