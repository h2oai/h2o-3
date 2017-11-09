package hex.genmodel.algos.ensemble;

import hex.genmodel.MojoModel;

public class StackedEnsembleMojoModel extends MojoModel {

    MojoModel _metaLearner; //Currently only a GLM. May change to be DRF, GBM, XGBoost, or DL in the future
    MojoModel[] _baseModels; //An array of base models
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
                    basePreds[k] = _baseModels[i].score0(row, basePredsRow)[j+1];
                    k++;
                }
            }
        }else if(_nclasses == 2){ //Binomial
            for(int i = 0; i < _baseModelNum; ++i) {
                _baseModels[i].score0(row, basePredsRow);
                basePreds[i] = basePredsRow[2];
            }
        }else{ //Regression
            for(int i = 0; i < _baseModelNum; ++i) { //Regression
                _baseModels[i].score0(row, basePredsRow);
                basePreds[i] = basePredsRow[0];
            }
        }
        _metaLearner.score0(basePreds, preds);
        return preds;
    }


}
