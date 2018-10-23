from h2o.estimators.xgboost import *
from tests import pyunit_utils

def get_native_parameters_test():
    assert H2OXGBoostEstimator.available() is True
    ntrees = 17
    # CPU Backend is forced for the results to be comparable
    h2oParamsS = {"ntrees":ntrees, "max_depth":4, "seed":1, "learn_rate":0.7, "col_sample_rate_per_tree" : 0.9,
                  "min_rows" : 5, "score_tree_interval": ntrees+1, "dmatrix_type":"sparse", "tree_method": "exact", "backend":"cpu"}

    nrows = 1000
    ncols = 10
    factorL = 11
    numCols = 0
    enumCols = ncols-numCols

    trainFile = pyunit_utils.genTrainFrame(nrows, 0, enumCols=enumCols, enumFactors=factorL, miscfrac=0.1,
                                           randseed=17)
    print(trainFile)
    myX = trainFile.names
    y='response'
    myX.remove(y)

    h2oModelS = H2OXGBoostEstimator(**h2oParamsS)
    h2oModelS.train(x=myX, y=y, training_frame=trainFile)

    print(h2oModelS._model_json["output"]["native_parameters"].as_data_frame())

    assert h2oModelS._model_json["output"]["native_parameters"]._table_header == u"Native XGBoost Parameters"


if __name__ == "__main__":
    pyunit_utils.standalone_test(get_native_parameters_test)
else:
    get_native_parameters_test()
