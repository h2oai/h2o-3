package hex.tree.isoforextended;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.NewChunk;
import water.fvec.Vec;

public class FilterLtTask extends MRTask<FilterLtTask> {

    private double value;
    private Vec mul;

    public FilterLtTask(Vec vec, double value) {
        this.mul = vec;
        this.value = value;
    }

    @Override
    public void map(Chunk[] cs, NewChunk[] ncs) {
        for (int row = 0; row < cs[0]._len; row++) {
            double num = mul.at(cs[0].start() + row);
            if (num < value) {
                for (int column = 0; column < cs.length; column++) {
                    ncs[column].addNum(cs[column].atd(row));
                }
            }
        }
    }
}
