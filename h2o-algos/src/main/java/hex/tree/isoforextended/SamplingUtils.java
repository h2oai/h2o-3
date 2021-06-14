package hex.tree.isoforextended;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.util.CollectionUtils;
import water.util.RandomUtils;

import java.util.HashSet;
import java.util.Random;

public class SamplingUtils  {

    /**
     *
     * @param frame Frame to be sampled
     * @param sampleSize exact size of sample
     * @param seed ...
     * @return Random sub-sample of frame with size equals to {@code sampleSize}
     */
    public static Frame sampleOfFixedSize(Frame frame, int sampleSize, long seed) {
        HashSet<Long> rowsToChoose = CollectionUtils.setOfUniqueRandomNumbers(sampleSize, frame.numRows(), seed);
        return new ChooseRowsTask(rowsToChoose).doAll(frame.types(), frame).outputFrame();
    }

    private static class ChooseRowsTask extends MRTask<ChooseRowsTask> {

        private final HashSet<Long> rowsToChoose;

        public ChooseRowsTask(HashSet<Long> rowsToChoose) {
            this.rowsToChoose = rowsToChoose;
        }

        @Override
        public void map(Chunk[] cs, NewChunk[] ncs) {
            for (int row = 0; row < cs[0]._len; row++) {
                if (rowsToChoose.contains(row + cs[0].start())) {
                    for (int column = 0; column < cs.length; column++) {
                        ncs[column].addNum(cs[column].atd(row));
                    }
                }
            }
        }
    }
}
