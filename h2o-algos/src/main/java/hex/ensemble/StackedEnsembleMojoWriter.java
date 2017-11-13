package hex.ensemble;

import hex.Model;
import hex.MultiModelMojoWriter;
import hex.StackedEnsembleModel;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import water.DKV;

public class StackedEnsembleMojoWriter extends MultiModelMojoWriter<StackedEnsembleModel,
        StackedEnsembleModel.StackedEnsembleParameters, StackedEnsembleModel.StackedEnsembleOutput> {

    @SuppressWarnings("unused")  // Called through reflection in ModelBuildersHandler
    public StackedEnsembleMojoWriter() {}

    public StackedEnsembleMojoWriter(StackedEnsembleModel model) {super(model);}


    @Override
    public String mojoVersion() {
        return "1.0";
    }

    @Override
    protected List<Model> getSubModels() {
        LinkedList<Model> subModels = new LinkedList<>();
        if (model._output._metalearner != null)
            subModels.add(model._output._metalearner);
        for (int i = 0; i < model._parms._base_models.length; i++)
            if (model._parms._base_models[i] != null) {
                Model aModel = DKV.getGet(model._parms._base_models[i]);
                subModels.add(aModel);
            }
        return subModels;
    }

    @Override
    protected void writeParentModelData() throws IOException {
        writekv("base_models_num", model._parms._base_models.length);
        writekv("metalearner", model._output._metalearner._key);
        for (int i = 0; i < model._parms._base_models.length; i++) {
            writekv("base_model" + i, model._parms._base_models[i]);
        }
    }
}
