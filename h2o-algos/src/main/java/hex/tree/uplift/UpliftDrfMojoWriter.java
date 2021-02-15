package hex.tree.uplift;

import hex.tree.SharedTreeMojoWriter;

import java.io.IOException;

/**
 * Mojo definition for DRF model.
 */
public class UpliftDrfMojoWriter extends SharedTreeMojoWriter<UpliftDRFModel, UpliftDRFModel.UpliftDRFParameters, UpliftDRFModel.UpliftDRFOutput> {

    @SuppressWarnings("unused")  // Called through reflection in ModelBuildersHandler
    public UpliftDrfMojoWriter() {}

    public UpliftDrfMojoWriter(UpliftDRFModel model) { super(model); }

    @Override public String mojoVersion() {
        return "1.40";
    }

    @Override
    protected void writeModelData() throws IOException {
        super.writeModelData();
        writekv("uplift_column", model._parms._uplift_column);
    }
}

