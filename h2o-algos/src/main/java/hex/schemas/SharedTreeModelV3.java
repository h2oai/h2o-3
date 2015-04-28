package hex.schemas;

import hex.tree.SharedTreeModel;
import water.api.API;
import water.api.ModelOutputSchema;
import water.api.ModelSchema;
import water.api.TwoDimTableBase;

public class SharedTreeModelV3<M extends SharedTreeModel<M, P, O>,
                                        S extends SharedTreeModelV3<M, S, P, PS, O, OS>,
                                        P extends SharedTreeModel.SharedTreeParameters,
                                        PS extends SharedTreeV3.SharedTreeParametersV3<P, PS>,
                                        O extends SharedTreeModel.SharedTreeOutput,
                                        OS extends SharedTreeModelV3.SharedTreeModelOutputV3<O,OS>>
        extends ModelSchema<M, S, P, PS, O, OS> {

  public static class SharedTreeModelOutputV3<O extends SharedTreeModel.SharedTreeOutput, SO extends SharedTreeModelOutputV3<O,SO>> extends ModelOutputSchema<O, SO> {
    @API(help="Variable Importances", direction=API.Direction.OUTPUT, level = API.Level.secondary)
    TwoDimTableBase variable_importances;

    @API(help="The Intercept term, the initial model function value to which trees make adjustments", direction=API.Direction.OUTPUT)
    double init_f;
  }
}
