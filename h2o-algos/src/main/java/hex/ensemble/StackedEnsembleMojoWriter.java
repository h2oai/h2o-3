package hex.ensemble;

import hex.Model;
import hex.ModelMetrics;
import hex.MultiModelMojoWriter;
import water.DKV;
import water.Key;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class StackedEnsembleMojoWriter extends MultiModelMojoWriter<StackedEnsembleModel,
        StackedEnsembleModel.StackedEnsembleParameters, StackedEnsembleModel.StackedEnsembleOutput> {

    @SuppressWarnings("unused")  // Called through reflection in ModelBuildersHandler
    public StackedEnsembleMojoWriter() {}

    public StackedEnsembleMojoWriter(StackedEnsembleModel model) {
        super(model);
        // White list supported metalearning transforms
        if (!(model._parms._metalearner_transform == StackedEnsembleModel.StackedEnsembleParameters.MetalearnerTransform.NONE ||
                model._parms._metalearner_transform == StackedEnsembleModel.StackedEnsembleParameters.MetalearnerTransform.Logit)) {
            throw new UnsupportedOperationException("Cannot save Stacked Ensemble with metalearner_transform = \"" +
                    model._parms._metalearner_transform.name() + "\" to MOJO.");
        }
    }

    @Override
    public ModelMetrics.MetricBuilderFactory getModelBuilderFactory() { return null; }
    
    @Override
    public String mojoVersion() {
        return "1.01";
    }

    @Override
    protected List<Model> getSubModels() {
        LinkedList<Model> subModels = new LinkedList<>();
        if (model._output._metalearner != null)
            subModels.add(model._output._metalearner);
        for (Key<Model> baseModelKey : model._parms._base_models)
            if (baseModelKey != null && model.isUsefulBaseModel(baseModelKey)) {
                Model aModel = DKV.getGet(baseModelKey);
                subModels.add(aModel);
            }
        return subModels;
    }

    @Override
    protected void writeParentModelData() throws IOException {
        writekv("base_models_num", model._parms._base_models.length);
        writekv("metalearner", model._output._metalearner._key);
        writekv("metalearner_transform", model._parms._metalearner_transform.toString());
        for (int i = 0; i < model._parms._base_models.length; i++) {
            if (model.isUsefulBaseModel(model._parms._base_models[i])) {
                writekv("base_model" + i, model._parms._base_models[i]);
            }
        }
    }
}
