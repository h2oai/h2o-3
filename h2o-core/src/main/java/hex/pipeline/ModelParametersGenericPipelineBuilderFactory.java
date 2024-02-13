package hex.pipeline;

import hex.Model;
import hex.ModelBuilder;
import hex.ModelParametersDelegateBuilderFactory;
import hex.pipeline.PipelineModel.PipelineParameters;
import water.util.Log;
import water.util.PojoUtils;
import water.util.PojoUtils.FieldNaming;

import java.util.HashMap;
import java.util.Map;

import static hex.ModelParametersGenericBuilderFactory.ALGO_PARAM;

/**
 * Similar to {@link hex.ModelParametersGenericBuilderFactory} but for pipelines:
 * - pipeline estimator params can be created dynamically based on {@value hex.ModelParametersGenericBuilderFactory#ALGO_PARAM} hyper-param.
 * - then other hyper-parameters can be set.
 */
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
