package hex;

import water.Iced;
import water.Key;
import water.util.ArrayUtils;

public abstract class ModelBuilderCallbacks<SELF extends ModelBuilderCallbacks> extends Iced<SELF> {
  
  private static class HandledException extends Exception {
    @Override
    public String toString() {
      return "";
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
      return this;
    }
  }
  
  private static final HandledException HANDLED = new HandledException();
  
  public void wrapCompute(ModelBuilder builder, Runnable compute) { compute.run(); }
  
  /**
   * Callback for successfully finished model builds
   *
   * @param modelKey key of built Model.
   */
  public void onModelSuccess(Key<Model> modelKey) {}

  /**
   * Callback for failed model builds
   *
   * @param modelKey   Key of the model that was attempted at being built.
   * @param cause      An instance of {@link Throwable} - cause of failure
   * @param parameters An instance of Model.Parameters used in the attempt to build the model
   */
  public void onModelFailure(Key<Model> modelKey, Throwable cause, Model.Parameters parameters) {}

  /**
   * subclasses may want to call this before processing exceptions on model failure.
   * @param cause
   * @return true if the exception is considered as having already been handled and can be ignored.
   *         false otherwise: in this case the exception is automatically marked as handled for future checks, 
   *                          but current code is supposed to handle it immediately.
   */
  protected boolean checkExceptionHandled(Throwable cause) {
    if (ArrayUtils.contains(cause.getSuppressed(), HANDLED)) return true;
    cause.addSuppressed(HANDLED);
    return false;
  }
  
}
