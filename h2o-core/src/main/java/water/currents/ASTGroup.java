package water.currents;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import water.AutoBuffer;
import water.H2O;
import water.MRTask;
import water.Iced;
import water.fvec.*;
import water.nbhm.NonBlockingHashMapLong;
import water.util.ArrayUtils;

/** GroupBy
 *  Group the rows of 'data' by unique combinations of '[group-by-cols]',
 *  ordering the results by [order-by-cols[.  Apply function 'fcn' to a Frame
 *  for each group, with a single column argument, and a NA-handling flag.
 *  Sets of tuples {fun,col,na} are allowed.
 *
 *  'fcn' must be a one of a small set of functions, all reductions, and 'GB'
 *  returns a row per unique group, with the first columns being the grouping
 *  column, and the last column the reduction result(s).
 *
 *  The returned column(s).
 *  
 */
class ASTGroup extends ASTPrim {
  enum NAHandling { ALL, RM, IGNORE }
  @Override int nargs() { return -1; } // (GB data [group-by-cols] [order-by-cols] {fcn col "na"}...)
  @Override public String str() { return "GB"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    int ncols = fr.numCols();
    ASTNumList groupby = check(ncols, asts[2]);
    ASTNumList orderby = check(ncols, asts[3]);
    AGG[] agg = new AGG[(asts.length-3)/3];
    for( int idx = 4; idx < asts.length; idx += 3 ) {
      AST fcn = asts[idx].exec(env).getFun();
      ASTNumList col = check(ncols,asts[idx+1]);
      if( col.cnt() != 1 ) throw new IllegalArgumentException("Group-By functions take only a single column");
      NAHandling na = NAHandling.valueOf(asts[idx+2].exec(env).getStr().toUpperCase());
      agg[(idx-3)/3] = new AGG(fcn,(int)col.min(),na);
    }

    throw H2O.unimpl();
  }

  private ASTNumList check( long dstX, AST ast ) {
    // Sanity check vs dst.  To simplify logic, jam the 1 col/row case in as a ASTNumList
    ASTNumList dim;
    if( ast instanceof ASTNumList  ) dim = (ASTNumList)ast;
    else if( ast instanceof ASTNum ) dim = new ASTNumList(((ASTNum)ast)._d.getNum());
    else throw new IllegalArgumentException("Requires a number-list, but found a "+ast.getClass());
    if( dim.isEmpty() ) return dim; // Allow empty
    if( !(0 <= dim.min() && dim.max()-1 <  dstX) &&
        !(1 == dim.cnt() && dim.max()-1 == dstX) ) // Special case of append
      throw new IllegalArgumentException("Selection must be an integer from 0 to "+dstX);
    return dim;
  }

  private static class AGG extends Iced {
    final AST _fcn;
    final int _col;
    final NAHandling _na;
    AGG( AST fcn, int col, NAHandling na ) { _fcn = fcn; _col = col; _na = na; }
  }

}
