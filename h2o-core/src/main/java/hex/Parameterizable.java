package hex;

/**
 * Must be implemented by classes that can be configured dynamically during HyperParameter-Optimization.
 */
public interface Parameterizable {

  /**
   * @param name hyperparameter name.
   * @return true if this hyperparameter is generally supported.
   */
  boolean hasParameter(String name);

  /**
   * 
   * @param name hyperparameter name.
   * @return the current value for the given hyperparameter.
   */
  Object getParameter(String name);

  /**
   * 
   * @param name hyperparameter name.
   * @param value the new value to assign for the given hyperparameter.
   */
  void setParameter(String name, Object value);

  /**
   * 
   * @param name hyperparameter name.
   * @return true iff the hyperparameter is currently set to its default value.
   */
  boolean isParameterSetToDefault(String name);

  /**
   * 
   * @param name hyperparameter name.
   * @return the default value for the given hyperparameter.
   */
  Object getParameterDefaultValue(String name);

  /**
   * 
   * @param name hyperparameter name.
   * @return true iff the given hyperparameter is allowed to be modified/reassigned on this instance.
   */
  boolean isParameterAssignable(String name);
}
