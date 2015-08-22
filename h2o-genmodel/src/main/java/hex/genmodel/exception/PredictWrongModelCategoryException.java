package hex.genmodel.exception;

/**
 * Wrong model category exception.
 *
 * Ea ch generated model is of exactly one category.
 * Only one of the different predict calls works with that category.
 *
 * For example, a model of category Binomial can only respond properly to
 * predictBinomial().
 *
 * Attempting to call the wrong predict method for a model results in this exception.
 */
public class PredictWrongModelCategoryException extends AbstractPredictException {
  public PredictWrongModelCategoryException(String message) {
    super(message);
  }
}
