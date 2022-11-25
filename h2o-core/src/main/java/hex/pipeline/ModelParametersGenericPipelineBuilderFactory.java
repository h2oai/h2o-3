package hex.pipeline;

import hex.Model;
import hex.ModelBuilder;
import hex.ModelParametersDelegateBuilderFactory;
import hex.ModelParametersGenericBuilderFactory;
import hex.pipeline.PipelineModel.PipelineParameters;
import water.util.Log;
import water.util.PojoUtils;
import water.util.PojoUtils.FieldNaming;

import java.util.HashMap;
import java.util.Map;

import static hex.ModelParametersGenericBuilderFactory.ALGO_PARAM;

public class ModelParametersGenericPipelineBuilderFactory extends ModelParametersDelegateBuilderFactory<PipelineParameters> {

  public ModelParametersGenericPipelineBuilderFactory() {
    super();
  }

  @Override
  public ModelParametersBuilder<PipelineParameters> get(PipelineParameters initialParams) {
    return new GenericPipelineParamsBuilder(initialParams, fieldNaming);
  }

  public static class GenericPipelineParamsBuilder extends DelegateParamsBuilder<PipelineParameters> {
    
    private final Map<String, Object> hyperParams = new HashMap<>();

    public GenericPipelineParamsBuilder(PipelineParameters params, FieldNaming fieldNaming) {
      super(params, fieldNaming);
    }

    @Override
    public ModelParametersBuilder<PipelineParameters> set(String name, Object value) {
      hyperParams.put(name, value);
      return this;
    }

    /**
     * in addition to {@link hex.ModelParametersGenericBuilderFactory.GenericParamsBuilder} can do, for pipelines I need:
     * - pipeline estimator params can be created dynamically based on some hyper-param (estimator.algo?).
     * - then other hyperparameters can be set: problem with pipeline default params? (everything is null)
     * @return
     */
    @Override
    public PipelineParameters build() {
      PipelineParameters result = params;
      Model.Parameters initEstimatorParams = result._estimatorParams;
      String algo = null;
      
      if (hyperParams.containsKey(ALGO_PARAM)) {
        algo = (String) hyperParams.get(ALGO_PARAM);
        result._estimatorParams = ModelBuilder.makeParameters(algo);
        if (initEstimatorParams != null) {
          //add values from init estimator params
          PojoUtils.copyProperties(result._estimatorParams, initEstimatorParams, FieldNaming.CONSISTENT);
        }
      }
      for (Map.Entry<String, Object> e : hyperParams.entrySet()) {
        if (ALGO_PARAM.equals(e.getKey())) continue;
        if (algo == null || result.hasParameter(fieldNaming.toDest(e.getKey()))) { // no check for `result.hasParameter` in case of strict algo, so that we can fail on invalid param
          result.setParameter(fieldNaming.toDest(e.getKey()), e.getValue());
        } else { // algo hyper-param was provided and this hyper-param is incompatible with it
          Log.debug("Ignoring hyper-parameter `"+e.getKey()+"` unsupported by `"+algo+"`.");
        }
      }
      return result;
    }
  }
}
