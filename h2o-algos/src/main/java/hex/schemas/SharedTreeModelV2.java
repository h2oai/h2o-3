package hex.schemas;

import hex.tree.SharedTreeModel;
import water.api.API;
import water.api.ModelOutputSchema;
import water.api.ModelSchema;
//import water.util.DocGen.HTML;

public abstract class SharedTreeModelV2 extends ModelSchema<SharedTreeModel, SharedTreeModel.SharedTreeParameters, SharedTreeModel.SharedTreeOutput, SharedTreeModelV2> {

  public abstract static class SharedTreeModelOutputV2 extends ModelOutputSchema<SharedTreeModel.SharedTreeOutput, SharedTreeModelOutputV2> {
    // Output fields; input fields are in the parameters list
    @API(help="Mean Square Error")
    public double mse;           // Total MSE, variance

  } // SharedTreeModelOutputV2
}
