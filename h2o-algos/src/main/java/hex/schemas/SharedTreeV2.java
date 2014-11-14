package hex.schemas;

import hex.tree.SharedTree;
import hex.tree.SharedTreeModel.SharedTreeParameters;
import water.api.API;
import water.api.ModelParametersSchema;

public abstract class SharedTreeV2 extends ModelBuilderSchema<SharedTree,SharedTreeV2,SharedTreeV2.SharedTreeParametersV2> {

  public abstract static class SharedTreeParametersV2 extends ModelParametersSchema<SharedTreeParameters, SharedTreeParametersV2> {
    static public String[] own_fields = new String[] {
        "ntrees"
    };

    // Input fields
    @API(help="Number of trees. Grid Search, comma sep values:50,100,150,200")
    public int ntrees;

    @Override public SharedTreeParametersV2 fillFromImpl(SharedTreeParameters parms) {
      super.fillFromImpl(parms);
      return this;
    }

  }
}
