package water.rapids;

import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;

import java.util.Arrays;

/** Calculate Pearson's Correlation Coefficient between columns of a frame
 *
 * Formula:
 *  Pearson's Correlation Coefficient = Cov(X,Y)/sigma(X) * sigma(Y)
 *
 * */
class ASTCorrelation extends ASTPrim {
    @Override
    public String[] args() { return new String[]{"ary", "x","y","use"}; }
    private enum Mode { Everything, AllObs, CompleteObs }
    @Override int nargs() { return 1+3; /* (cor X Y use) */}
    @Override public String str() { return "cor"; }
    @Override
    public Val apply(Env env, Env.StackHelp stk, AST asts[]) {
        Frame frx = stk.track(asts[1].exec(env)).getFrame();
        Frame fry = stk.track(asts[2].exec(env)).getFrame();
        if( frx.numRows() != fry.numRows() )
            throw new IllegalArgumentException("Frames must have the same number of rows, found "+frx.numRows()+" and "+fry.numRows());

        String use = stk.track(asts[3].exec(env)).getStr();
        Mode mode;
        switch( use ) {
            case "everything":            mode = Mode.Everything; break;
            case "all.obs":               mode = Mode.AllObs; break;
            case "complete.obs":          mode = Mode.CompleteObs; break;
            default: throw new IllegalArgumentException("unknown use mode: "+use);
        }

        return fry.numRows() == 1 ? scalar(frx,fry,mode) : array(frx,fry,mode);
    }

    // Pearson Correlation for one row, which will return a scalar value.
    private ValNum scalar( Frame frx, Frame fry, Mode mode) {
        if( frx.numCols() != fry.numCols())
            throw new IllegalArgumentException("Single rows must have the same number of columns, found "+frx.numCols()+" and "+fry.numCols());
        Vec vecxs[] = frx.vecs();
        Vec vecys[] = fry.vecs();
        double xmean=0, ymean=0, xvar=0, yvar=0,xsd=0,ysd=0, ncols = frx.numCols(), NACount=0, xval, yval, ss=0;
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

        for( int r = 0; r < ncols; r++ ) {
            xval = vecxs[r].at(0);
            yval = vecys[r].at(0);
            if (!(Double.isNaN(xval) || Double.isNaN(yval)))
                //Compute variance of x and y vars
                xvar += Math.pow((vecxs[r].at(0) - xmean), 2);
                yvar += Math.pow((vecys[r].at(0) - ymean), 2);
                //Compute sum of squares of x and y
                ss += (vecxs[r].at(0) - xmean) * (vecys[r].at(0) - ymean);
            }
        xsd = Math.sqrt(xvar/(frx.numRows())); //Sample Standard Deviation
        ysd = Math.sqrt(yvar/(fry.numRows())); //Sample Standard Deviation
        double cor_denom = xsd * ysd;

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

        return new ValNum(ss/cor_denom); //Pearson's Correlation Coefficient
    }

