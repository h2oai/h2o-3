package hex.genmodel.algos.isotonic;

import hex.genmodel.MojoModel;

public class IsotonicRegressionMojoModel extends MojoModel {

    protected IsotonicCalibrator _isotonic_calibrator;

    public IsotonicRegressionMojoModel(String[] columns, String[][] domains, String responseColumn) {
        super(columns, domains, responseColumn);
    }

    @Override
    public double[] score0(double[] row, double[] preds) {
        preds[0] = _isotonic_calibrator.calibrateP1(row[0]);
        return preds;
    }

}
