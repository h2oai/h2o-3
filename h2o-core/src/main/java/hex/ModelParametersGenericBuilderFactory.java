package hex;

import water.util.Log;
import water.util.PojoUtils;
import water.util.PojoUtils.FieldNaming;

import java.util.HashMap;
import java.util.Map;

/**
 * A {@link ModelParametersBuilderFactory} that can dynamically generate parameters for any kind of model algorithm, 
 * as soon as one of the hyper-parameter is named {@value #ALGO_PARAM}, 
 * in which case it is recommended to obtain a new builder using a {@link CommonModelParameters} instance, 
 * that will be used to provide the standard params for all type of algos.
 * 
 * Otherwise, if there's no {@value #ALGO_PARAM} hyper-parameter, this factory behaves similarly to {@link ModelParametersBuilderFactory}.
 * 
 * TODO: future improvement. When griding over multiple algos, we may want to apply different values for an hyper-parameter with the same name on algo-A and algo-B.
 *       In this case, we should be able to handle hyper-parameters differently based on naming convention. For example using `$` to prefix the param with the algo:
 *       - GBM$_max_depth = [3, 5, 7, 9, 11]
 *       - XGBoost$_max_depth = [5, 10, 15]
 *       as soon as the algo is defined, then the params are assigned this way:
 *       - if `_my_param` is provided, check if `Algo$_my_param` is also provided: if so then apply only the latter, otherwise apply the former.
 */
public class ModelParametersGenericBuilderFactory extends ModelParametersDelegateBuilderFactory<Model.Parameters> {
  
  public static final String ALGO_PARAM = "algo";

  /**
   * A generic class containing only common {@link Model.Parameters} that can be used as initial common parameters 
   * when searching over multiple algos.
   */
  public static class CommonModelParameters extends Model.Parameters {
    @Override
    public String algoName() {
      return null;
    }

    @Override
    public String fullName() {
      return null;
    }

    @Override
    public String javaName() {
      return null;
    }

    @Override
    public long progressUnits() {
      return 0;
    }
  }

  public ModelParametersGenericBuilderFactory() {
    super();
  }

  public ModelParametersGenericBuilderFactory(FieldNaming fieldNaming) {
    super(fieldNaming);
  }

  @Override
  public ModelParametersBuilder<Model.Parameters> get(Model.Parameters initialParams) {
    return new GenericParamsBuilder(initialParams, fieldNaming);
  }

  public static class GenericParamsBuilder extends DelegateParamsBuilder<Model.Parameters> {
    
    private final Map<String, Object> hyperParams = new HashMap<>();

    public GenericParamsBuilder(Model.Parameters params, FieldNaming fieldNaming) {
      super(params, fieldNaming);
    }

    @Override
    public ModelParametersBuilder<Model.Parameters> set(String name, Object value) {
      hyperParams.put(name, value);
      return this;
    }

    @Override
    public Model.Parameters build() {
      Model.Parameters result = params;
      String algo = null;
      if (hyperParams.containsKey(ALGO_PARAM)) {
        algo = (String) hyperParams.get(ALGO_PARAM);
        result = ModelBuilder.makeParameters(algo);
        //add values from init params
        PojoUtils.copyProperties(result, params, FieldNaming.CONSISTENT);
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
