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
  
  public void beforeCompute(Model.Parameters params) {}
  
  public void beforeCompute(ModelBuilder builder) {}
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
  
  protected boolean checkExceptionHandled(Throwable cause) {
    if (ArrayUtils.contains(cause.getSuppressed(), HANDLED)) return true;
    cause.addSuppressed(HANDLED);
    return false;
  }
  
}