    // Correlation Matrix.
    // Compute correlation between all columns from each Frame against each other.
    // Return a matrix of correlations which is frx.numCols wide and fry.numCols tall.
    private Val array( Frame frx, Frame fry, Mode mode) {
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
            CorTaskEverything[] cvs = new CorTaskEverything[ncoly];

            double[] xmeans = new double[ncolx];
            for (int x = 0; x < ncolx; x++) {
                xmeans[x] = vecxs[x].mean();
            }

            // Start up tasks. Does all Xs vs one Y.
            for (int y = 0; y < ncoly; y++)
                cvs[y] = new CorTaskEverything(vecys[y].mean(), xmeans).dfork(new Frame(vecys[y]).add(frx));

            // One col will return a scalar.
            if (ncolx == 1 && ncoly == 1) {
                return new ValNum(cvs[0].getResult()._cors[0]);
            }

            // Gather all the Xs-vs-Y correlation arrays and build out final Frame of correlations.
            Vec[] res = new Vec[ncoly];
            Key<Vec>[] keys = Vec.VectorGroup.VG_LEN1.addVecs(ncoly);
            for (int y = 0; y < ncoly; y++)
                res[y] = Vec.makeVec(cvs[y].getResult()._cors, keys[y]);

            return new ValFrame(new Frame(fry._names, res));

        }
        else {

            CorTaskCompleteObsMean taskCompleteObsMean = new CorTaskCompleteObsMean(ncoly, ncolx).doAll(new Frame(fry).add(frx));
            long NACount = taskCompleteObsMean._NACount;
            double[] ymeans = ArrayUtils.div(taskCompleteObsMean._ysum, fry.numRows() - NACount);
            double[] xmeans = ArrayUtils.div(taskCompleteObsMean._xsum, fry.numRows() - NACount);

            // Start up tasks. Does all Xs vs one Y.
            CorTaskCompleteObs cvs = new CorTaskCompleteObs(ymeans, xmeans).doAll(new Frame(fry).add(frx));

            // One col will return a scalar.
            if (ncolx == 1 && ncoly == 1) {
                return new ValNum(cvs._cors[0][0] / (fry.numRows() - 1 - NACount));
            }

            // Gather all the Xs-vs-Y covariance arrays; divide by rows
            Vec[] res = new Vec[ncoly];
            Key<Vec>[] keys = Vec.VectorGroup.VG_LEN1.addVecs(ncoly);
            for (int y = 0; y < ncoly; y++)
                res[y] = Vec.makeVec(ArrayUtils.div(cvs._cors[y], (fry.numRows() - 1 - NACount)), keys[y]);

            return new ValFrame(new Frame(fry._names, res));
        }
    }

    private static class CorTaskEverything extends MRTask<CorTaskEverything> {
        double[] _cors;
        final double _xmeans[], _ymean;
        CorTaskEverything(double ymean, double[] xmeans) { _ymean = ymean;_xmeans = xmeans; }
        @Override public void map( Chunk cs[] ) {
            final int ncolsx = cs.length-1;
            final Chunk cy = cs[0];
            final int len = cy._len;
            _cors = new double[ncolsx];
            double sum;
            double varx;
            double vary;
            for( int x=0; x<ncolsx; x++ ) {
                sum = 0;
                varx = 0;
                vary = 0;
                final Chunk cx = cs[x+1];
                final double xmean = _xmeans[x];
                for( int row=0; row<len; row++ ) {
                    varx += ((cx.atd(row) - xmean) * (cx.atd(row) - xmean))/(len-1); //Compute variance for x
                    vary += ((cy.atd(row) - _ymean) * (cy.atd(row) - _ymean))/(len-1); //Compute variance for y
                    sum += ((cx.atd(row) - xmean) * (cy.atd(row) - _ymean))/(len-1); //Compute sum of square
                }
                _cors[x] = sum/(Math.sqrt(varx) * Math.sqrt(vary)); //Pearsons correlation coefficient
            }
        }
        @Override public void reduce( CorTaskEverything cvt ) { ArrayUtils.add(_cors,cvt._cors); }
    }
    private static class CorTaskCompleteObsMean extends MRTask<CorTaskCompleteObsMean> {
        double[] _xsum, _ysum;
        long _NACount;
        int _ncolx, _ncoly;
        CorTaskCompleteObsMean(int ncoly, int ncolx) { _ncolx = ncolx; _ncoly = ncoly;}
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
                //reset existing arrays to 0. Will save on GC.
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
        @Override public void reduce( CorTaskCompleteObsMean cvt ) {
            ArrayUtils.add(_xsum, cvt._xsum);
            ArrayUtils.add(_ysum, cvt._ysum);
            _NACount += cvt._NACount;
        }
    }

    private static class CorTaskCompleteObs extends MRTask<CorTaskCompleteObs> {
        double[][] _cors;
        final double _xmeans[], _ymeans[];
        CorTaskCompleteObs(double[] ymeans, double[] xmeans) { _ymeans = ymeans; _xmeans = xmeans; }
        @Override public void map( Chunk cs[] ) {
            int ncolx = _xmeans.length;
            int ncoly = _ymeans.length;
            double[] xvals = new double[ncolx];
            double[] yvals = new double[ncoly];
            _cors = new double[ncoly][ncolx];
            double[] _cors_y;
            double xval, yval, ymean;
            boolean add;
            int len = cs[0]._len;
            for (int row = 0; row < len; row++) {
                add = true;
                //reset existing arrays to 0. Will save on GC.
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
                        _cors_y = _cors[y];
                        yval = yvals[y];
                        ymean = _ymeans[y];
                        for (int x = 0; x < ncolx; x++)
                            _cors_y[x] += ((xvals[x] - _xmeans[x]) * (yval - ymean))/(Math.sqrt(Math.pow(xvals[x] - _xmeans[x],2)) * Math.sqrt(Math.pow(yval - ymean,2)));
                    }
                }
            }
        }
        @Override public void reduce( CorTaskCompleteObs cvt ) {
            ArrayUtils.add(_cors,cvt._cors);
        }
    }
}
