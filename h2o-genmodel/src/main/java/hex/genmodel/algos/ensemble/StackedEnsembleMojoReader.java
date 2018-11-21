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
        _model._baseModels = new StackedEnsembleMojoModel.StackedEnsembleMojoSubModel[baseModelNum];
        final String[] columnNames = readkv("[columns]");
        for (int i = 0; i < baseModelNum; i++) {
            String modelKey = readkv("base_model" + i);
            final MojoModel model = getModel(modelKey);
            _model._baseModels[i] = new StackedEnsembleMojoModel.StackedEnsembleMojoSubModel(model,
                    createMapping(model, columnNames, modelKey));
        }
    }

    /**
     * Creates an array of integers with mapping of referential column name space into model-specific column name space.
     *
     * @param model     Model to create column mapping for
     * @param reference Column mapping servig as a reference
     * @param modelName Name of the model for various error reports
     * @return An array of integers with mapping. Null of no mapping is necessary.
     */
    private static int[] createMapping(final MojoModel model, final String[] reference, final String modelName) {
        int[] mapping = new int[reference.length];
        if (model._names.length != reference.length) {
            throw new IllegalStateException(String.format("Model '%s' is expected to have has non-standard number of columns.",
                    modelName));
        }
        boolean foundDifference = false;
        for (int i = 0; i < reference.length; i++) {
            final int pos = findColumnIndex(model._names, reference[i]);
            if (pos == -1) {
                throw new IllegalStateException(String.format("Model '%s' does not have input column '%s'",
                        modelName, reference[i]));
            }
            if (pos != i) foundDifference = true;
            mapping[i] = pos;
        }

        if (foundDifference) {
            return mapping;
        } else return null;
    }

    private static int findColumnIndex(String[] arr, String searchedColname) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i].equals(searchedColname)) return i;
        }
        return -1;
    }

    @Override
    protected StackedEnsembleMojoModel makeModel(String[] columns, String[][] domains, String responseColumn) {
        return new StackedEnsembleMojoModel(columns, domains, responseColumn);
    }

    @Override public String mojoVersion() {
        return "1.00";
    }
}
