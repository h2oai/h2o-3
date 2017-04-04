package water.rapids.ast.prims.search;

import water.H2O;
import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.ast.AstBuiltin;
import water.fvec.Vec;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;
import water.rapids.vals.ValRow;

public abstract class AstWhichFunc extends AstBuiltin<AstWhichFunc> {
    @Override
    public String[] args() {
        return new String[]{"frame", "na_rm", "axis"};
    }

    @Override
    public int nargs() {
        return 1 + 1;
    }

    @Override
    public String str() {
        throw H2O.unimpl();
    }

    public abstract double op(Vec l); //Operation to perform in colWiseWhichVal() -> Vec.max() or Vec.min().

    public abstract String searchVal(); //String indicating what we are searching for across rows in rowWiseWhichVal() -> max or min.

    public abstract double init();

    @Override
    public Val apply(Env env, Env.StackHelp stk, AstRoot[] asts) {
        Val val1 = asts[1].exec(env);
        if (val1 instanceof ValFrame) {
            Frame fr = stk.track(val1).getFrame();
            boolean na_rm = asts[2].exec(env).getNum() == 1;
            boolean axis = asts.length == 4 && (asts[3].exec(env).getNum() == 1);
            return axis ? rowwiseWhichVal(fr, na_rm) : colwiseWhichVal(fr, na_rm);
        }
        else if (val1 instanceof ValRow) {
            // This may be called from AstApply when doing per-row computations.
            double[] row = val1.getRow();
            boolean na_rm = asts[2].exec(env).getNum() == 1;
            double val = Double.NEGATIVE_INFINITY;
            double valIndex = 0;
            if(searchVal() == "max") { //Looking for the max?
                for (int i = 0; i < row.length; i++) {
                    if (Double.isNaN(row[i])) {
                        if (!na_rm)
                            return new ValRow(new double[]{Double.NaN}, null);
                    } else {
                        if (row[i] > val) {
                            val = row[i];
                            valIndex = i;
                        }
                    }
                }
            }else if(searchVal() == "min"){ //Looking for the min?
                for (int i = 0; i < row.length; i++) {
                    if (Double.isNaN(row[i])) {
                        if (!na_rm)
                            return new ValRow(new double[]{Double.NaN}, null);
                    } else {
                        if (row[i] < val) {
                            val = row[i];
                            valIndex = i;
                        }
                    }
                }
            }
            else{
                throw new IllegalArgumentException("Incorrect argument: expected to search for max() or min(), received " + searchVal());
            }
            return new ValRow(new double[]{valIndex}, null);
        } else
            throw new IllegalArgumentException("Incorrect argument: expected a frame or a row, received " + val1.getClass());
    }


