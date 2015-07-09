package water.currents;

import water.H2O;
import water.fvec.Frame;
import water.fvec.Vec;

/** Variance between columns of a frame */
class ASTVariance extends ASTPrim {
  private enum Mode { Everything, AllObs, CompleteObs }
  @Override int nargs() { return 1+3; /* (var X Y use) */}
  @Override public String str() { return "var"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame frx = stk.track(asts[1].exec(env)).getFrame();
    Frame fry = stk.track(asts[2].exec(env)).getFrame();
    if( frx.numRows() != fry.numRows() )
      throw new IllegalArgumentException("Frames must have the same number of rows, found "+frx.numRows()+" and "+fry.numRows());
    if( frx.numCols() != fry.numCols() )
      throw new IllegalArgumentException("Frames must have the same number of columns, found "+frx.numCols()+" and "+fry.numCols());

    String use = stk.track(asts[3].exec(env)).getStr();
    Mode mode;
    switch( use ) {
    case "everything":            mode = Mode.Everything; break;
    case "all.obs":               mode = Mode.AllObs; break;
    case "complete.obs":          mode = Mode.CompleteObs; break;
    default: throw new IllegalArgumentException("unknown use mode, found: "+use);
    }

    return frx.numRows() == 1 ? scalar(frx,fry,mode) : array(frx,fry,mode);
  }

  // Scalar covariance for 1 row
  private ValNum scalar( Frame frx, Frame fry, Mode mode ) {
    Vec vecxs[] = frx.vecs();
    Vec vecys[] = fry.vecs();
    double xmean=0, ymean=0, ncols = frx.numCols();
    for( Vec v : vecxs ) xmean += v.at(0);
    for( Vec v : vecys ) ymean += v.at(0);
    xmean /= ncols; ymean /= ncols;
   
    double ss=0;
    for( int r = 0; r < ncols; ++r )
      ss += (vecxs[r].at(0) - xmean) * (vecys[r].at(0) - ymean);
    if( mode.equals(Mode.AllObs) ) throw new IllegalArgumentException("Mode is 'all.obs' but NAs are present");
    return new ValNum(ss/(ncols-1));
  }

  private ValNum array( Frame frx, Frame fry, Mode mode ) {
    throw H2O.unimpl();
  }
}
