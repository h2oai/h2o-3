package hex;

import hex.Model;
import hex.ModelBuilder;
import hex.ModelParametersBuilderFactory;
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
 */
public class ModelParametersGenericBuilderFactory extends ModelParametersDelegateBuilderFactory<Model.Parameters> {
  
  public static final String ALGO_PARAM = "algo";
  
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
      if (hyperParams.containsKey(ALGO_PARAM)) {
        result = ModelBuilder.makeParameters((String) hyperParams.get(ALGO_PARAM));
        //add values from init params
        PojoUtils.copyProperties(result, params, FieldNaming.CONSISTENT);
      }
      for (Map.Entry<String, Object> e : hyperParams.entrySet()) {
        if (ALGO_PARAM.equals(e.getKey())) continue;
        result.setParameter(fieldNaming.toDest(e.getKey()), e.getValue());
      }
      return result;
    }
  }
}
