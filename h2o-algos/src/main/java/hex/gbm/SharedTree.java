package hex.gbm;

import water.*;
import water.H2O.H2OCountedCompleter;
import hex.*;
import hex.schemas.*;

public abstract class SharedTree<M extends SharedTreeModel<M,P,O>, P extends SharedTreeModel.SharedTreeParameters, O extends SharedTreeModel.SharedTreeOutput> extends ModelBuilder<M,P,O> {
  // Called from an http request
  public SharedTree( P parms) {
    super("SharedTree",parms);
  }
}
