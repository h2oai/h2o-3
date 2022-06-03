package hex.tree.sdt.mrtasks;

import water.MRTask;
import water.fvec.Chunk;

// todo - adapt for any number of classes, now it is binary
public class CountSplitValuesMRTask extends MRTask<CountSplitValuesMRTask> {
    public int featureSplit;
    public double threshold;
    public int countLeft;
    public int countLeft0;
    public int countRight;
    public int countRight0;

    public CountSplitValuesMRTask(int featureSplit, double threshold) {
        this.featureSplit = featureSplit;
        this.threshold = threshold;
        this.countLeft = 0;
        this.countLeft0 = 0;
        this.countRight = 0;
        this.countRight0 = 0;
    }

    @Override
    public void map(Chunk[] cs) {
        int classFeature = cs.length - 1;
        int numRows = cs[0]._len;
        for (int row = 0; row < numRows; row++) {
            if (cs[featureSplit].atd(row) <= threshold) {
                countLeft++;
                if (Math.abs(cs[classFeature].atd(row)) <= 0.1) {
                    countLeft0++;
                }
            } else {
                countRight++;
//                System.out.println(cs[classFeature].atd(row));
                if (Math.abs(cs[classFeature].atd(row)) <= 0.1) {
                    countRight0++;
//                    System.out.println("yes, " + countRight0);
                }
            }
        }
    }

    @Override
    public void reduce(CountSplitValuesMRTask mrt) {
        this.countLeft += mrt.countLeft;
        this.countRight += mrt.countRight;
        this.countLeft0 += mrt.countLeft0;
        this.countRight0 += mrt.countRight0;
    }
}
