package hex.genmodel.exception;

/**
 * Unknown type exception.
 *
 * When a RowData observation is provided to a predict method, the value types are extremely restricted.
 * This exception occurs if the value of a RowData element is of the wrong data type.
 *
 * (The only supported value types are String and Double.)
 */
public class PredictUnknownTypeException extends AbstractPredictException {
  public PredictUnknownTypeException(String message) {
    super(message);
  }
}
