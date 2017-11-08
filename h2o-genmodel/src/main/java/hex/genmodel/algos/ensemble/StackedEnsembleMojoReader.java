package hex.genmodel.algos.ensemble;

import hex.genmodel.ModelMojoReader;

import java.io.IOException;

public class StackedEnsembleMojoReader extends ModelMojoReader<StackedEnsembleMojoModel> {

    @Override
    public String getModelName() {
        return "StackedEnsemble";
    }

    @Override
    protected void readModelData() throws IOException {
        _model._metaLearner = readkv("metalearner");
        _model._baseModels = readkv("base_models");
    }

    @Override
    protected StackedEnsembleMojoModel makeModel(String[] columns, String[][] domains, String responseColumn) {
        return new StackedEnsembleMojoModel(columns, domains, responseColumn);
    }
}
