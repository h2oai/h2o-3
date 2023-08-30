package hex.adaboost;

import water.MRTask;
import water.fvec.Chunk;

/**
 * Count sum of all weights and sum of bad predicted weights for AdaBoost purpose
 */
class CountWeTask extends MRTask<CountWeTask> {
    double W = 0;
    double We = 0;

    @Override
    public void map(Chunk weights, Chunk response, Chunk predict) {
        for (int row = 0; row < weights._len; row++) {
            double weight = weights.atd(row);
            W += weight;
            if (response.at8(row) != predict.at8(row)) {
                We += weight;
            }
        }
    }

    @Override
    public void reduce(CountWeTask mrt) {
        W += mrt.W;
        We += mrt.We;
    }
}
