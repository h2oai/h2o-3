package hex.adaboost;

import water.MRTask;
import water.fvec.Chunk;

/**
 * Update weights according to AdaBoost algorithm
 */
class UpdateWeightsTask extends MRTask<UpdateWeightsTask> {
    double expAm;
    double expAmInverse;

    public UpdateWeightsTask(double alphaM) {
        expAm = Math.exp(alphaM);
        expAmInverse = Math.exp(-alphaM);
    }

    @Override
    public void map(Chunk weights, Chunk response, Chunk predict) {
        for (int row = 0; row < weights._len; row++) {
            double weight = weights.atd(row);
            if (response.at8(row) != predict.at8(row)) {
                weights.set(row, weight * expAm);
            } else {
                weights.set(row, weight * expAmInverse);
            }
        }
    }
}
