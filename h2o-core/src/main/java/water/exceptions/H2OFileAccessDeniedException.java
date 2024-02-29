package water.exceptions;

/**
 * Exception thrown when a file matches file_deny_glob
 */
public class H2OFileAccessDeniedException extends H2OAbstractRuntimeException {

  public H2OFileAccessDeniedException(String message, String dev_message) {
    super(message, dev_message);
  }

  public H2OFileAccessDeniedException(String message) {
    super(message, message);
  }

}
