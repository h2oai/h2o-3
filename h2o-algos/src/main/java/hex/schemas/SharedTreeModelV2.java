package hex.schemas;

import hex.tree.SharedTreeModel;
import water.api.API;
import water.api.ModelOutputSchema;
import water.api.ModelSchema;
import water.api.TwoDimTableV1;

public abstract class SharedTreeModelV2<M extends SharedTreeModel<M, P, O>,
                                        S extends SharedTreeModelV2<M, S, P, PS, O, OS>,
                                        P extends SharedTreeModel.SharedTreeParameters,
                                        PS extends SharedTreeV2.SharedTreeParametersV2<P, PS>,
                                        O extends SharedTreeModel.SharedTreeOutput,
                                        OS extends SharedTreeModelV2.SharedTreeModelOutputV2<O,OS>>
        extends ModelSchema<M, S, P, PS, O, OS> {

  public static class SharedTreeModelOutputV2<O extends SharedTreeModel.SharedTreeOutput, SO extends SharedTreeModelOutputV2<O,SO>> extends ModelOutputSchema<O, SO> {
    // Output fields; input fields are in the parameters list
    @API(help="Mean Square Error")
    public double mse;           // Total MSE, variance
    @API(help="Variable Importances")
    public TwoDimTableV1 variableImportances;

  } // SharedTreeModelOutputV2
}
