package hex.tree.uplift;

import hex.tree.SharedTreeMojoWriter;

import java.io.IOException;

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
        writekv("default_auuc_thresholds", model._output._defaultAuucThresholds);
    }
}
