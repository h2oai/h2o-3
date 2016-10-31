package hex.genmodel.easy.exception;

/**
 * Unknown type exception.
 *
 * When a RowData observation is provided to a predict method, the value types are extremely restricted.
 * This exception occurs if the value of a numeric feature fails to parse as Double. Ex. empty string
 *
 */
public class PredictNumberFormatException extends PredictException {
  public PredictNumberFormatException(String message) {
    super(message);
  }
}
