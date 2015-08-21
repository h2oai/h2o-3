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

 *  Group the rows of 'data' by unique combinations of '[cols]'.  Apply
 *  function 'fcn' to a Frame for each group, with a single column argument,
 *  and a NA-handling flag.  Sets of tuples {fun,col,na} are allowed.
 *
 *  'fcn' must be a one of a small set of functions, all reductions, and 'GB'
 *  returns a row per unique group, with the first columns being the grouping
 *  column, and the last column the reduction result(s).
 *
 *  The returned column(s).
 *  
 */
class ASTGroup extends ASTPrim {
  @Override int nargs() { return -1; } // (GB data [group-by-cols] [order-by-cols] (AGG ...) )
  @Override public String str() { return "GB"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    throw H2O.unimpl();
  }
}

// Placeholder class to group aggregate descriptionbs
class ASTAGG extends ASTPrim {
  @Override int nargs() { return -1; } // (AGG fcn "arg-col-for-fcn" "na_handling" ... )
  @Override public String str() { return "AGG"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    throw H2O.fail();
  }
}
