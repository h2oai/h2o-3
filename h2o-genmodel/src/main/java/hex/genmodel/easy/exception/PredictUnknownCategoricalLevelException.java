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
public class PredictUnknownCategoricalLevelException extends PredictException {
  public final String columnName;
  public final String unknownLevel;

  public PredictUnknownCategoricalLevelException(String message, String columnName, String unknownLevel) {
    super(message);
    this.columnName = columnName;
    this.unknownLevel = unknownLevel;
  }

  /**
   * Get the column name for which the unknown level was given as input.
   * @return Column name
   */
  @SuppressWarnings("unused")
  public String getColumnName() {
    return columnName;
  }

  /**
   * Get the unknown level which was not seen during model training.
   * @return Unknown level
   */
  @SuppressWarnings("unused")
  public String getUnknownLevel() {
    return unknownLevel;
  }
}
