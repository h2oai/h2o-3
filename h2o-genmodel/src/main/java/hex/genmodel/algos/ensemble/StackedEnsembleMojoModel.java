package hex.genmodel.algos.ensemble;

import hex.genmodel.MojoModel;

import java.util.Arrays;

public class StackedEnsembleMojoModel extends MojoModel {

    MojoModel _metaLearner; //Currently only a GLM. May change to be DRF, GBM, XGBoost, or DL in the future
    StackedEnsembleMojoSubModel[] _baseModels; //An array of base models
    int _baseModelNum; //Number of base models

    public StackedEnsembleMojoModel(String[] columns, String[][] domains, String responseColumn) {
        super(columns, domains, responseColumn);
    }

    @Override
    public double[] score0(double[] row, double[] preds) {
        double[] basePreds = new double[_baseModelNum]; //Proper allocation for binomial and regression ensemble (one prediction per base model)
        double[] basePredsRow = new double[preds.length];
        if(_nclasses > 2) { //Multinomial
            int k = 0;
            basePreds = new double[_baseModelNum * _nclasses]; //Proper allocation for multinomial ensemble (class probabilities per base model)
            for(int i = 0; i < _baseModelNum; ++i){
                for(int j = 0; j < _nclasses; ++j){
                    basePreds[k] = _baseModels[i]._mojoModel.score0(_baseModels[i].remapRow(row), basePredsRow)[j + 1];
                    k++;
                }
            }
        }else if(_nclasses == 2){ //Binomial
            for(int i = 0; i < _baseModelNum; ++i) {
                _baseModels[i]._mojoModel.score0(_baseModels[i].remapRow(row), basePredsRow);
                basePreds[i] = basePredsRow[2];
            }
        }else{ //Regression
            for(int i = 0; i < _baseModelNum; ++i) { //Regression
                _baseModels[i]._mojoModel.score0(_baseModels[i].remapRow(row), basePredsRow);
                basePreds[i] = basePredsRow[0];
            }
        }
        _metaLearner.score0(basePreds, preds);
        return preds;
    }

    /**
     * In stacked ensembles, multiple models may appear. Problem with multiple models present is possibly different
     * internal order of features. Therefore, the scored row's values must be re-mapped to the internal order of each
     * model.
     */
    static class StackedEnsembleMojoSubModel {

        final MojoModel _mojoModel;
        final int[] _mapping; // Mapping. If null, no mapping is required.

        public StackedEnsembleMojoSubModel(MojoModel mojoModel, int[] mapping) {
            _mojoModel = mojoModel;
            _mapping = mapping;
        }

        /**
         * Returns a new array represeting row values re-mapped to order given by the underlying submodel.
         * Order of columns in the row may remain the same, yet a new instance of double[] is returned all the time.
         *
         * @param row Row to re-map
         * @return A new instance of double[] with values re-mapped to order given by the underlying submodel.
         */
        public double[] remapRow(final double[] row) {
            double[] remappedRow = Arrays.copyOf(row, row.length);
            if (_mapping == null) return remappedRow; // Null mapping means no remapping is needed.

            for (int i = 0; i < _mapping.length; i++) {
                if (_mapping[i] == i) continue; // do not copy if the column is not shifted
                remappedRow[_mapping[i]] = row[i];
            }

            return remappedRow;
        }
    }


}
