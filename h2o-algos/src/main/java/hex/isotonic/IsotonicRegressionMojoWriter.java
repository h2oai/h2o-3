package hex.isotonic;

import hex.ModelMojoWriter;

import java.io.IOException;

public class IsotonicRegressionMojoWriter
        extends ModelMojoWriter<IsotonicRegressionModel, IsotonicRegressionModel.IsotonicRegressionParameters, IsotonicRegressionModel.IsotonicRegressionOutput> {

    @SuppressWarnings("unused")  // Called through reflection in ModelBuildersHandler
    public IsotonicRegressionMojoWriter() {}

    public IsotonicRegressionMojoWriter(IsotonicRegressionModel model) {
        super(model);
    }

    @Override
    public String mojoVersion() {
        return "1.00";
    }

    @Override
    protected void writeModelData() throws IOException {
        write(model.toIsotonicCalibrator());
    }

}

