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
     * @return Random sub-sample of frame with size approximately {@code sampleSize}
     */
    public static Frame sampleOfApproxSize(Frame frame, int sampleSize, long seed) {
        return new SubSampleTask(sampleSize, frame.numRows(),seed)
                .doAll(frame.types(), frame.vecs()).outputFrame();
    }

    /**
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

    /**
     * Task to create random sub-sample of given Frame
     */
    private static class SubSampleTask extends MRTask<SubSampleTask> {

        private final long seed;
        private final double sampleRate;

        public SubSampleTask(int sampleSize, long frameNumRows, long seed) {
            this.seed = seed;
            this.sampleRate = ((double) sampleSize) / frameNumRows;
        }

        @Override
        public void map(Chunk[] cs, NewChunk[] ncs) {
            Random random = RandomUtils.getRNG(seed + cs[0].start());
            for (int row = 0; row < cs[0]._len; row++) {
                if (random.nextDouble() <= sampleRate) {
                    for (int column = 0; column < cs.length; column++) {
                        ncs[column].addNum(cs[column].atd(row));
                    }
                }
            }
        }
    }
}
