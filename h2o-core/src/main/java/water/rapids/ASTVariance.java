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
  public String[] args() { return new String[]{"ary", "x","y","use", "symmetric"}; }
  private enum Mode { Everything, AllObs, CompleteObs }
  @Override int nargs() { return 1+4; /* (var X Y use symmetric) */}
  @Override public String str() { return "var"; }
  @Override
  public Val apply(Env env, Env.StackHelp stk, AST asts[]) {
    Frame frx = stk.track(asts[1].exec(env)).getFrame();
    Frame fry = stk.track(asts[2].exec(env)).getFrame();
    if( frx.numRows() != fry.numRows() )
      throw new IllegalArgumentException("Frames must have the same number of rows, found "+frx.numRows()+" and "+fry.numRows());

    String use = stk.track(asts[3].exec(env)).getStr();
    boolean symmetric = asts[4].exec(env).getNum()==1;
    Mode mode;
    switch( use ) {
    case "everything":            mode = Mode.Everything; break;
    case "all.obs":               mode = Mode.AllObs; break;
    case "complete.obs":          mode = Mode.CompleteObs; break;
    default: throw new IllegalArgumentException("unknown use mode: "+use);
    }

    return fry.numRows() == 1 ? scalar(frx,fry,mode) : array(frx,fry,mode,symmetric);
  }

  // Scalar covariance for 1 row
  private ValNum scalar( Frame frx, Frame fry, Mode mode) {
    if( frx.numCols() != fry.numCols()) 
      throw new IllegalArgumentException("Single rows must have the same number of columns, found "+frx.numCols()+" and "+fry.numCols());
    Vec vecxs[] = frx.vecs();
    Vec vecys[] = fry.vecs();
    double xmean=0, ymean=0, ncols = frx.numCols(), NACount=0, xval, yval, ss=0;
    for( int r = 0; r < ncols; r++) {
      xval = vecxs[r].at(0);
      yval = vecys[r].at(0);
      if (Double.isNaN(xval) || Double.isNaN(yval))
        NACount++;
      else {
        xmean += xval;
        ymean += yval;
      }
    }
    xmean /= (ncols - NACount); ymean /= (ncols - NACount);
   
    if (NACount != 0) {
      if (mode.equals(Mode.AllObs)) throw new IllegalArgumentException("Mode is 'all.obs' but NAs are present");
      if (mode.equals(Mode.Everything)) return new ValNum(Double.NaN);
    }
    
    for( int r = 0; r < ncols; r++ ) {
      xval = vecxs[r].at(0);
      yval = vecys[r].at(0);
      if (!(Double.isNaN(xval) || Double.isNaN(yval))) 
        ss += (vecxs[r].at(0) - xmean) * (vecys[r].at(0) - ymean);
    }
    return new ValNum(ss/(ncols-NACount-1));
  }

  // Matrix covariance.  Compute covariance between all columns from each Frame
  // against each other.  Return a matrix of covariances which is frx.numCols
  // wide and fry.numCols tall.
  private Val array( Frame frx, Frame fry, Mode mode, boolean symmetric) {
    Vec[] vecxs = frx.vecs();
    int ncolx = vecxs.length;
    Vec[] vecys = fry.vecs();
    int ncoly = vecys.length;

    if (mode.equals(Mode.Everything) || mode.equals(Mode.AllObs)) {

      if (mode.equals(Mode.AllObs)) {
        for (Vec v : vecxs)
          if (v.naCnt() != 0)
            throw new IllegalArgumentException("Mode is 'all.obs' but NAs are present");
        if (!symmetric)
          for (Vec v : vecys)
            if (v.naCnt() != 0)
              throw new IllegalArgumentException("Mode is 'all.obs' but NAs are present");
      }
      CoVarTaskEverything[] cvs = new CoVarTaskEverything[ncoly];

      double[] xmeans = new double[ncolx];
      for (int x = 0; x < ncoly; x++)
        xmeans[x] = vecxs[x].mean();

      if (symmetric) {
        //1-col returns scalar
        if (ncoly == 1)
          return new ValNum(vecys[0].naCnt() == 0 ? vecys[0].sigma() * vecys[0].sigma() : Double.NaN);

        int[] idx = new int[ncoly];
        for (int y = 1; y < ncoly; y++) idx[y] = y;
        int[] first_index = new int[]{0};
        //compute covariances between column_i and column_i+1, column_i+2, ...
        Frame reduced_fr;
        for (int y = 0; y < ncoly-1; y++) {
          idx = ArrayUtils.removeIds(idx, first_index);
          reduced_fr = new Frame(frx.vecs(idx));
          cvs[y] = new CoVarTaskEverything(vecys[y].mean(), xmeans).dfork(new Frame(vecys[y]).add(reduced_fr));
        }

        double[][] res_array = new double[ncoly][ncoly];

        //fill in the diagonals (variances) using sigma from rollupstats
        for (int y = 0; y < ncoly; y++)
          res_array[y][y] = vecys[y].naCnt() == 0 ? vecys[y].sigma() * vecys[y].sigma() : Double.NaN;
        

        //arrange the results into the bottom left of res_array. each successive cvs is 1 smaller in length
        for (int y = 0; y < ncoly - 1; y++)
          System.arraycopy(ArrayUtils.div(cvs[y].getResult()._covs, (fry.numRows() - 1)), 0, res_array[y], y + 1, ncoly - y - 1);

        //copy over the bottom left of res_array to its top right
        for (int y = 0; y < ncoly - 1; y++) {
          for (int x = y + 1; x < ncoly; x++) {
            res_array[x][y] = res_array[y][x];
          }
        }
        //set Frame
        Vec[] res = new Vec[ncoly];
        Key<Vec>[] keys = Vec.VectorGroup.VG_LEN1.addVecs(ncoly);
        for (int y = 0; y < ncoly; y++) {
          res[y] = Vec.makeVec(res_array[y], keys[y]);
        }
        return new ValFrame(new Frame(fry._names, res));
      }

      // Launch tasks; each does all Xs vs one Y
      for (int y = 0; y < ncoly; y++)
        cvs[y] = new CoVarTaskEverything(vecys[y].mean(), xmeans).dfork(new Frame(vecys[y]).add(frx));

      // 1-col returns scalar 
      if (ncolx == 1 && ncoly == 1) {
        return new ValNum(cvs[0].getResult()._covs[0] / (fry.numRows() - 1));
      }

      // Gather all the Xs-vs-Y covariance arrays; divide by rows
      Vec[] res = new Vec[ncoly];
      Key<Vec>[] keys = Vec.VectorGroup.VG_LEN1.addVecs(ncoly);
      for (int y = 0; y < ncoly; y++)
        res[y] = Vec.makeVec(ArrayUtils.div(cvs[y].getResult()._covs, (fry.numRows() - 1)), keys[y]);
      
      return new ValFrame(new Frame(fry._names, res));
    }
    
    else { //if (mode.equals(Mode.CompleteObs)) {
      //two-pass algorithm for computation of variance for numerical stability
      
      if (symmetric) {
        if (ncoly == 1)
          return new ValNum(vecys[0].sigma() * vecys[0].sigma());
        
        CoVarTaskCompleteObsMeanSym taskCompleteObsMeanSym = new CoVarTaskCompleteObsMeanSym().doAll(fry);
        long NACount = taskCompleteObsMeanSym._NACount;
        double[] ymeans = ArrayUtils.div(taskCompleteObsMeanSym._ysum, fry.numRows() - NACount);

        // 1 task with all Ys
        CoVarTaskCompleteObsSym cvs = new CoVarTaskCompleteObsSym(ymeans).doAll(new Frame(fry));
        double[][] res_array = new double[ncoly][ncoly];
        
        for (int y = 0; y < ncoly; y++) {
          System.arraycopy(ArrayUtils.div(cvs._covs[y], (fry.numRows() - 1 - NACount)), y, res_array[y], y, ncoly - y);
        }

        //copy over the bottom left of res_array to its top right
        for (int y = 0; y < ncoly - 1; y++) {
          for (int x = y + 1; x < ncoly; x++) {
            res_array[x][y] = res_array[y][x];
          }
        }
        //set Frame
        Vec[] res = new Vec[ncoly];
        Key<Vec>[] keys = Vec.VectorGroup.VG_LEN1.addVecs(ncoly);
        for (int y = 0; y < ncoly; y++) {
          res[y] = Vec.makeVec(res_array[y], keys[y]);
        }
        return new ValFrame(new Frame(fry._names, res));
      }
      
      CoVarTaskCompleteObsMean taskCompleteObsMean = new CoVarTaskCompleteObsMean(ncoly, ncolx).doAll(new Frame(fry).add(frx));
      long NACount = taskCompleteObsMean._NACount;
      double[] ymeans = ArrayUtils.div(taskCompleteObsMean._ysum, fry.numRows() - NACount);
      double[] xmeans = ArrayUtils.div(taskCompleteObsMean._xsum, fry.numRows() - NACount);

      // 1 task with all Xs and Ys
      CoVarTaskCompleteObs cvs = new CoVarTaskCompleteObs(ymeans, xmeans).doAll(new Frame(fry).add(frx));

      // 1-col returns scalar 
      if (ncolx == 1 && ncoly == 1) {
        return new ValNum(cvs._covs[0][0] / (fry.numRows() - 1 - NACount));
      }

      // Gather all the Xs-vs-Y covariance arrays; divide by rows
      Vec[] res = new Vec[ncoly];
      Key<Vec>[] keys = Vec.VectorGroup.VG_LEN1.addVecs(ncoly);
      for (int y = 0; y < ncoly; y++)
        res[y] = Vec.makeVec(ArrayUtils.div(cvs._covs[y], (fry.numRows() - 1 - NACount)), keys[y]);

      return new ValFrame(new Frame(fry._names, res));    
    }
  }

  private static class CoVarTaskEverything extends MRTask<CoVarTaskEverything> {
    double[] _covs;
    final double _xmeans[], _ymean;
    CoVarTaskEverything(double ymean, double[] xmeans) { _ymean = ymean; _xmeans = xmeans; }
    @Override public void map( Chunk cs[] ) {
      final int ncolsx = cs.length-1;
      final Chunk cy = cs[0];
      final int len = cy._len;
      _covs = new double[ncolsx];
      double sum;
      for( int x=0; x<ncolsx; x++ ) {
        sum = 0;
        final Chunk cx = cs[x+1];
        final double xmean = _xmeans[x];
        for( int row=0; row<len; row++ ) 
          sum += (cx.atd(row)-xmean)*(cy.atd(row)-_ymean);
        _covs[x] = sum;
      }
    }
    @Override public void reduce( CoVarTaskEverything cvt ) { ArrayUtils.add(_covs,cvt._covs); }
  }

  private static class CoVarTaskCompleteObsMean extends MRTask<CoVarTaskCompleteObsMean> {
    double[] _xsum, _ysum;
    long _NACount;
    int _ncolx, _ncoly;
    CoVarTaskCompleteObsMean(int ncoly, int ncolx) { _ncolx = ncolx; _ncoly = ncoly;}
    @Override public void map( Chunk cs[] ) {
      _xsum = new double[_ncolx];
      _ysum = new double[_ncoly];

      double[] xvals = new double[_ncolx];
      double[] yvals = new double[_ncoly];

      double xval, yval;
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
          ArrayUtils.add(_xsum, xvals);
          ArrayUtils.add(_ysum, yvals);
        }
      }
    }
    @Override public void reduce( CoVarTaskCompleteObsMean cvt ) {
      ArrayUtils.add(_xsum, cvt._xsum);
      ArrayUtils.add(_ysum, cvt._ysum);
      _NACount += cvt._NACount;
    }
  }

  private static class CoVarTaskCompleteObs extends MRTask<CoVarTaskCompleteObs> {
    double[][] _covs;
    final double _xmeans[], _ymeans[];
    CoVarTaskCompleteObs(double[] ymeans, double[] xmeans) { _ymeans = ymeans; _xmeans = xmeans; }
    @Override public void map( Chunk cs[] ) {
      int ncolx = _xmeans.length;
      int ncoly = _ymeans.length;
      double[] xvals = new double[ncolx];
      double[] yvals = new double[ncoly];
      _covs = new double[ncoly][ncolx];
      double[] _covs_y;
      double xval, yval, ymean;
      boolean add;
      int len = cs[0]._len;
      for (int row = 0; row < len; row++) {
        add = true;
        //reset existing arrays to 0 rather than initializing new ones to save on garbage collection
        Arrays.fill(xvals, 0);
        Arrays.fill(yvals, 0);

        for (int y = 0; y < ncoly; y++) {
          final Chunk cy = cs[y];
          yval = cy.atd(row);
          //if any yval along a row is NA, discard the entire row
          if (Double.isNaN(yval)) {
            add = false;
            break;
          }
          yvals[y] = yval;
        }
        if (add) {
          for (int x = 0; x < ncolx; x++) {
            final Chunk cx = cs[x + ncoly];
            xval = cx.atd(row);
            //if any xval along a row is NA, discard the entire row
            if (Double.isNaN(xval)) {
              add = false;
              break;
            }
            xvals[x] = xval;
          }
        }
        //add is true iff row has been traversed and found no NAs among yvals and xvals  
        if (add) {
          for (int y = 0; y < ncoly; y++) {
            _covs_y = _covs[y];
            yval = yvals[y];
            ymean = _ymeans[y];
            for (int x = 0; x < ncolx; x++)
              _covs_y[x] += (xvals[x] - _xmeans[x]) * (yval - ymean);
          }
        }
      }
    }
    @Override public void reduce( CoVarTaskCompleteObs cvt ) {
      ArrayUtils.add(_covs,cvt._covs);
    }
  }
  
  private static class CoVarTaskCompleteObsMeanSym extends MRTask<CoVarTaskCompleteObsMeanSym> {
    double[] _ysum;
    long _NACount;
    @Override public void map( Chunk cs[] ) {
      int ncoly = cs.length;
      _ysum = new double[ncoly];
      
      double[] yvals = new double[ncoly];
      double yval;
      boolean add;
      int len = cs[0]._len;
      for (int row = 0; row < len; row++) {
        add = true;
        Arrays.fill(yvals, 0);
        
        for (int y = 0; y < ncoly; y++) {
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
          ArrayUtils.add(_ysum, yvals);
        }
      }
    }

    @Override public void reduce( CoVarTaskCompleteObsMeanSym cvt ) {
      ArrayUtils.add(_ysum, cvt._ysum);
      _NACount += cvt._NACount;
    }
  }

  private static class CoVarTaskCompleteObsSym extends MRTask<CoVarTaskCompleteObsSym> {
    double[][] _covs;
    final double _ymeans[];
    CoVarTaskCompleteObsSym(double[] ymeans) { _ymeans = ymeans; }
    @Override public void map( Chunk cs[] ) {
      int ncoly = _ymeans.length;
      double[] yvals = new double[ncoly];
      _covs = new double[ncoly][ncoly];
      double[] _covs_y;
      double yval, ymean;
      boolean add;
      int len = cs[0]._len;
      for (int row = 0; row < len; row++) {
        add = true;
        //reset existing arrays to 0 rather than initializing new ones to save on garbage collection
        Arrays.fill(yvals, 0);

        for (int y = 0; y < ncoly; y++) {
          final Chunk cy = cs[y];
          yval = cy.atd(row);
          //if any yval along a row is NA, discard the entire row
          if (Double.isNaN(yval)) {
            add = false;
            break;
          }
          yvals[y] = yval;
        }
        
        //add is true iff row has been traversed and found no NAs among yvals
        if (add) {
          for (int y = 0; y < ncoly; y++) {
            _covs_y = _covs[y];
            yval = yvals[y];
            ymean = _ymeans[y];
            for (int x = y; x < ncoly; x++)
              _covs_y[x] += (yvals[x] - _ymeans[x]) * (yval - ymean);
          }
        }
      }
    }
    @Override public void reduce( CoVarTaskCompleteObsSym cvt ) {
      ArrayUtils.add(_covs,cvt._covs);
    }
  }

  static double getVar(Vec v) {
    return v.naCnt() == 0 ? v.sigma() * v.sigma() : Double.NaN;
  }
}
