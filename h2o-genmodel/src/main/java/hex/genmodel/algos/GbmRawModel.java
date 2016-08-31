package hex.genmodel.algos;

import hex.genmodel.RawModel;

import java.util.Map;

/**
 * "Gradient Boosting Method" RawModel
 */
public class GbmRawModel extends RawModel {

    public GbmRawModel(ContentReader cr, Map<String, Object> info, String[] columns, String[][] domains) {
        super(cr, info, columns, domains);
    }

    @Override
    public double[] score0(double[] data, double[] preds) {
        return new double[0];
    }
}
