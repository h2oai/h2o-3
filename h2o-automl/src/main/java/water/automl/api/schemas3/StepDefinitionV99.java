package water.automl.api.schemas3;

import ai.h2o.automl.StepDefinition;
import ai.h2o.automl.StepDefinition.Alias;
import ai.h2o.automl.StepDefinition.Step;
import water.api.API;
import water.api.EnumValuesProvider;
import water.api.Schema;
import water.util.Log;

import java.util.Arrays;

public final class StepDefinitionV99 extends Schema<StepDefinition, StepDefinitionV99> {

  public static final class StepV99 extends Schema<Step, StepV99> {
    @API(help="The id of the step (must be unique per step provider).", direction=API.Direction.INOUT)
    public String id;
    @API(help="The relative weight for the given step (can impact time and/or number of models allocated for this step).", direction=API.Direction.INOUT)
    public int weight;
  }  
  
  public static final class AliasProvider extends EnumValuesProvider<Alias> {
    public AliasProvider() {
      super(Alias.class);
    }
  }

  @API(help="Name of the step provider (usually, this is also the name of an algorithm).", direction=API.Direction.INOUT)
  public String name;
  @API(help="An alias representing a predefined list of steps to be executed.", valuesProvider=AliasProvider.class, direction=API.Direction.INOUT)
  public Alias alias;
  @API(help="The list of steps to be executed (Mutually exclusive with alias).", direction=API.Direction.INOUT)
  public StepV99[] steps;

  @Override
  public StepDefinitionV99 fillFromAny(Object o) {
    if (o.getClass().isArray()) {
      Object[] os = (Object[])o;
      assert os.length > 0;
      assert os[0] instanceof String;
      name = (String) os[0];
      if (os.length == 1) {
        alias = Alias.all;
      } else if (os[1] instanceof String) {
        alias = Alias.valueOf((String) os[1]);
      } else if (os[1].getClass().isArray()) {
        assert os[1].getClass().getComponentType() == String.class;
        String[] stepIds = (String[])os[1];
        steps = new StepV99[stepIds.length];
        for (int i=0; i<stepIds.length; i++) {
          StepV99 step = steps[i] = new StepV99();
          step.id = stepIds[i];
        }
      } else {
        Log.warn("Malformed step definition, skipping steps: "+ Arrays.toString(os));
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

