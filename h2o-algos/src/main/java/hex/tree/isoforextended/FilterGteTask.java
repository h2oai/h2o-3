package hex.tree.isoforextended;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.NewChunk;
import water.fvec.Vec;

/**
 * Filter given frame by value. Given frame will be filtered by values in given vector.
 * 
 * @author Adam Valenta
 */
public class FilterGteTask extends MRTask<FilterGteTask> {

    private double value;
    private Vec vec;

    /**
     * In the output will be all rows from frame where vec.at(rownumber) >= value
     * @param vec Vector used for filtering. Length of vector must be the same as number of rows in given Frame
     * @param value value to compare
     */
    public FilterGteTask(Vec vec, double value) {
        this.vec = vec;
        this.value = value;
    }

    @Override
    public void map(Chunk[] cs, NewChunk[] ncs) {
        Vec.Reader vecReader = vec.new Reader();
        for (int row = 0; row < cs[0]._len; row++) {
            double num = vecReader.at(cs[0].start() + row);
            if (num >= value) {
                for (int column = 0; column < cs.length; column++) {
                    ncs[column].addNum(cs[column].atd(row));
                }
            }
        }
    }
}
