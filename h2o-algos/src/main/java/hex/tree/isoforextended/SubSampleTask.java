package hex.tree.isoforextended;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.NewChunk;
import water.util.RandomUtils;

import java.util.Random;

/**
 * Task to create random sub-sample of given Frame
 */
public class SubSampleTask extends MRTask<SubSampleTask> {

    private final long seed;
    private final double sampleRate;

    public SubSampleTask(long subSampleSize, long frameNumRows, long seed) {
        this.seed = seed;
        this.sampleRate = ((double) subSampleSize) / frameNumRows;

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
