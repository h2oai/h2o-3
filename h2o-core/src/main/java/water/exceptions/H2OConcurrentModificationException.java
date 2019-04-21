package water.exceptions;

/**
 * H2OConcurrentModificationException signals that object was modified while being used in another
 * operation.
 * Example use case is deleting a Vec while a checksum is being calculated on it.
 */
public class H2OConcurrentModificationException extends H2OAbstractRuntimeException {

  public H2OConcurrentModificationException(String message) {
    super(message, message);
  }

}
