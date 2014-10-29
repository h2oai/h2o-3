package hex.gbm;

import hex.ModelBuilder;

public abstract class SharedTree<M extends SharedTreeModel<M,P,O>, P extends SharedTreeModel.SharedTreeParameters, O extends SharedTreeModel.SharedTreeOutput> extends ModelBuilder<M,P,O> {
  public SharedTree( String name, P parms) { super(name,parms); }
}
