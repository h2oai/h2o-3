package hex.genmodel.easy.error;

import hex.genmodel.easy.EasyPredictModelWrapper;

/**
 * A void implementation of {@link hex.genmodel.easy.EasyPredictModelWrapper.ErrorConsumer}.
 * It's purpose is to avoid forcing developers do to null checks in code before each and every call.
 */
public final class VoidErrorConsumer extends EasyPredictModelWrapper.ErrorConsumer {

  @Override
  public final void dataTransformError(String columnName, Object value, String message) {
    //Do nothing on purpose to avoid the need for null checks
  }

  @Override
  public final void unseenCategorical(String columnName, Object value, String message) {
    //Do nothing on purpose to avoid the need for null checks
  }
}
