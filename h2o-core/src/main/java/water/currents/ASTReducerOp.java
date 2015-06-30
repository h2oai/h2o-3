package water.currents;

import water.H2O;
import water.MRTask;
import water.fvec.*;
import water.parser.ValueString;
import water.currents.Val.*;

/**
 * Subclasses take a Frame and a na.rm flag and produce a scalar
 */
abstract class ASTReducerOp extends ASTPrim {
  @Override int nargs() { return 1+2; }
  @Override ValNum apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    double rmna = stk.track(asts[2].exec(env)).getNum();
    if( rmna != 0 && rmna != 1 ) throw new IllegalArgumentException("Expected a 0/NAs propagate or 1/NAs ignored");
    return new ValNum(rmna==1 ? new NaRmRedOp(this).doAll(fr)._d : new RedOp(this).doAll(fr)._d);
  }
  /** Override to express a basic math primitive */
  abstract double op( double l, double r );

  static class RedOp extends MRTask<RedOp> {
    double _d;
    final ASTReducerOp _bin;
    RedOp( ASTReducerOp bin ) { _bin=bin; }
    @Override public void map( Chunk chks[] ) {
      int rows = chks[0]._len;
      for( Chunk C : chks ) {
        if( !C.vec().isNumeric() ) throw new IllegalArgumentException("Numeric columns only");
        double sum = _d;
        for( int r = 0; r < rows; r++ )
          sum = _bin.op(sum, C.atd(r));
        _d = sum;
        if( Double.isNaN(sum) ) break; // Shortcut if the reduction is already NaN
      }
    }
    @Override public void reduce( RedOp s ) { _d = _bin.op(_d,s._d); }
  }

  static class NaRmRedOp extends MRTask<NaRmRedOp> {
    double _d;
    final ASTReducerOp _bin;
    NaRmRedOp( ASTReducerOp bin ) { _bin=bin; }
    @Override public void map( Chunk chks[] ) {
      int rows = chks[0]._len;
      for( Chunk C : chks ) {
        if( !C.vec().isNumeric() ) throw new IllegalArgumentException("Numeric columns only");
        double sum = _d;
        for( int r = 0; r < rows; r++ ) {
          double d = C.atd(r);
          if( !Double.isNaN(d) )
            sum = _bin.op(sum, d);
        }
        _d = sum;
        if( Double.isNaN(sum) ) break; // Shortcut if the reduction is already NaN
      }
    }
    @Override public void reduce( NaRmRedOp s ) { _d = _bin.op(_d, s._d); }
  }
}

// ----------------------------------------------------------------------------
class ASTSum  extends ASTReducerOp { String str() { return "sum" ; } double op( double l, double r ) { return l+r; } }
class ASTMin  extends ASTReducerOp { String str() { return "min" ; } double op( double l, double r ) { return Math.min(l,r); } }
class ASTMax  extends ASTReducerOp { String str() { return "max" ; } double op( double l, double r ) { return Math.max(l,r); } }
class ASTMean extends ASTReducerOp { String str() { return "mean"; } double op( double l, double r ) { return l+r; }
  @Override int nargs() { return 1+3; } // mean ary trim narm
  @Override ValNum apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    double trim = stk.track(asts[2].exec(env)).getNum();
    double rmna = stk.track(asts[3].exec(env)).getNum();
    if( rmna != 0 && rmna != 1 ) throw new IllegalArgumentException("Expected a 0/NAs propagate or 1/NAs ignored");
    double sum = rmna==1 ? new NaRmRedOp(this).doAll(fr)._d : new RedOp(this).doAll(fr)._d;
    long cnt = fr.numCols()*fr.numRows();
    if( rmna == 1 )
      for( Vec vec : fr.vecs() )
        cnt -= vec.naCnt();
    return new ValNum(sum/cnt);
  }
}
