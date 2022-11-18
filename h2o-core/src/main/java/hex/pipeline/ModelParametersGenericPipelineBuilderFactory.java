package hex.pipeline;

import hex.Model;
import hex.ModelParametersGenericBuilderFactory;
import water.util.PojoUtils;

public class ModelParametersGenericPipelineBuilderFactory extends ModelParametersGenericBuilderFactory {

  public ModelParametersGenericPipelineBuilderFactory() {
    super();
  }

  @Override
  public ModelParametersBuilder<Model.Parameters> get(Model.Parameters initialParams) {
    return new GenericPipelineParamsBuilder(initialParams, fieldNaming);
  }

  public static class GenericPipelineParamsBuilder extends GenericParamsBuilder {

    public GenericPipelineParamsBuilder(Model.Parameters params, PojoUtils.FieldNaming fieldNaming) {
      super(params, fieldNaming);
    }

    /**
     * TODO: on top of what {@link hex.ModelParametersGenericBuilderFactory.GenericParamsBuilder} can do, for pipelines I need:
     * - pipeline estimator params can be created dynamically based on some hyper-param (estimator.algo?).
     * - then other hyper parameters can be set: problem with pipeline default params (everything is null!!!)
     * @return
     */
    @Override
    public Model.Parameters build() {
      return super.build();
    }
  }
}
