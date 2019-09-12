package water.automl.api.schemas3;

import ai.h2o.automl.Algo;
import ai.h2o.automl.StepDefinition;
import ai.h2o.automl.StepDefinition.Alias;
import water.api.API;
import water.api.EnumValuesProvider;
import water.api.Schema;
import water.util.Log;

public final class StepDefinitionV99 extends Schema<StepDefinition, StepDefinitionV99> {

  public static final class AliasProvider extends EnumValuesProvider<Alias> {
    public AliasProvider() {
      super(Alias.class);
    }
  }

  @API(help="", direction=API.Direction.INOUT)
  public String name;
  @API(help="", valuesProvider=AliasProvider.class, direction=API.Direction.INOUT)
  public Alias alias;
  @API(help="", direction=API.Direction.INOUT)
  public String[] ids;

  @Override
  public StepDefinitionV99 fillFromAny(Object o) {
    if (o.getClass().isArray()) {
      Object[] steps = (Object[])o;
      assert steps.length > 0;
      assert steps[0] instanceof String;
      name = (String) steps[0];
      if (steps.length == 1) {
        alias = Alias.all;
      } else if (steps[1] instanceof String) {
        alias = Alias.valueOf((String) steps[1]);
      } else if (steps[1].getClass().isArray()) {
        assert steps[1].getClass().getComponentType() == String.class;
        ids = (String[]) steps[1];
      } else {
        Log.warn("Blah blah blah");
      }
      return this;
    } else if (o instanceof String) {
      name = (String)o;
      alias = Alias.all;
      return this;
    }
    return super.fillFromAny(o);
  }
}

