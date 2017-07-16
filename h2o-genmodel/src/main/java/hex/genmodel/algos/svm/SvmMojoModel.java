package hex.genmodel.algos.svm;

import hex.genmodel.MojoModel;

public class SvmMojoModel extends MojoModel {

    boolean meanImputation;
    double[] weights;
    double[] means;
    double interceptor;
    double defaultThreshold;
    double threshold;

    SvmMojoModel(String[] columns, String[][] domains) {
        super(columns, domains);
    }

    @Override
    public double[] score0(double[] row, double[] preds) {
        java.util.Arrays.fill(preds, 0);
        double pred = interceptor;

        for (int i = 0; i < row.length; i++) {
            if (Double.isNaN(row[i]) && meanImputation) {
                pred += (means[i] * weights[i]);
            } else {
                pred += (row[i] * weights[i]);
            }
        }

        if (_nclasses == 1) {
            preds[0] = pred;
        } else {
            if (pred > threshold) {
                preds[2] = pred < defaultThreshold ? defaultThreshold : pred;
                preds[1] = preds[2] - 1;
                preds[0] = 1;
            } else {
                preds[2] = pred >= defaultThreshold ? defaultThreshold - 1 : pred;
                preds[1] = preds[2] + 1;
                preds[0] = 0;
            }
        }

        return preds;
    }
}
