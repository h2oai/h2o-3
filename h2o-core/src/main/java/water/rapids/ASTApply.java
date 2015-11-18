package water.rapids;

import water.H2O;
import water.fvec.*;
import water.MRTask;

/** Apply a Function to a frame
 *  Typically, column-by-column, produces a 1-row frame as a result
 */
class ASTApply extends ASTPrim {
  @Override
  public String[] args() { return new String[]{"ary", "margin", "fun"}; }
  @Override int nargs() { return 1+3; } // (apply frame 1/2 fun) 
  @Override public String str() { return "apply"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr     = stk.track(asts[1].exec(env)).getFrame();
    double margin= stk.track(asts[2].exec(env)).getNum();
    AST fun      = stk.track(asts[3].exec(env)).getFun();

    int nargs = fun.nargs();
    if( nargs != -1 && nargs != 2 )
      throw new IllegalArgumentException("Incorrect number of arguments; '"+fun+"' expects "+nargs+" but was passed "+2);

    switch( (int)margin ) {
    case 1:  return rowwise(env,    fr,fun);
    case 2:  return colwise(env,stk,fr,fun); 
    default: throw new IllegalArgumentException("Only row-wise (margin 1) or col-wise (margin 2) allowed");
    }
  }
   
  // --------------------------------------------------------------------------
  private Val colwise( Env env, Env.StackHelp stk, Frame fr, AST fun ) {
    // Break each column into it's own Frame, then execute the function passing
    // the 1 argument.  All columns are independent, and this loop should be
    // parallized over each column.
    Vec vecs[] = fr.vecs();
    Val vals[] = new Val[vecs.length];
    AST[] asts = new AST[]{fun,null};
    for( int i=0; i<vecs.length; i++ ) {
      asts[1] = new ASTFrame(new Frame(new String[]{fr._names[i]}, new Vec[]{vecs[i]}));
      try (Env.StackHelp stk_inner = env.stk()) {
          vals[i] = fun.apply(env,stk_inner,asts);
        }
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
    case Val.NUMS:
      for( int i=0; i<vecs.length; i++ )
        ovecs[i] = Vec.makeCon(vals[i].getNums()[0],1L);
      break;
    case Val.STRS:
      throw H2O.unimpl();
    case Val.FUN:  throw water.H2O.unimpl();
    case Val.STR:  throw water.H2O.unimpl();
    default:       throw water.H2O.unimpl();
    }
    return new ValFrame(new Frame(fr._names,ovecs));
  }

  // --------------------------------------------------------------------------
  // Break each row into it's own Row, then execute the function passing the
  // 1 argument.  All rows are independent, and run in parallel
  private Val rowwise( Env env, Frame fr, final AST fun ) {
    final String[] names = fr._names;

    final ASTFun scope = env._scope;  // Current execution scope; needed to lookup variables

    // do a single row of the frame to determine the size of the output.
    double[] ds = new double[fr.numCols()];
    for(int col=0;col<fr.numCols();++col)
      ds[col] = fr.vec(col).at(0);
    int noutputs = fun.apply(env,env.stk(),new AST[]{fun,new ASTRow(ds,fr.names())}).getRow().length;

    Frame res = new MRTask() {
        @Override public void map( Chunk chks[], NewChunk[] nc ) {
          double ds[] = new double[chks.length]; // Working row
          AST[] asts = new AST[]{fun,new ASTRow(ds,names)}; // Arguments to be called; they are reused endlessly
          Session ses = new Session();                      // Session, again reused endlessly
          Env env = new Env(ses);
          env._scope = scope;                               // For proper namespace lookup
          for( int row=0; row<chks[0]._len; row++ ) {
            for( int col=0; col<chks.length; col++ ) // Fill the row
              ds[col] = chks[col].atd(row);
            try (Env.StackHelp stk_inner = env.stk()) {
              double[] valRow = fun.apply(env,stk_inner,asts).getRow(); // Make the call per-row
              for( int newCol=0;newCol<nc.length;++newCol)
                nc[newCol].addNum(valRow[newCol]);
              }
          }
          ses.end(null);        // Mostly for the sanity checks
        }
      }.doAll(noutputs, Vec.T_NUM, fr).outputFrame();
    return new ValFrame(res);
  }
}


/** Evaluate any number of expressions, returning the last one */
class ASTComma extends ASTPrim {
  @Override
  public String[] args() { return new String[]{"..."}; }
  @Override int nargs() { return -1; } // variable args
  @Override public String str() { return ","; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Val val = new ValNum(0);
    for( int i=1; i<asts.length; i++ )
      val = stk.track(asts[i].exec(env)); // Evaluate all expressions for side-effects
    return val;                           // Return the last one
  }
}
