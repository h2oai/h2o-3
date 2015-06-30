package water.currents;

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
    // the 1 argument.  All columns are independent, and this loop should be
    // parallized over each column.
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
    Vec ovecs[] = new Vec[vecs.length];
    switch( v0.type() ) {
    case Val.NUM:
      for( int i=0; i<vecs.length; i++ )  
        ovecs[i] = Vec.makeCon(vals[i].getNum(),1L); // Since the zero column is a number, all must be numbers
      break;
    case Val.FRM:
      long nrows = v0.getFrame().numRows();
      for( int i=0; i<vecs.length; i++ ) {
        Frame res = vals[i].getFrame(); // Since the zero column is a frame, all must be frames
        if( res.numCols() != 1 ) throw new IllegalArgumentException("apply result Frames must have one column, found "+res.numCols()+" cols");
        if( res.numRows() != nrows ) throw new IllegalArgumentException("apply result Frames must have all the same rows, found "+nrows+" rows and "+res.numRows());
        ovecs[i] = res.vec(0);
      }
      break;
    case Val.FUN:  throw water.H2O.unimpl();
    case Val.STR:  throw water.H2O.unimpl();
    default:       throw water.H2O.unimpl();
    }
    return new ValFrame(new Frame(fr._names,ovecs));
  }
}

/** Evaluate any number of expressions, returning the last one */
class ASTComma extends ASTPrim {
  @Override int nargs() { return -1; } // variable args
  @Override public String str() { return ","; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Val val = new ValNum(0);
    for( int i=1; i<asts.length; i++ )
      val = stk.track(asts[i].exec(env)); // Evaluate all expressions for side-effects
    return val;                           // Return the last one
  }
}
