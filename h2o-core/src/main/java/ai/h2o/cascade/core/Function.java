package ai.h2o.cascade.core;

import ai.h2o.cascade.vals.Val;
import ai.h2o.cascade.Cascade;

/**
 */
public abstract class Function {
  public Scope scope;

  public abstract Val apply0(Val[] args);


  //--------------------------------------------------------------------------------------------------------------------
  // Exceptions
  //--------------------------------------------------------------------------------------------------------------------

  /**
   * Exception to be raised within the body of a function, indicating that the
   * type of the {@code index}'th argument is incorrect.
   *
   * <p> If the type error refers to overall shape of all arguments, then
   * {@code index} will be -1.
   *
   * <p> This exception will be caught within {@code AstApply} and converted
   * into a {@link Cascade.TypeError}, translating the argument's {@code index}
   * into its location within the cascade expression being executed.
   */
  public static class TypeError extends IllegalArgumentException {
    public int index;

    public TypeError(int i, String message) {
      super(message);
      index = i;
    }
  }


  /**
   * Exception to be raised within the body of a function, indicating that the
   * value of the {@code i}'th argument is incorrect.
   *
   * <p> This exception will be caught within {@code AstApply} and converted
   * into a {@link Cascade.ValueError}, translating the argument's
   * {@code index} into its location within the cascade expression being
   * executed.
   */
  public static class ValueError extends IllegalArgumentException {
    public int index;

    public ValueError(int i, String message) {
      super(message);
      index = i;
    }
  }


  /**
   * Exception to be raised within the body of a function, indicating an error
   * condition that is not related to any argument in particular.
   *
   * <p> This exception will be caught within {@code AstApply} and converted
   * into a {@link Cascade.RuntimeError}.
   */
  public static class RuntimeError extends RuntimeException {
    public RuntimeError(String message) {
      super(message);
    }
  }

}
