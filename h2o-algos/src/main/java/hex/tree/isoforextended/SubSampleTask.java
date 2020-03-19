package hex.tree.isoforextended;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.NewChunk;
import water.util.RandomUtils;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TODO avalenta make subsampling randomized and guarantee given size for small datasets
 */
public class SubSampleTask extends MRTask<SubSampleTask> {

    private int subSampleSize;
    private Random random;
    
    private AtomicInteger currentSubSampleSize = new AtomicInteger(-1);

    public SubSampleTask(int subSampleSize, long seed) {
        this.subSampleSize = subSampleSize;
        this.random = RandomUtils.getRNG(seed);
    }

    @Override
    public void map(Chunk[] cs, NewChunk[] ncs) {
        for (int row = 0; row < cs[0]._len; row++) {
            if (random.nextBoolean() && (currentSubSampleSize.incrementAndGet() < subSampleSize)) {
                for (int column = 0; column < cs.length; column++) {
                    ncs[column].addNum(cs[column].atd(row));
                }
            }
        }
    }
}
