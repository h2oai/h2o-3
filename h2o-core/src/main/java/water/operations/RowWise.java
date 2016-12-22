package water.operations;

import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.rapids.vals.ValFrame;

public class RowWise {
    /**
     * Compute Frame means by rows, and return a frame consisting of a single Vec of means in each row.
     */
    public static ValFrame mean(Frame fr, final boolean na_rm) {
        String[] newnames = {"mean"};
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

        // Construct the frame over which the mean should be computed
        Frame compFrame = new Frame();
        for (int i = 0; i < fr.numCols(); i++) {
            Vec vec = fr.vec(i);
            if (n_numeric > 0 ? vec.isNumeric() : vec.isTime())
                compFrame.add(fr.name(i), vec);
        }
        Vec anyvec = compFrame.anyVec();

        // Take into account certain corner cases
        if (anyvec == null) {
            Frame res = new Frame(newkey);
            anyvec = fr.anyVec();
            if (anyvec != null) {
                // All columns in the original frame are non-numeric -> return a vec of NAs
                res.add("mean", anyvec.makeCon(Double.NaN));
            } // else the original frame is empty, in which case we return an empty frame too
            return new ValFrame(res);
        }
        if (!na_rm && n_numeric < fr.numCols() && n_time < fr.numCols()) {
            // If some of the columns are non-numeric and na_rm==false, then the result is a vec of NAs
            Frame res = new Frame(newkey, newnames, new Vec[]{anyvec.makeCon(Double.NaN)});
            return new ValFrame(res);
        }

        // Compute the mean over all rows
        final int numCols = compFrame.numCols();
        Frame res = new MRTask() {
            @Override
            public void map(Chunk[] cs, NewChunk nc) {
                for (int i = 0; i < cs[0]._len; i++) {
                    double d = 0;
                    int numNaColumns = 0;
                    for (int j = 0; j < numCols; j++) {
                        double val = cs[j].atd(i);
                        if (Double.isNaN(val))
                            numNaColumns++;
                        else
                            d += val;
                    }
                    if (na_rm ? numNaColumns < numCols : numNaColumns == 0)
                        nc.addNum(d / (numCols - numNaColumns));
                    else
                        nc.addNum(Double.NaN);
                }
            }
        }.doAll(1, resType, compFrame)
                .outputFrame(newkey, newnames, null);

        // Return the result
        return new ValFrame(res);
    }
}
