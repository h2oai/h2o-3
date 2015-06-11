package water.currents;

import java.util.ArrayList;
import water.util.SB;
import water.fvec.Frame;
import water.fvec.Vec;

/** Apply a Function to a frame
 *  Typically, column-by-column, produces a 1-row frame as a result
 */
class ASTApply extends ASTPrim {
  @Override int nargs() { return 1+3; } // (apply frame 1/2 fun) 
  @Override public String str() { return "apply"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr     = stk.track(asts[1].exec(env)).getFrame();
    double margin= stk.track(asts[2].exec(env)).getNum();
    AST fun      = stk.track(asts[3].exec(env)).getFun();

    switch( (int)margin ) {
    case 1:  return rowwise(env,stk,fr,fun); 
    case 2:  return colwise(env,stk,fr,fun); 
    default: throw new IllegalArgumentException("Only row-wise (margin 1) or col-wise (margin 2) allowed");
    }
  }
   
  private Val rowwise( Env env, Env.StackHelp stk, Frame fr, AST fun ) { throw water.H2O.unimpl(); }
  private Val colwise( Env env, Env.StackHelp stk, Frame fr, AST fun ) { 
    Vec vecs[] = fr.vecs();
    for( int i=0; i<vecs.length; i++ ) {
      Frame f1 = new Frame(new String[]{fr._names[i]}, new Vec[]{vecs[i]});
      AST[] asts = new AST[]{fun,null/*f1*/};
      Val res = fun.apply(env,stk,asts);
      

      throw water.H2O.unimpl(); 
    }
    throw water.H2O.unimpl(); 
  }
}

