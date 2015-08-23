package hex.genmodel.easy.exception;

/**
 * Unknown categorical level exception.
 *
 * A categorical column is equivalent to a factor column or an enum column.
 * A column in which different values can only be compared for equality with one another, but
 * not distance.
 *
 * This exception occurs when the data point to predict contains a value that was not seen
 * during model training.
 *
 * This can definitely happen as a result of the user providing bad input.
 */
public class PredictUnknownCategoricalLevelException extends AbstractPredictException {
  public PredictUnknownCategoricalLevelException(String message) {
    super(message);
  }
}
