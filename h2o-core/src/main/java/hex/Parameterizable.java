package hex;

/**
 * Must be implemented by classes that can be configured dynamically during HyperParameter-Optimization.
 */
public interface Parameterizable<SELF extends Parameterizable> {

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

  /**
   * To be implemented by subclasses to provide a proper copy that can be then parametrized without risk of modifying the original.
   * This is necessary for hyperparameter search to work properly: 
   * for most subclasses it can default to a basic clone, 
   * but some others (e.g. compound model parameters like {@link hex.pipeline.PipelineModel.PipelineParameters}) 
   * may also need to create fresh keys pointing to fresh objects: a simple {@code clone} or {@code deepClone} would not be enough for those.
   * 
   * Also, simply overriding {@code clone} would have unsuitable side effects as simple cloning is necessary and used during serialization.
   * 
   * @return a copy of the current instance, safe to use in hyperparameter search.
   */
  SELF freshCopy();
}
