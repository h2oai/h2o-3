package hex.genmodel.algos.ensemble;

import hex.genmodel.MojoModel;
import hex.genmodel.MultiModelMojoReader;

import java.io.IOException;

public class StackedEnsembleMojoReader extends MultiModelMojoReader<StackedEnsembleMojoModel> {

    @Override
    public String getModelName() {
        return "StackedEnsemble";
    }

    @Override
    protected void readParentModelData() throws IOException {
        int baseModelNum = readkv("base_models_num", 0);
        _model._baseModelNum = baseModelNum;
        _model._metaLearner = getModel((String) readkv("metalearner"));
        _model._baseModels = new MojoModel[baseModelNum];
        for (int i = 0; i < baseModelNum; i++) {
            String modelKey = readkv("base_model" + i);
            _model._baseModels[i] = getModel(modelKey);
        }
    }

    @Override
    protected StackedEnsembleMojoModel makeModel(String[] columns, String[][] domains, String responseColumn) {
        return new StackedEnsembleMojoModel(columns, domains, responseColumn);
    }
}
