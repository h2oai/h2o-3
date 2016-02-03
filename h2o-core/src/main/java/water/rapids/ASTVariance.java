package water.rapids;

import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import java.util.Arrays;

/** Variance between columns of a frame */
class ASTVariance extends ASTPrim {
  @Override
  public String[] args() { return new String[]{"ary", "x","y","use"}; }
  private enum Mode { Everything, AllObs, CompleteObs, PairwiseCompleteObs }
  @Override int nargs() { return 1+3; /* (var X Y use) */}
  @Override public String str() { return "var"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame frx = stk.track(asts[1].exec(env)).getFrame();
    Frame fry = stk.track(asts[2].exec(env)).getFrame();
    if( frx.numRows() != fry.numRows() )
      throw new IllegalArgumentException("Frames must have the same number of rows, found "+frx.numRows()+" and "+fry.numRows());
    String use = stk.track(asts[3].exec(env)).getStr();
    Mode mode;
    //In R, if the use arg is set, the na.rm arg has no effect (same result whether it is T or F). The na.rm param only 
    // comes into play when no use arg is set. Without a use arg, setting na.rm = T is equivalent to use = "complete.obs",
    // while setting na.rm = F (default) is equivalent to use = "everything". 
    switch( use ) {
      case "everything":            mode = Mode.Everything; break;
      case "all.obs":               mode = Mode.AllObs; break;
      case "complete.obs":          mode = Mode.CompleteObs; break;
      case "pairwise.complete.obs": mode = Mode.PairwiseCompleteObs; break;
      default: throw new IllegalArgumentException("unknown use mode, found: "+use);
    }
    
    return frx.numRows() == 1 ? scalar(frx,fry,mode) : array(frx,fry,mode);
  }

  // Scalar covariance for 1 row
  private ValNum scalar( Frame frx, Frame fry, Mode mode) {
    if( frx.numCols() != fry.numCols())
      throw new IllegalArgumentException("Single rows have the same number of columns, found "+frx.numCols()+" and "+fry.numCols());
    Vec vecxs[] = frx.vecs();
    Vec vecys[] = fry.vecs();
    double xsum=0, ysum=0, NACount=0, ncols = frx.numCols(), xval, yval, ss=0;
    for( int i=0; i< vecxs.length; i++) {
      xval = vecxs[i].at(0);
      yval = vecys[i].at(0);
      if (Double.isNaN(xval) || Double.isNaN(yval))
        NACount++;
      else {
        xsum += xval;
        ysum += yval;
        ss += xval * yval;
      }
    }
    
    if (NACount>0) {
      if (mode.equals(Mode.AllObs)) throw new IllegalArgumentException("Mode is 'all.obs' but NAs are present");
      if (mode.equals(Mode.Everything)) return new ValNum(Double.NaN);
    }
    return new ValNum((ss - xsum * ysum/(ncols - NACount)) / (ncols-1-NACount));
  }

