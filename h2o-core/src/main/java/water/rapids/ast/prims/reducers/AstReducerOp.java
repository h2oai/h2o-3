package water.rapids.ast.prims.reducers;

import water.MRTask;
import water.fvec.Chunk;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.vals.ValNum;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;

/**
 * Subclasses take a Frame and produces a scalar.  NAs -> NAs
 */
public abstract class AstReducerOp extends AstPrimitive {
  @Override
  public int nargs() {
    return -1;
  }

  @Override
  public Val apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    // NOTE: no *initial* value needed for the reduction.  Instead, the
    // reduction op is used between pairs of actual values, and never against
    // the empty list.  NaN is returned if there are *no* values in the
    // reduction.
    double d = Double.NaN;
    for (int i = 1; i < asts.length; i++) {
      Val val = asts[i].exec(env);
      double d2 = val.isFrame() ? new AstReducerOp.RedOp().doAll(stk.track(val).getFrame())._d : val.getNum();
      if (i == 1) d = d2;
      else d = op(d, d2);
    }
    return new ValNum(d);
  }

  /**
   * Override to express a basic math primitive
   */
  public abstract double op(double l, double r);

  class RedOp extends MRTask<AstReducerOp.RedOp> {
    double _d;

    @Override
    public void map(Chunk chks[]) {
      int rows = chks[0]._len;
      for (Chunk C : chks) {
        if (!C.vec().isNumeric()) throw new IllegalArgumentException("Numeric columns only");
        double sum = _d;
        for (int r = 0; r < rows; r++)
          sum = op(sum, C.atd(r));
        _d = sum;
        if (Double.isNaN(sum)) break; // Shortcut if the reduction is already NaN
      }
    }

    @Override
    public void reduce(AstReducerOp.RedOp s) {
      _d = op(_d, s._d);
    }
  }

//  class NaRmRedOp extends MRTask<NaRmRedOp> {
//    double _d;
//    @Override public void map( Chunk chks[] ) {
//      int rows = chks[0]._len;
//      for( Chunk C : chks ) {
//        if( !C.vec().isNumeric() ) throw new IllegalArgumentException("Numeric columns only");
//        double sum = _d;
//        for( int r = 0; r < rows; r++ ) {
//          double d = C.atd(r);
//          if( !Double.isNaN(d) )
//            sum = op(sum, d);
//        }
//        _d = sum;
//        if( Double.isNaN(sum) ) break; // Shortcut if the reduction is already NaN
//      }
//    }
//    @Override public void reduce( NaRmRedOp s ) { _d = op(_d, s._d); }
//  }

}
