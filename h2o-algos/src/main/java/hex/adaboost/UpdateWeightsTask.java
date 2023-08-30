package hex.adaboost;

import water.MRTask;
import water.fvec.Chunk;

/**
 * Update weights according to AdaBoost algorithm
 */
class UpdateWeightsTask extends MRTask<UpdateWeightsTask> {
    double exp_am;
    double exp_am_inverse;

    public UpdateWeightsTask(double alpha_m) {
        exp_am = Math.exp(alpha_m);
        exp_am_inverse = Math.exp(-alpha_m);
    }

    @Override
    public void map(Chunk weights, Chunk response, Chunk predict) {
        for (int row = 0; row < weights._len; row++) {
            double weight = weights.atd(row);
            if (response.at8(row) != predict.at8(row)) {
                weights.set(row, weight * exp_am);
            } else {
                weights.set(row, weight * exp_am_inverse);
            }
        }
    }
}
