package hex.tree.isoforextended;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.util.CollectionUtils;

import java.util.Arrays;

public class SamplingUtils  {

    /**
     *
     * @param frame Frame to be sampled
     * @param sampleSize exact size of sample
     * @param seed ...
     * @return Random sub-sample of frame with size equals to {@code sampleSize}
     */
    public static Frame sampleOfFixedSize(Frame frame, int sampleSize, long seed) {
        long[] rowsToChoose = CollectionUtils.setOfUniqueRandomNumbers(sampleSize, frame.numRows(), seed);
        return new ChooseRowsTask(rowsToChoose).doAll(frame.types(), frame).outputFrame();
    }

    private static class ChooseRowsTask extends MRTask<ChooseRowsTask> {

        private final long[] rowsToChoose;

        public ChooseRowsTask(long[] rowsToChoose) {
            this.rowsToChoose = rowsToChoose;
        }

        @Override
        public void map(Chunk[] cs, NewChunk[] ncs) {
            for (int row = 0; row < cs[0]._len; row++) {
                if (Arrays.binarySearch(rowsToChoose, row + cs[0].start()) >= 0) {
                    for (int column = 0; column < cs.length; column++) {
                        ncs[column].addNum(cs[column].atd(row));
                    }
                }
            }
        }
    }
}
