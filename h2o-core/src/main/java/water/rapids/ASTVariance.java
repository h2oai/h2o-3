package water.rapids;

import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;

/** Variance between columns of a frame */
class ASTVariance extends ASTPrim {
  @Override
  public String[] args() { return new String[]{"ary", "x","y","use"}; }
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
    if( Double.isNaN(ss) && mode.equals(Mode.AllObs) ) throw new IllegalArgumentException("Mode is 'all.obs' but NAs are present");
    return new ValNum(ss/(ncols-1));
  }

  // Matrix covariance.  Compute covariance between all columns from each Frame
  // against each other.  Return a matrix of covariances which is frx.numCols
  // wide and fry.numCols tall.
  private Val array( Frame frx, Frame fry, Mode mode ) {
    Vec[] vecxs = frx.vecs();  int ncolx = vecxs.length;
    Vec[] vecys = fry.vecs();  int ncoly = vecys.length;
    double[] ymeans = new double[ncoly];
    for( int y=0; y<ncoly; y++ ) // All the Y means
      ymeans[y] = vecys[y].mean();

    // Launch tasks; each does all Ys vs one X
    CoVarTask[] cvts = new CoVarTask[ncolx];
    for( int x=0; x<ncolx; x++ )
      cvts[x] = new CoVarTask(vecxs[x].mean(),ymeans).dfork(new Frame(vecxs[x]).add(fry));
    // Short cut for the 1-row-1-col result: return a scalar
    if( ncolx==1 && ncoly==1 )
      return new ValNum(cvts[0].getResult()._covs[0]/(frx.numRows()-1));

    // Gather all the Ys-vs-X covariance arrays; divide by rows
    Vec[] res = new Vec[ncolx];
    Key<Vec>[] keys = Vec.VectorGroup.VG_LEN1.addVecs(ncolx);
    for( int x=0; x<ncolx; x++ )
      res[x] = Vec.makeVec(ArrayUtils.div(cvts[x].getResult()._covs, (frx.numRows()-1)), keys[x]);

    // CNC - For fun, uncomment this code to scale all values by their
    // respective std-devs, basically standardizing the results.  This gives
    // something similar to a r^2 correlation where 1.0 (max possible value)
    // indicates perfect linearity (maybe someting weaker: perfect equality?),
    // and -1 perfectly anti-linear (90% twist), and zero is remains
    // uncorrelated (with actual covariance, zero is also uncorrelated but
    // non-zero values are scaled by the columns' numeric range).
    //
    //for( int x=0; x<ncolx; x++ ) {
    //  double ds[] = ArrayUtils.div(cvts[x].getResult()._covs, (frx.numRows()-1));
    //  ArrayUtils.div(cvts[x].getResult()._covs, vecxs[x].sigma());
    //  for( int y=0; y<ncoly; y++ )
    //    ds[y] /= vecys[y].sigma();
    //  res[x] = Vec.makeVec(ds, keys[x]);
    //}
    return new ValFrame(new Frame(frx._names,res));
  }

  private static class CoVarTask extends MRTask<CoVarTask> {
    double[] _covs;
    final double _xmean, _ymeans[];
    CoVarTask( double xmean, double[] ymeans ) { _xmean = xmean; _ymeans = ymeans; }
    @Override public void map( Chunk cs[] ) {
      final int ncols = cs.length-1;
      final Chunk cx = cs[0];
      final int len = cx._len;
      _covs = new double[ncols];
      for( int y=0; y<ncols; y++ ) {
        double sum = 0;
        final Chunk cy = cs[y+1];
        final double ymean = _ymeans[y];
        for( int row=0; row<len; row++ ) 
          sum += (cx.atd(row)-_xmean)*(cy.atd(row)-ymean);
        _covs[y] = sum;
      }
    }
    @Override public void reduce( CoVarTask cvt ) { ArrayUtils.add(_covs,cvt._covs); }
  }

  static double getVar(Vec v) {
    double m = v.mean();
    CoVarTask t = new CoVarTask(m,new double[]{m}).doAll(new Frame(v, v));
    return t._covs[0] / (v.length() - 1);
  }
}
