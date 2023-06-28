package hex.genmodel.algos.upliftdrf;
import hex.genmodel.algos.tree.SharedTreeMojoReader;

import java.io.IOException;

/**
 */
public class UpliftDrfMojoReader extends SharedTreeMojoReader<UpliftDrfMojoModel> {

    @Override
    public String getModelName() {
        return "Distributed Uplift Random Forest";
    }

    @Override
    protected void readModelData() throws IOException {
        super.readModelData();
        _model._treatmentColumn = readkv("treatment_column");
    }

    @Override
    protected UpliftDrfMojoModel makeModel(String[] columns, String[][] domains, String responseColumn) {
        return null;
    }

    @Override
    protected UpliftDrfMojoModel makeModel(String[] columns, String[][] domains, String responseColumn, String treatmentColumn) {
        return new UpliftDrfMojoModel(columns, domains, responseColumn, treatmentColumn);
    }

    @Override public String mojoVersion() {
        return "1.40";
    }
}
