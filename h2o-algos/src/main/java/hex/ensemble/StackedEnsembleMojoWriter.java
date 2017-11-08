package hex.ensemble;

import hex.ModelMojoWriter;
import hex.StackedEnsembleModel;

import java.io.IOException;

public class StackedEnsembleMojoWriter extends ModelMojoWriter<StackedEnsembleModel,
        StackedEnsembleModel.StackedEnsembleParameters, StackedEnsembleModel.StackedEnsembleOutput> {

    @SuppressWarnings("unused")  // Called through reflection in ModelBuildersHandler
    public StackedEnsembleMojoWriter() {}

    public StackedEnsembleMojoWriter(StackedEnsembleModel model) {super(model);}


    @Override
    public String mojoVersion() {
        return "1.20";
    }

    @Override
    protected void writeModelData() throws IOException {
        writekv("metalearner", model._output._metalearner._key);
        for (int i = 0; i < model._parms._base_models.length; i++) {
            writekv("base_models" + i, model._parms._base_models[i]);
        }
    }
}
