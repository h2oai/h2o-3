package hex.genmodel.algos.xgboost;

import hex.genmodel.MojoModel;

/**
 * "Gradient Boosting Machine" MojoModel
 */
public final class XGBoostMojoModel extends MojoModel {
    public XGBoostMojoModel(String[] columns, String[][] domains) {
        super(columns, domains);
    }

    @Override
    public final double[] score0(double[] row, double offset, double[] preds) {
      return null;
    }

    @Override
    public double[] score0(double[] row, double[] preds) {
        return score0(row, 0.0, preds);
    }

}
