package hex.genmodel.algos.isotonic;

import hex.genmodel.ModelMojoReader;

import java.io.IOException;

public class IsotonicRegressionMojoReader extends ModelMojoReader<IsotonicRegressionMojoModel> {

    @Override
    public String getModelName() {
        return "Isotonic Regression";
    }

    @Override
    public String mojoVersion() {
        return "1.00";
    }

    @Override
    protected void readModelData() throws IOException {
        _model._isotonic_calibrator = readIsotonicCalibrator();
    }

    @Override
    protected IsotonicRegressionMojoModel makeModel(String[] columns, String[][] domains, String responseColumn) {
        return new IsotonicRegressionMojoModel(columns, domains, responseColumn);
    }

}