    /**
     * Compute row-wise, and return a frame consisting of a single Vec of value indexes in each row.
     */
    private ValFrame rowwiseWhichVal(Frame fr, final boolean na_rm) {
        String[] newnames = {"which." + searchVal()};
        Key<Frame> newkey = Key.make();

        // Determine how many columns of different types we have
        int n_numeric = 0, n_time = 0;
        for (Vec vec : fr.vecs()) {
            if (vec.isNumeric()) n_numeric++;
            if (vec.isTime()) n_time++;
        }
        // Compute the type of the resulting column: if all columns are TIME then the result is also time; otherwise
        // if at least one column is numeric then the result is also numeric.
        byte resType = n_numeric > 0 ? Vec.T_NUM : Vec.T_TIME;

        // Construct the frame over which the val index should be computed
        Frame compFrame = new Frame();
        for (int i = 0; i < fr.numCols(); i++) {
            Vec vec = fr.vec(i);
            if (n_numeric > 0? vec.isNumeric() : vec.isTime())
                compFrame.add(fr.name(i), vec);
        }
        Vec anyvec = compFrame.anyVec();

        // Take into account certain corner cases
        if (anyvec == null) {
            Frame res = new Frame(newkey);
            anyvec = fr.anyVec();
            if (anyvec != null) {
                // All columns in the original frame are non-numeric -> return a vec of NAs
                res.add("which." + searchVal(), anyvec.makeCon(Double.NaN));
            } // else the original frame is empty, in which case we return an empty frame too
            return new ValFrame(res);
        }
        if (!na_rm && n_numeric < fr.numCols() && n_time < fr.numCols()) {
            // If some of the columns are non-numeric and na_rm==false, then the result is a vec of NAs
            Frame res = new Frame(newkey, newnames, new Vec[]{anyvec.makeCon(Double.NaN)});
            return new ValFrame(res);
        }

        // Compute over all rows
        final int numCols = compFrame.numCols();
        Frame res = new MRTask() {
            @Override
            public void map(Chunk[] cs, NewChunk nc) {
                for (int i = 0; i < cs[0]._len; i++) {
                    int numNaColumns = 0;
                    double value = Double.NEGATIVE_INFINITY;
                    int valueIndex = 0;
                    if (searchVal() == "max") { //Looking for the max?
                        for (int j = 0; j < numCols; j++) {
                            double val = cs[j].atd(i);
                            if (Double.isNaN(val)) {
                                numNaColumns++;
                            } else if (val > value) { //Return the first occurrence of the val
                                value = val;
                                valueIndex = j;
                            }
                        }
                    }else if(searchVal()=="min"){ //Looking for the min?
                        for (int j = 0; j < numCols; j++) {
                            double val = cs[j].atd(i);
                            if (Double.isNaN(val)) {
                                numNaColumns++;
                            }
                            else if(val <  value) { //Return the first occurrence of the min index
                                value = val;
                                valueIndex = j;
                            }
                        }
                    }else{
                        throw new IllegalArgumentException("Incorrect argument: expected to search for max() or min(), received " + searchVal());
                    }
                    if (na_rm ? numNaColumns < numCols : numNaColumns == 0)
                        nc.addNum(valueIndex);
                    else
                        nc.addNum(Double.NaN);
                }
            }
        }.doAll(1, resType, compFrame)
                .outputFrame(newkey, newnames, null);

        // Return the result
        return new ValFrame(res);
    }


    /**
     * Compute column-wise (i.e.value index of each column), and return a frame having a single row.
     */
    private ValFrame colwiseWhichVal(Frame fr, final boolean na_rm) {
        Frame res = new Frame();

        Vec vec1 = Vec.makeCon(null, 0);
        assert vec1.length() == 1;

        for (int i = 0; i < fr.numCols(); i++) {
            Vec v = fr.vec(i);
            double searchValue = op(v);
            boolean valid = (v.isNumeric() || v.isTime() || v.isBinary()) && v.length() > 0 && (na_rm || v.naCnt() == 0);
            FindIndexCol findIndexCol = new FindIndexCol(searchValue).doAll(new byte[]{Vec.T_NUM}, v);
            Vec newvec = vec1.makeCon(valid ? findIndexCol._valIndex : Double.NaN, v.isTime()? Vec.T_TIME : Vec.T_NUM);
            res.add(fr.name(i), newvec);
        }

        vec1.remove();
        return new ValFrame(res);
    }

    private static class FindIndexCol extends MRTask<AstWhichFunc.FindIndexCol>{
        double _val;
        double _valIndex;


        FindIndexCol(double val) {
            _val = val;
            _valIndex = Double.POSITIVE_INFINITY;
        }

        @Override
        public void map(Chunk c, NewChunk nc) {
            long start = c.start();
            for (int i = 0; i < c._len; ++i) {
                if (c.atd(i) == _val) {
                    _valIndex = start + i;
                    break;
                }
            }
        }

        @Override
        public void reduce(AstWhichFunc.FindIndexCol mic) {
            _valIndex = Math.min(_valIndex, mic._valIndex); //Return the first occurrence of the val index
        }
    }
}
