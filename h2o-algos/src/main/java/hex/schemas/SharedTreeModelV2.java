package hex.schemas;

import hex.tree.SharedTreeModel;
import water.api.API;
import water.api.ModelOutputSchema;
import water.api.ModelSchema;
import water.api.TwoDimTableV1;

public abstract class SharedTreeModelV2<M extends SharedTreeModel, S extends SharedTreeModelV2<M, S, P, O>, P extends SharedTreeModel.SharedTreeParameters, O extends SharedTreeModel.SharedTreeOutput> extends ModelSchema<M, S, P, O> {

  public abstract static class SharedTreeModelOutputV2<O extends SharedTreeModel.SharedTreeOutput, SO extends SharedTreeModelOutputV2<O,SO>> extends ModelOutputSchema<O, SO> {
    // Output fields; input fields are in the parameters list
    @API(help="Mean Square Error")
    public double mse;           // Total MSE, variance
    @API(help="Variable Importances")
    public TwoDimTableV1 variableImportances;
  
  } // SharedTreeModelOutputV2
}