  // Matrix covariance.  Compute covariance between all columns from each Frame
  // against each other.  Return a matrix of covariances which is frx.numCols
  // tall and fry.numCols wide.
  private Val array( Frame frx, Frame fry, Mode mode) {
    Vec[] vecxs = frx.vecs();  int ncolx = vecxs.length;
    Vec[] vecys = fry.vecs();  int ncoly = vecys.length;

    if (mode.equals(Mode.Everything) || mode.equals(Mode.AllObs)) {
      // Launch tasks; each does all Xs vs one Y
      CoVarTaskEverything[] cvts = new CoVarTaskEverything[ncoly];
      for (int y = 0; y < ncoly; y++)
        cvts[y] = new CoVarTaskEverything().dfork(new Frame(vecys[y]).add(frx));
      // Short cut for the 1-row-1-col result: return a scalar
      if (ncolx == 1 && ncoly == 1) {
        CoVarTaskEverything res = cvts[0].getResult();
        if (mode.equals(Mode.AllObs) && Double.isNaN(res._ss[0])) throw new IllegalArgumentException("Mode is 'all.obs' but NAs are present");
        return new ValNum((res._ss[0] - res._xsum[0] * res._ysum[0] / (frx.numRows())) / (frx.numRows() - 1));
      }
      // Gather all the Xs-vs-Y covariance arrays; divide by rows
      Vec[] res = new Vec[ncoly];
      Key<Vec>[] keys = Vec.VectorGroup.VG_LEN1.addVecs(ncoly);
      for (int y = 0; y < ncoly; y++) {
        CoVarTaskEverything cvtx = cvts[y].getResult();
        if (mode.equals(Mode.AllObs))
          for (double ss : cvtx._ss)
            if (Double.isNaN(ss)) throw new IllegalArgumentException("Mode is 'all.obs' but NAs are present");
        res[y] = Vec.makeVec(ArrayUtils.div(ArrayUtils.subtract(cvtx._ss, ArrayUtils.mult(cvtx._xsum,
                ArrayUtils.div(cvtx._ysum, frx.numRows()))), frx.numRows() - 1), keys[y]);
      }

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
      return new ValFrame(new Frame(fry._names, res));
    }
    
    if (mode.equals(Mode.CompleteObs)) {
      CoVarTaskComplete cvs = new CoVarTaskComplete(ncolx, ncoly).doAll(new Frame(fry).add(frx));
      
      if (ncolx == 1 && ncoly == 1)
        return new ValNum((cvs._ss[0][0] - cvs._xsum[0] * cvs._ysum[0] / (frx.numRows() - cvs._NACount)) / (frx.numRows() - cvs._NACount - 1));
      
      //All Xs-and-Ys
      Vec[] res = new Vec[ncoly];
      Key<Vec>[] keys = Vec.VectorGroup.VG_LEN1.addVecs(ncoly);      
      for (int y= 0; y < ncoly; y++) {
        res[y] = Vec.makeVec(ArrayUtils.div(ArrayUtils.subtract(cvs._ss[y], ArrayUtils.mult(cvs._xsum.clone(),
                (cvs._ysum[y] / (frx.numRows() - cvs._NACount)))), (frx.numRows() - 1 - cvs._NACount)), keys[y]);
      }
      return new ValFrame(new Frame(fry._names, res));
    }
    
    if (mode.equals(Mode.PairwiseCompleteObs)) {

      CoVarTaskPairwise[] cvts = new CoVarTaskPairwise[ncoly];
      for (int y = 0; y < ncoly; y++)
        cvts[y] = new CoVarTaskPairwise().dfork(new Frame(vecys[y]).add(frx));
      // Short cut for the 1-row-1-col result: return a scalar
      if (ncolx == 1 && ncoly == 1) {
        CoVarTaskPairwise res = cvts[0].getResult();
        return new ValNum((res._ss[0] - res._xsum[0] * res._ysum[0] / (frx.numRows() - res._NACount[0])) / (frx.numRows() - 1 - res._NACount[0]));
      }
      // Gather all the Xs-vs-Y covariance arrays; divide by rows
      Vec[] res = new Vec[ncoly];
      Key<Vec>[] keys = Vec.VectorGroup.VG_LEN1.addVecs(ncoly);
      for (int y = 0; y < ncoly; y++) {
        CoVarTaskPairwise cvtx = cvts[y].getResult();
        res[y] = Vec.makeVec(ArrayUtils.div(ArrayUtils.subtract(cvtx._ss, ArrayUtils.mult(cvtx._xsum,
                ArrayUtils.div(cvtx._ysum, ArrayUtils.subtract(frx.numRows(), cvtx._NACount.clone())))), ArrayUtils.subtract(frx.numRows() - 1, cvtx._NACount.clone())), keys[y]);
      }
      
      return new ValFrame(new Frame(fry._names, res));
    }
    throw new IllegalArgumentException("unknown use mode, found: "+mode);
  }


  private static class CoVarTaskEverything extends MRTask<CoVarTaskEverything> {
    double[] _ss, _xsum, _ysum;
    CoVarTaskEverything() {}
    @Override public void map( Chunk cs[] ) {
      final int ncolsx = cs.length-1;
      final Chunk cy = cs[0];
      final int len = cy._len;
      _ss = new double[ncolsx];
      _xsum = new double[ncolsx];
      _ysum = new double[ncolsx];
      double xval, yval;
      for( int x=0; x<ncolsx; x++ ) {
        double ss = 0, xsum = 0, ysum = 0;
        final Chunk cx = cs[x+1];
        for( int row=0; row<len; row++ ) {
          xval = cx.atd(row);
          yval = cy.atd(row);
          xsum += xval;
          ysum += yval;
          ss += xval * yval;
        }
        _ss[x] = ss;
        _xsum[x] = xsum;
        _ysum[x] = ysum;
      }
    }
    @Override public void reduce( CoVarTaskEverything cvt ) {
      ArrayUtils.add(_ss,cvt._ss);
      ArrayUtils.add(_xsum, cvt._xsum);
      ArrayUtils.add(_ysum, cvt._ysum);
    }
  }

