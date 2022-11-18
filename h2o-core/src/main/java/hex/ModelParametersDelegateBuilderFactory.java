package hex;

import water.util.PojoUtils.FieldNaming;

public class ModelParametersDelegateBuilderFactory<MP extends Model.Parameters> implements ModelParametersBuilderFactory<MP> {
  
  protected final FieldNaming fieldNaming;

  public ModelParametersDelegateBuilderFactory() {
    this(FieldNaming.CONSISTENT);
  }

  public ModelParametersDelegateBuilderFactory(FieldNaming fieldNaming) {
    this.fieldNaming = fieldNaming;
  }

  @Override
  public ModelParametersBuilder<MP> get(MP initialParams) {
    return new DelegateParamsBuilder<>(initialParams, fieldNaming);
  }

  @Override
  public FieldNaming getFieldNamingStrategy() {
    return fieldNaming;
  }
  
  public static class DelegateParamsBuilder<MP extends Model.Parameters>
          implements ModelParametersBuilder<MP> {
    
    protected final MP params;
    protected final FieldNaming fieldNaming;
    

    protected DelegateParamsBuilder(MP params, FieldNaming fieldNaming) {
      this.params = params;
      this.fieldNaming = fieldNaming;
    }

    @Override
    public ModelParametersBuilder<MP> set(String name, Object value) {
      this.params.setParameter(fieldNaming.toDest(name), value);
      return this;
    }

    @Override
    public MP build() {
      return params;
    }
  }
}
