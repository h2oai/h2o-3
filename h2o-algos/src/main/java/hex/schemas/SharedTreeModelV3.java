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
    // Output fields; input fields are in the parameters list
    @API(help="Mean Square Error for Training Frame")
    public double mse;           // Total MSE, variance
    @API(help="Variable Importances")
    public TwoDimTableBase variable_importances;
  } // SharedTreeModelOutputV2
}
