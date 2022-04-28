package hex.tree.sdt;

import water.MRTask;
import water.fvec.Chunk;

// todo - adapt for any number of classes, now it is binary
public class CountSplitValuesMRTask extends MRTask<CountSplitValuesMRTask> {
    int featureSplit;
    double threshold;
    int classFeature;
    int countLeft;
    int countLeft0;
    int countRight;
    int countRight0;

    public CountSplitValuesMRTask(int featureSplit, double threshold, int classFeature) {
        this.featureSplit = featureSplit;
        this.threshold = threshold;
        this.classFeature = classFeature;
        this.countLeft = 0;
        this.countLeft0 = 0;
        this.countRight = 0;
        this.countRight0 = 0;
    }

    @Override
    public void map(Chunk[] cs) {
        int numRows = cs[0]._len;
        for (int row = 0; row < numRows; row++) {
            if (cs[featureSplit].atd(row) <= threshold) {
                countLeft++;
                if (cs[classFeature].atd(row) == 0) {
                    countLeft0++;
                }
            } else {
                countRight++;
                if (cs[classFeature].atd(row) == 0) {
                    countRight0++;
                }
            }
        }
    }

    @Override
    public void reduce(CountSplitValuesMRTask mrt) {
        this.countLeft += mrt.countLeft;
        this.countRight += mrt.countRight;
    }
}
