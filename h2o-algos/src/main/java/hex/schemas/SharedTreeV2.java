package hex.schemas;

import hex.gbm.SharedTree;
import hex.gbm.SharedTreeModel.SharedTreeParameters;
import water.api.API;
import water.api.ModelParametersSchema;
import water.fvec.Frame;
import water.util.PojoUtils;

public abstract class SharedTreeV2 extends ModelBuilderSchema<SharedTree,SharedTreeV2,SharedTreeV2.SharedTreeParametersV2> {

  public abstract static class SharedTreeParametersV2 extends ModelParametersSchema<SharedTreeParameters, SharedTreeParametersV2> {
    public String[] fields() { return new String[] {
        "destination_key",
        "training_frame",
        "ntrees"
      }; }

    // Input fields
    @API(help="Number of trees. Grid Search, comma sep values:50,100,150,200")
    public int ntrees;

    @Override public SharedTreeParametersV2 fillFromImpl(SharedTreeParameters parms) {
      super.fillFromImpl(parms);
      return this;
    }

  }
}
