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
  Frame _fr;
  boolean _rmna;
  @Override int nargs() { return 1+2; }
  @Override ValNum apply( Env env, Env.StackHelp stk, AST asts[] ) {
    _fr = stk.track(asts[1].exec(env)).getFrame();
    double rmna = stk.track(asts[2].exec(env)).getNum();
    if( rmna == 0.0 ) _rmna = false;
    else if( rmna == 1.0 ) _rmna = true;
    else throw new IllegalArgumentException("Expected a 0/NAs propagate or 1/NAs ignored");
    return new ValNum(_rmna ? new NaRmRedOp(this).doAll(_fr)._d : new RedOp(this).doAll(_fr)._d);
  }
  /** Override to express a basic math primitive */
  abstract double op( double l, double r );

  private static class RedOp extends MRTask<RedOp> {
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

  private class NaRmRedOp extends MRTask<NaRmRedOp> {
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
  @Override ValNum apply( Env env, Env.StackHelp stk, AST asts[] ) {
    double sum = super.apply(env,stk,asts).getNum();
    long cnt = _fr.numCols()*_fr.numRows();
    if( _rmna )
      for( Vec vec : _fr.vecs() )
        cnt -= vec.naCnt();
    return new ValNum(sum/cnt);
  }
}
