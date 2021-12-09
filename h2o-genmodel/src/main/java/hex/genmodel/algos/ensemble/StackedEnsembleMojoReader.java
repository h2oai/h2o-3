package hex.genmodel.algos.ensemble;

import hex.genmodel.MojoModel;
import hex.genmodel.MultiModelMojoReader;

public class StackedEnsembleMojoReader extends MultiModelMojoReader<StackedEnsembleMojoModel> {

    @Override
    public String getModelName() {
        return "StackedEnsemble";
    }

    @Override
    protected String getModelMojoReaderClassName() { return "hex.ensemble.StackedEnsembleMojoWriter"; }

    @Override
    protected void readParentModelData() {
        int baseModelNum = readkv("base_models_num", 0);
        _model._baseModelNum = baseModelNum;
        _model._metaLearner = getModel((String) readkv("metalearner"));

        final String metaLearnerTransform = readkv("metalearner_transform", "NONE");
        if (!metaLearnerTransform.equals("NONE") && !metaLearnerTransform.equals("Logit"))
            throw new UnsupportedOperationException("Metalearner Transform \"" + metaLearnerTransform + "\" is not supported!");
        _model._useLogitMetaLearnerTransform = metaLearnerTransform.equals("Logit");

        _model._baseModels = new StackedEnsembleMojoModel.StackedEnsembleMojoSubModel[baseModelNum];
        final String[] columnNames = readkv("[columns]");
        for (int i = 0; i < baseModelNum; i++) {
            String modelKey = readkv("base_model" + i);
            if (modelKey == null)
                continue;
            final MojoModel model = getModel(modelKey);
            _model._baseModels[i] = new StackedEnsembleMojoModel.StackedEnsembleMojoSubModel(model,
                    createMapping(model, columnNames, modelKey));
        }
    }

    /**
     * Creates an array of integers with mapping of referential column name space into model-specific column name space.
     *
     * @param model     Model to create column mapping for
     * @param reference Column mapping serving as a reference
     * @param modelName Name of the model for various error reports
     * @return An array of integers with representing the mapping.
     */
    private static int[] createMapping(final MojoModel model, final String[] reference, final String modelName) {
        String[] features = model.features();
        int[] mapping = new int[features.length];
        for (int i = 0; i < mapping.length; i++) {
            String feature = features[i];
            mapping[i] = findColumnIndex(reference, feature);
            if (mapping[i] < 0) {
                throw new IllegalStateException(String.format("Model '%s' does not have input column '%s'",
                        modelName, feature));
            }
        }
        return mapping;
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
        return "1.01";
    }
}
