package water.currents;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import water.AutoBuffer;
import water.H2O;
import water.MRTask;
import water.fvec.*;
import water.nbhm.NonBlockingHashMapLong;
import water.util.ArrayUtils;

/** GroupBy

 *  Group the rows of 'data' by unique combinations of '[cols]'.
 *  Apply function 'fun' to a Frame for each group.
 *
 *  'fun' must be a reduction, and 'group' returns a row per unique group, with
 *  the first columns being the grouping column, and the last column the
 *  reduction result.
 *
 *  The returned column 
 *  
 */
class ASTGroup extends ASTPrim {
  @Override int nargs() { return -1; } // (groupby data [cols] {frame . (= frame$Sepal.Length 17)})
  @Override public String str() { return "groupby"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    throw H2O.unimpl();
  }
}