  private static class CoVarTaskComplete extends MRTask<CoVarTaskComplete> {
    double[][] _ss;
    double[] _xsum, _ysum;
    long _NACount;
    int _ncolx, _ncoly;
    CoVarTaskComplete(int ncolx, int ncoly) { _ncolx = ncolx; _ncoly = ncoly;}
    @Override public void map( Chunk cs[] ) {
      
      _ss = new double[_ncoly][_ncolx];
      _xsum = new double[_ncolx];
      _ysum = new double[_ncoly];

      double[] xvals = new double[_ncolx];
      double[] yvals = new double[_ncoly];

      double xval, yval;
      double[] _ss_y;
      boolean add;
      int len = cs[0]._len;
      for (int row = 0; row < len; row++) {
        add = true;
        //reset existing arrays to 0 rather than initializing new ones to save on garbage collection
        Arrays.fill(xvals, 0);
        Arrays.fill(yvals, 0);
        
        for (int y = 0; y < _ncoly; y++) {
          final Chunk cy = cs[y];
          yval = cy.atd(row);
          //if any yval along a row is NA, discard the entire row
          if (Double.isNaN(yval)) {
            _NACount++;
            add = false;
            break;
          }
          yvals[y] = yval;
        }
        if (add) {
          for (int x = 0; x < _ncolx; x++) {
            final Chunk cx = cs[x + _ncoly];
            xval = cx.atd(row);
            //if any xval along a row is NA, discard the entire row
            if (Double.isNaN(xval)) {
              _NACount++;
              add = false;
              break;
            }
            xvals[x] = xval;
          }
        }
        //add is true iff row has been traversed and found no NAs among yvals and xvals  
        if (add) {
          for (int y=0; y < _ncoly; y++) {
            _ss_y = _ss[y];
            for (int x = 0; x < _ncolx; x++)
              _ss_y[x] += xvals[x] * yvals[y];
          }
          ArrayUtils.add(_xsum, xvals);
          ArrayUtils.add(_ysum, yvals);
        }
      }
    }
    @Override public void reduce( CoVarTaskComplete cvt ) {
      ArrayUtils.add(_ss,cvt._ss);
      ArrayUtils.add(_xsum, cvt._xsum);
      ArrayUtils.add(_ysum, cvt._ysum);
      _NACount += cvt._NACount;
    }
  }

  private static class CoVarTaskPairwise extends MRTask<CoVarTaskPairwise> {
    double[] _ss, _xsum, _ysum;
    long[] _NACount;
    CoVarTaskPairwise() {}
    @Override public void map( Chunk cs[] ) {
      final int ncolsx = cs.length-1;
      final Chunk cy = cs[0];
      final int len = cy._len;
      _ss = new double[ncolsx];
      _xsum = new double[ncolsx];
      _ysum = new double[ncolsx];
      _NACount = new long[ncolsx];
      double xval, yval;
      for( int x=0; x<ncolsx; x++ ) {
        double ss = 0, xsum = 0, ysum = 0;
        long na = 0;
        final Chunk cx = cs[x+1];
        for( int row=0; row<len; row++ ) {
          xval = cx.atd(row);
          yval = cy.atd(row);
          if (Double.isNaN(xval) || Double.isNaN(yval))
            na++;
          else {
            xsum += xval;
            ysum += yval; 
            ss += xval * yval;
          }
        }
        _ss[x] = ss;
        _xsum[x] = xsum;
        _ysum[x] = ysum;
        _NACount[x] = na;
      }
    }
    @Override public void reduce( CoVarTaskPairwise cvt ) { 
      ArrayUtils.add(_ss,cvt._ss);
      ArrayUtils.add(_xsum, cvt._xsum);
      ArrayUtils.add(_ysum, cvt._ysum);
      ArrayUtils.add(_NACount, cvt._NACount);
    }
  }

  static double getVar(Vec v) {
    CoVarTaskEverything res = new CoVarTaskEverything().doAll(new Frame(v, v));
    return (res._ss[0] - res._xsum[0] * res._ysum[0] / (v.length())) / (v.length() - 1);
    
  }
}
