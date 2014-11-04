package hex.schemas;

import hex.tree.SharedTree;
import water.api.Handler;
import water.api.Schema;
import water.H2O;

public abstract class SharedTreeHandler<I extends SharedTree, S extends Schema<I,S>> extends Handler<I,S> {
  @Override public void compute2() { throw H2O.fail(); }
}
