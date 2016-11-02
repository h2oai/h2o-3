package water.rapids.ast.prims.advmath;

import water.Key;
import water.MRTask;
import water.fvec.*;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;
import water.rapids.vals.ValNum;
import water.rapids.ast.AstFunction;
import water.util.ArrayUtils;

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
    double xmean = 0;
    double ymean = 0;
    double xvar = 0;
    double yvar = 0;
    double xsd;
    double ysd;
    double ncols = fry.numCols();
    double NACount = 0;
    double xval;
    double yval;
    double ss = 0;
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
    xsd = Math.sqrt(xvar / (ncols - 1 - NACount)); //Sample Standard Deviation
    ysd = Math.sqrt(yvar / (ncols - 1 - NACount)); //Sample Standard Deviation
    double denom = xsd * ysd; //sd(x) * sd(y)

    if (NACount != 0) {
      if (mode.equals(Mode.AllObs)) throw new IllegalArgumentException("Mode is 'all.obs' but NAs are present");
      if (mode.equals(Mode.Everything)) return new ValNum(Double.NaN);
    }

    return new ValNum((ss / (ncols - NACount - 1)) / denom); //Pearson's Correlation Coefficient
  }

  // Matrix correlation.  Compute correlation between all columns from each Frame
  // against each other.  Return a matrix of correlations which is frx.numCols
  // wide and fry.numCols tall.
  private Val array(Frame frx, Frame fry, Mode mode) {
    Vec[] vecxs = frx.vecs();
    int ncolx = vecxs.length;
    Vec[] vecys = fry.vecs();
    int ncoly = vecys.length;

    if (mode.equals(Mode.Everything) || mode.equals(Mode.AllObs)) {

      if (mode.equals(Mode.AllObs)) {
        for (Vec v : vecxs)
          if (v.naCnt() != 0)
            throw new IllegalArgumentException("Mode is 'all.obs' but NAs are present");
      }

      //Set up CoVarTask
      CoVarTask[] cvs = new CoVarTask[ncoly];

      //Get mean of x vecs
      double[] xmeans = new double[ncolx];
      for (int x = 0; x < ncolx; x++) {
        xmeans[x] = vecxs[x].mean();
      }

      //Set up double arrays to capture sd(x), sd(y) and sd(x) * sd(y)
      double[] sigmay = new double[ncoly];
      double[] sigmax = new double[ncolx];
      double[][] denom = new double[ncoly][ncolx];

      // Launch tasks; each does all Xs vs one Y
      for (int y = 0; y < ncoly; y++) {
        //Get covariance between x and y
        cvs[y] = new CoVarTask(vecys[y].mean(), xmeans).dfork(new Frame(vecys[y]).add(frx));
        //Get sigma of y vecs
        sigmay[y] = vecys[y].sigma();
      }

      //Get sigma of x vecs
      for (int x = 0; x < ncolx; x++) {
        sigmax[x] = vecxs[x].sigma();
      }

      //Denominator for correlation calculation is sigma_y * sigma_x (All x sigmas vs one Y)
      for (int y = 0; y < ncoly; y++) {
        for (int x = 0; x < ncolx; x++) {
          denom[y][x] = sigmay[y] * sigmax[x];
        }
      }

      // 1-col returns scalar
      if (ncolx == 1 && ncoly == 1) {
        return new ValNum((cvs[0].getResult()._covs[0] / (fry.numRows() - 1)) / denom[0][0]);
      }

      //Gather final result, which is the correlation coefficient per column
      Vec[] res = new Vec[ncoly];
      Key<Vec>[] keys = Vec.VectorGroup.VG_LEN1.addVecs(ncoly);
      for (int y = 0; y < ncoly; y++) {
        res[y] = Vec.makeVec(ArrayUtils.div(ArrayUtils.div(cvs[y].getResult()._covs, (fry.numRows() - 1)), denom[y]), keys[y]);
      }

      return new ValFrame(new Frame(fry._names, res));
    } else { //if (mode.equals(Mode.CompleteObs))

      //Omit NA rows between X and Y.
      //This will help with cov, sigma & mean calculations later as we only want to calculate cov, sigma, & mean
      //for rows with no NAs
      Frame frxy_naomit = new MRTask() {
        private void copyRow(int row, Chunk[] cs, NewChunk[] ncs) {
          for (int i = 0; i < cs.length; ++i) {
            if (cs[i] instanceof CStrChunk) ncs[i].addStr(cs[i], row);
            else if (cs[i] instanceof C16Chunk) ncs[i].addUUID(cs[i], row);
            else if (cs[i].hasFloat()) ncs[i].addNum(cs[i].atd(row));
            else ncs[i].addNum(cs[i].at8(row), 0);
          }
        }

        @Override
        public void map(Chunk[] cs, NewChunk[] ncs) {
          int col;
          for (int row = 0; row < cs[0]._len; ++row) {
            for (col = 0; col < cs.length; ++col)
              if (cs[col].isNA(row)) break;
            if (col == cs.length) copyRow(row, cs, ncs);
          }
        }
      }.doAll(new Frame(frx).add(fry).types(), new Frame(frx).add(fry)).outputFrame(new Frame(frx).add(fry).names(), new Frame(frx).add(fry).domains());

      //Collect new vecs that do not contain NA rows
      Vec[] vecxs_naomit = frxy_naomit.subframe(0, ncolx).vecs();
      int ncolx_naomit = vecxs_naomit.length;
      Vec[] vecys_naomit = frxy_naomit.subframe(ncolx, frxy_naomit.vecs().length).vecs();
      int ncoly_naomit = vecys_naomit.length;

      //Set up CoVarTask
      CoVarTask[] cvs = new CoVarTask[ncoly_naomit];

      //Get mean of X vecs
      double[] xmeans = new double[ncolx_naomit];
      for (int x = 0; x < ncolx_naomit; x++) {
        xmeans[x] = vecxs_naomit[x].mean();
      }

      //Set up double arrays to capture sd(x), sd(y) and sd(x) * sd(y)
      double[] sigmay = new double[ncoly_naomit];
      double[] sigmax = new double[ncolx_naomit];
      double[][] denom = new double[ncoly_naomit][ncolx_naomit];

      // Launch tasks; each does all Xs vs one Y
      for (int y = 0; y < ncoly_naomit; y++) {
        //Get covariance between x and y
        cvs[y] = new CoVarTask(vecys_naomit[y].mean(), xmeans).dfork(new Frame(vecys_naomit[y]).add(frxy_naomit.subframe(0, ncolx)));
        //Get sigma of y vecs
        sigmay[y] = vecys_naomit[y].sigma();
      }

      //Get sigma of x vecs
      for (int x = 0; x < ncolx_naomit; x++) {
        sigmax[x] = vecxs_naomit[x].sigma();
      }

      //Denominator for correlation calculation is sigma_y * sigma_x (All x sigmas vs one Y)
      for (int y = 0; y < ncoly_naomit; y++) {
        for (int x = 0; x < ncolx_naomit; x++) {
          denom[y][x] = sigmay[y] * sigmax[x];
        }
      }

      // 1-col returns scalar
      if (ncolx_naomit == 1 && ncoly_naomit == 1) {
        return new ValNum((cvs[0].getResult()._covs[0] / (frxy_naomit.numRows() - 1)) / denom[0][0]);
      }

      //Gather final result, which is the correlation coefficient per column
      Vec[] res = new Vec[ncoly_naomit];
      Key<Vec>[] keys = Vec.VectorGroup.VG_LEN1.addVecs(ncoly_naomit);
      for (int y = 0; y < ncoly_naomit; y++) {
        res[y] = Vec.makeVec(ArrayUtils.div(ArrayUtils.div(cvs[y].getResult()._covs, (frxy_naomit.numRows() - 1)), denom[y]), keys[y]);
      }

      return new ValFrame(new Frame(frxy_naomit.subframe(ncolx, frxy_naomit.vecs().length)._names, res));
    }
  }

  private static class CoVarTask extends MRTask<CoVarTask> {
    double[] _covs;
    final double _xmeans[], _ymean;

    CoVarTask(double ymean, double[] xmeans) {
      _ymean = ymean;
      _xmeans = xmeans;
    }

    @Override
    public void map(Chunk cs[]) {
      final int ncolsx = cs.length - 1;
      final Chunk cy = cs[0];
      final int len = cy._len;
      _covs = new double[ncolsx];
      double sum;
      for (int x = 0; x < ncolsx; x++) {
        sum = 0;
        final Chunk cx = cs[x + 1];
        final double xmean = _xmeans[x];
        for (int row = 0; row < len; row++)
          sum += (cx.atd(row) - xmean) * (cy.atd(row) - _ymean);
        _covs[x] = sum;
      }
    }

    @Override
    public void reduce(CoVarTask cvt) {
      ArrayUtils.add(_covs, cvt._covs);
    }
  }
}