package hex;

public interface DataTransformSupport extends CVSupport {
  /**
   * @return an identifier shared by the entire model building lifecycle (from training, including sub-models, to scoring after model completion),
   * allowing {@link DataTransformer} implementations to refer to it to build/retrieve objects needed during this lifecycle. 
   */
  String getModelLifecycleId();
}
