import sys
sys.path.insert(1,"../../../")
import time

from h2o.estimators.xgboost import *
from tests import pyunit_utils

'''
The goal of this test is to compare the results of H2OXGBoost and natibve XGBoost for binomial classification.
The dataset contains both numerical and enum columns.
'''
def comparison_test_dense():
    if sys.version.startswith("2"):
        print("native XGBoost tests only supported on python3")
        return
    import xgboost as xgb
    assert H2OXGBoostEstimator.available() is True
    ret = h2o.cluster()
    if len(ret.nodes) == 1:
        runSeed = 1
        dataSeed = 17
        testTol = 1e-5
        ntrees = 17
        maxdepth = 5
        # CPU Backend is forced for the results to be comparable
        h2oParamsD = {"ntrees":ntrees, "max_depth":maxdepth, "seed":runSeed, "learn_rate":0.7, "col_sample_rate_per_tree" : 0.9,
                     "min_rows" : 5, "score_tree_interval": ntrees+1, "dmatrix_type":"sparse", "tree_method": "exact", "backend":"cpu"}
        nativeParam = {'colsample_bytree': h2oParamsD["col_sample_rate_per_tree"],
                       'tree_method': 'exact',
                       'seed': h2oParamsD["seed"],
                       'booster': 'gbtree',
                       'objective': 'reg:linear',
                       'eta': h2oParamsD["learn_rate"],
                       'grow_policy': 'depthwise',
                       'alpha': 0.0,
                       'subsample': 1.0,
                       'colsample_bylevel': 1.0,
                       'max_delta_step': 0.0,
                       'min_child_weight': h2oParamsD["min_rows"],
                       'gamma': 0.0,
                       'max_depth': h2oParamsD["max_depth"]}

        nrows = 10000
        ncols = 10
        factorL = 11
        numCols = 0
        enumCols = ncols-numCols
        responseL = 2

        trainFile = pyunit_utils.genTrainFrame(nrows, numCols, enumCols=enumCols, enumFactors=factorL, miscfrac=0.5,
                                               responseLevel=responseL, randseed=dataSeed)


        y='response'
        trainFile = trainFile.drop(y)   # drop the enum response and generate real values here
        yresp = 0.99*pyunit_utils.random_dataset_numeric_only(10000, 1, integerR = 1000000, misFrac=0, randSeed=dataSeed)
        yresp.set_name(0, y)
        trainFile = trainFile.cbind(yresp)
        myX = trainFile.names
        myX.remove(y)
        enumCols = myX[0:enumCols]

        h2oModelD = H2OXGBoostEstimator(**h2oParamsD)
        # gather, print and save performance numbers for h2o model
        h2oModelD.train(x=myX, y=y, training_frame=trainFile)
        h2oTrainTimeD = h2oModelD._model_json["output"]["run_time"]
        time1 = time.time()
        h2oPredictD = h2oModelD.predict(trainFile)
        h2oPredictTimeD = time.time()-time1

        # train the native XGBoost
        nativeTrain = pyunit_utils.convertH2OFrameToDMatrixSparse(trainFile, y, enumCols=enumCols)
        nrounds=ntrees
        nativeModel = xgb.train(params=nativeParam, dtrain=nativeTrain, num_boost_round=nrounds)
        nativeTrainTime = time.time()-time1
        # create a "test" matrix - it will be identical to "train" matrix but it will not have any cached predictions
        # if we tried to use matrix `nativeTrain` predict(..) will not actually compute anything it will return the cached predictions
        # cached predictions are slightly different from the actual predictions
        nativeTest = pyunit_utils.convertH2OFrameToDMatrixSparse(trainFile, y, enumCols=enumCols)
        time1=time.time()
        nativePred = nativeModel.predict(data=nativeTest, ntree_limit=ntrees)
        nativeScoreTime = time.time()-time1

        pyunit_utils.summarizeResult_regression(h2oPredictD, nativePred, h2oTrainTimeD, nativeTrainTime, h2oPredictTimeD,
                                              nativeScoreTime, tolerance=testTol)
    else:
        print("********  Test skipped.  This test cannot be performed in multinode environment.")

if __name__ == "__main__":
    pyunit_utils.standalone_test(comparison_test_dense)
else:
    comparison_test_dense()
