package hex.grid;

import hex.Model;
import hex.ModelParametersBuilderFactory;
import water.util.PojoUtils;

/**
 * The factory is producing a parameters builder which uses reflection to setup field values.
 *
 * @param <MP> type of model parameters object
 */
public class SimpleParametersBuilderFactory<MP extends Model.Parameters>
        implements ModelParametersBuilderFactory<MP> {

  @Override
  public ModelParametersBuilder<MP> get(MP initialParams) {
    return new SimpleParamsBuilder<>(initialParams);
  }

  @Override
  public PojoUtils.FieldNaming getFieldNamingStrategy() {
    return PojoUtils.FieldNaming.CONSISTENT;
  }

  /**
   * The builder modifies initial model parameters directly by reflection.
   * <p>
   * Usage:
   * <pre>{@code
   *   GBMModel.GBMParameters params =
   *     new SimpleParamsBuilder(initialParams)
   *      .set("_ntrees", 30)
   *      .set("_learn_rate", 0.01)
   *      .build()
   * }</pre>
   *
   * @param <MP> type of model parameters object
   */
  public static class SimpleParamsBuilder<MP extends Model.Parameters>
          implements ModelParametersBuilder<MP> {

    final private MP params;

    public SimpleParamsBuilder(MP initialParams) {
      params = initialParams;
    }

    @Override
    public boolean isAssignable(String name) {
      return params.isParameterAssignable(name);
    }

    @Override
    public ModelParametersBuilder<MP> set(String name, Object value) {
      PojoUtils.setField(params, name, value, PojoUtils.FieldNaming.CONSISTENT);
      return this;
    }

    @Override
    public MP build() {
      return params;
    }
  }
}
