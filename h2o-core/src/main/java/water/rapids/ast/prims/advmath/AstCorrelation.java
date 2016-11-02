package water.rapids.ast.prims.advmath;

import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.vals.ValFrame;
import water.rapids.vals.ValNum;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.util.ArrayUtils;
import java.util.Arrays;

/**
 * Calculate Pearson's Correlation Coefficient between columns of a frame
 * <p/>
 * Formula:
 * Pearson's Correlation Coefficient = Cov(X,Y)/sigma(X) * sigma(Y)
 */
public class AstCorrelation extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary", "x", "y", "use"};
  }

  private enum Mode {Everything, AllObs, CompleteObs}

  @Override
  public int nargs() {
    return 1 + 3; /* (cor X Y use) */
  }

  @Override
  public String str() {
    return "cor";
  }

  @Override
  public Val apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame frx = stk.track(asts[1].exec(env)).getFrame();
    Frame fry = stk.track(asts[2].exec(env)).getFrame();
    if (frx.numRows() != fry.numRows())
      throw new IllegalArgumentException("Frames must have the same number of rows, found " + frx.numRows() + " and " + fry.numRows());

    String use = stk.track(asts[3].exec(env)).getStr();
    Mode mode;
    switch (use) {
      case "everything":
        mode = Mode.Everything;
        break;
      case "all.obs":
        mode = Mode.AllObs;
        break;
      case "complete.obs":
        mode = Mode.CompleteObs;
        break;
      default:
        throw new IllegalArgumentException("unknown use mode: " + use);
    }
    return fry.numRows() == 1 ? scalar(frx, fry, mode) : array(frx, fry, mode);
  }

  // Pearson Correlation for one row, which will return a scalar value.
  private ValNum scalar(Frame frx, Frame fry, Mode mode) {
    if (frx.numCols() != fry.numCols())
      throw new IllegalArgumentException("Single rows must have the same number of columns, found " + frx.numCols() + " and " + fry.numCols());
    Vec vecxs[] = frx.vecs();
    Vec vecys[] = fry.vecs();
    double xmean = 0, ymean = 0, xvar = 0, yvar = 0, xsd = 0, ysd = 0, ncols = frx.numCols(), NACount = 0, xval, yval, ss = 0;
    for (int r = 0; r < ncols; r++) {
      xval = vecxs[r].at(0);
      yval = vecys[r].at(0);
      if (Double.isNaN(xval) || Double.isNaN(yval))
        NACount++;
      else {
        xmean += xval;
        ymean += yval;
      }
    }
    xmean /= (ncols - NACount);
    ymean /= (ncols - NACount);

    for (int r = 0; r < ncols; r++) {
      xval = vecxs[r].at(0);
      yval = vecys[r].at(0);
      if (!(Double.isNaN(xval) || Double.isNaN(yval))) {
        //Compute variance of x and y vars
        xvar += Math.pow((vecxs[r].at(0) - xmean), 2);
        yvar += Math.pow((vecys[r].at(0) - ymean), 2);
        //Compute sum of squares of x and y
        ss += (vecxs[r].at(0) - xmean) * (vecys[r].at(0) - ymean);
      }
    }
    xsd = Math.sqrt(xvar / (frx.numRows())); //Sample Standard Deviation
    ysd = Math.sqrt(yvar / (fry.numRows())); //Sample Standard Deviation
    double cor_denom = xsd * ysd;

    if (NACount != 0) {
      if (mode.equals(Mode.AllObs)) throw new IllegalArgumentException("Mode is 'all.obs' but NAs are present");
      if (mode.equals(Mode.Everything)) return new ValNum(Double.NaN);
    }

    for (int r = 0; r < ncols; r++) {
      xval = vecxs[r].at(0);
      yval = vecys[r].at(0);
      if (!(Double.isNaN(xval) || Double.isNaN(yval)))
        ss += (vecxs[r].at(0) - xmean) * (vecys[r].at(0) - ymean);
    }

    return new ValNum(ss / cor_denom); //Pearson's Correlation Coefficient
  }

  // Correlation Matrix.
  // Compute correlation between all columns from each Frame against each other.
  // Return a matrix of correlations which is frx.numCols wide and fry.numCols tall.
  private Val array(Frame frx, Frame fry, Mode mode) {
    Vec[] vecxs = frx.vecs();
    int ncolx = vecxs.length;
    Vec[] vecys = fry.vecs();
    int ncoly = vecys.length;

    if (mode.equals(Mode.AllObs)) {
      for (Vec v : vecxs)
        if (v.naCnt() != 0)
          throw new IllegalArgumentException("Mode is 'all.obs' but NAs are present");
    }


    CorTaskMean taskMean = new CorTaskMean(ncoly, ncolx, mode.equals(Mode.CompleteObs)?true:false).doAll(new Frame(fry).add(frx));

    long NACount = taskMean._NACount;
    double[] ymeans = ArrayUtils.div(taskMean._ysum, fry.numRows() - NACount);
    double[] xmeans = ArrayUtils.div(taskMean._xsum, fry.numRows() - NACount);

    CorTask[] cvs = new CorTask[ncoly];

    // Launch tasks; each does all Xs vs one Y
    for (int y = 0; y < ymeans.length; y++)
      cvs[y] = new CorTask(ymeans[y], xmeans,true).dfork(new Frame(vecys[y]).add(frx));

    // 1-col returns scalar
    if (ncolx == 1 && ncoly == 1) {
      return new ValNum(cvs[0].getResult()._cors[0] / cvs[0].getResult()._denom[0]);
    }

    // Gather all the Xs-vs-Y covariance arrays; divide by rows
    Vec[] res = new Vec[ncoly];
    Key<Vec>[] keys = Vec.VectorGroup.VG_LEN1.addVecs(ncoly);
    for (int y = 0; y < ncoly; y++)
      res[y] = Vec.makeVec(ArrayUtils.div(cvs[y].getResult()._cors, cvs[y].getResult()._denom), keys[y]);

    return new ValFrame(new Frame(fry._names, res));
  }

  private static class CorTask extends MRTask<CorTask> {
    double[] _cors;
    double[] _denom;
    final double _xmeans[], _ymean;
    boolean _completeObs;

    CorTask(double ymean, double[] xmeans, boolean completeObs) {
      _ymean = ymean;
      _xmeans = xmeans;
      _completeObs = completeObs;
    }

    @Override
    public void map(Chunk cs[]) {
      final int ncolsx = cs.length - 1;
      final Chunk cy = cs[0];
      final int len = cy._len;
      _cors = new double[ncolsx];
      _denom = new double[ncolsx];
      double sum;
      double varx;
      double vary;
      for (int x = 0; x < ncolsx; x++) {
        sum = 0;
        varx = 0;
        vary = 0;
        final Chunk cx = cs[x + 1];
        final double xmean = _xmeans[x];
        for (int row = 0; row < len; row++) {
          if(_completeObs){
            //If mode is "complete.obs", then we omit rows with NA's
            //Checking for NA's and omitting any rows with an NA value.
            //This same check is done for the mean vector (See CorTaskMean)
            if(!(cx.isNA(row)) && !(cy.isNA(row))){
              varx += (cx.atd(row) - xmean) * (cx.atd(row) - xmean); //Compute variance for x
              vary += (cy.atd(row) - _ymean) * (cy.atd(row) - _ymean); //Compute variance for y
              sum += (cx.atd(row) - xmean) * (cy.atd(row) - _ymean); //Compute sum of square
            }
          }
          else {
            varx += (cx.atd(row) - xmean) * (cx.atd(row) - xmean); //Compute variance for x
            vary += (cy.atd(row) - _ymean) * (cy.atd(row) - _ymean); //Compute variance for y
            sum += (cx.atd(row) - xmean) * (cy.atd(row) - _ymean); //Compute sum of square
          }
        }
        _cors[x] = sum;
        _denom[x] = Math.sqrt(varx) * Math.sqrt(vary);
      }
    }

    @Override
    public void reduce(CorTask cvt) {
      ArrayUtils.add(_cors, cvt._cors);
      ArrayUtils.add(_denom, cvt._denom);
    }
  }
  
  private static class CorTaskMean extends MRTask<CorTaskMean> {
    double[] _xsum, _ysum;
    long _NACount;
    int _ncolx, _ncoly;
    boolean _completeObs;

    CorTaskMean(int ncoly, int ncolx, boolean completeObs) {
      _ncolx = ncolx;
      _ncoly = ncoly;
      _completeObs = completeObs;
    }

    @Override
    public void map(Chunk cs[]) {
      _xsum = new double[_ncolx];
      _ysum = new double[_ncoly];

      double[] xvals = new double[_ncolx];
      double[] yvals = new double[_ncoly];

      double xval, yval;
      boolean add;
      int len = cs[0]._len;
      for (int row = 0; row < len; row++) {
        add = true;
        //reset existing arrays to 0. Should save on GC.
        Arrays.fill(xvals, 0);
        Arrays.fill(yvals, 0);

        for (int y = 0; y < _ncoly; y++) {
          final Chunk cy = cs[y];
          yval = cy.atd(row);
          //if any yval along a row is NA and _completeObs is true, discard the entire row
          if (Double.isNaN(yval) && _completeObs) {
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
            //if any yval along a row is NA and _completeObs is true, discard the entire row
            if (Double.isNaN(xval) && _completeObs) {
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

    @Override
    public void reduce(CorTaskMean cvt) {
      ArrayUtils.add(_xsum, cvt._xsum);
      ArrayUtils.add(_ysum, cvt._ysum);
      _NACount += cvt._NACount;
    }
  }
}

