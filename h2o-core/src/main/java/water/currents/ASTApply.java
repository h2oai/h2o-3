package water.currents;

import java.util.ArrayList;
import water.util.SB;
import water.fvec.Frame;
import water.fvec.Vec;
import water.Key;

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
    case 1:  return rowwise(env,fr,fun); 
    case 2:  return colwise(env,fr,fun); 
    default: throw new IllegalArgumentException("Only row-wise (margin 1) or col-wise (margin 2) allowed");
    }
  }
   
  private Val rowwise( Env env, Frame fr, AST fun ) { throw water.H2O.unimpl(); }
  private Val colwise( Env env, Frame fr, AST fun ) {
    
    // Break each column into it's own Frame, then execute the function passing
    // the 1 argument.
    Vec vecs[] = fr.vecs();
    Val vals[] = new Val[vecs.length];
    for( int i=0; i<vecs.length; i++ ) {
      Frame f1 = new Frame(new String[]{fr._names[i]}, new Vec[]{vecs[i]});
      vals[i] = new ASTExec(fun,new ASTFrame(f1)).exec(env);
    }
    
    // All the resulting Vals must be the same scalar type (and if ValFrames,
    // the columns must be the same count and type).  Build a Frame result with
    // 1 row column per applied function result (per column), and as many rows
    // as there are columns in the returned Frames.
    Val v0 = vals[0];
    double ds[] = new double[vecs.length];
    for( int i=0; i<vecs.length; i++ ) {
      Val v = vals[i];
      if( v0.type() != v.type() )
        throw new IllegalArgumentException("Apply of column "+fr._names[0]+" returned "+v0.getClass()+", and column "+fr._names[i]+" returned "+v.getClass());
      switch( v0.type() ) {
      case Val.FRM:  throw water.H2O.unimpl();
      case Val.FUN:  throw water.H2O.unimpl();
      case Val.STR:  throw water.H2O.unimpl();
      case Val.NUM:  throw water.H2O.unimpl();
      default:       throw water.H2O.unimpl();
      }
    }
    Key<Vec> key = Vec.VectorGroup.VG_LEN1.addVecs(1)[0];
    Vec vec = Vec.makeVec(ds,key);
    throw water.H2O.unimpl(); 
  }
}

