import xgboost as xgb
import time

from h2o.estimators.xgboost import *
from tests import pyunit_utils

'''
The goal of this test is to compare the results of H2OXGBoost and natibve XGBoost for binomial classification.
The dataset contains only enum columns.
'''
def comparison_test():
    assert H2OXGBoostEstimator.available() is True
    ret = h2o.cluster()
    if len(ret.nodes) == 1:
        runSeed = 1
        dataSeed = 17
        ntrees = 17
        responseL = 11
        # CPU Backend is forced for the results to be comparable
        h2oParamsS = {"ntrees":ntrees, "max_depth":4, "seed":runSeed, "learn_rate":0.7, "col_sample_rate_per_tree" : 0.9,
                      "min_rows" : 5, "score_tree_interval": ntrees+1, "dmatrix_type":"sparse", "tree_method": "exact", "backend":"cpu"}
        nativeParam = {'colsample_bytree': h2oParamsS["col_sample_rate_per_tree"],
                       'tree_method': 'exact',
                       'seed': h2oParamsS["seed"],
                       'booster': 'gbtree',
                       'objective': 'multi:softprob',
                       'lambda': 0.0,
                       'eta': h2oParamsS["learn_rate"],
                       'grow_policy': 'depthwise',
                       'alpha': 0.0,
                       'subsample': 1.0,
                       'colsample_bylevel': 1.0,
                       'max_delta_step': 0.0,
                       'min_child_weight': h2oParamsS["min_rows"],
                       'gamma': 0.0,
                       'max_depth': h2oParamsS["max_depth"],
                       'num_class':responseL}

        nrows = 10000
        ncols = 10
        factorL = 11
        numCols = 0
        enumCols = ncols-numCols

        trainFile = pyunit_utils.genTrainFrame(nrows, numCols, enumCols=enumCols, enumFactors=factorL, miscfrac=0.5,
                                               responseLevel=responseL, randseed=dataSeed)       # load in dataset and add response column
        print(trainFile)
        myX = trainFile.names
        y='response'
        myX.remove(y)
        nativeTrain = pyunit_utils.convertH2OFrameToDMatrixSparse(trainFile, y, enumCols=myX)

        h2oModelS = H2OXGBoostEstimator(**h2oParamsS)
        # gather, print and save performance numbers for h2o model
        h2oModelS.train(x=myX, y=y, training_frame=trainFile)
        h2oTrainTimeS = h2oModelS._model_json["output"]["run_time"]
        time1 = time.time()
        h2oPredictS = h2oModelS.predict(trainFile)
        h2oPredictTimeS = time.time()-time1

        # train the native XGBoost
        nrounds = ntrees
        nativeModel = xgb.train(params=nativeParam, dtrain=nativeTrain, num_boost_round=nrounds)
        modelInfo = nativeModel.get_dump()
        print(modelInfo)
        print("num_boost_round: {1}, Number of trees built: {0}".format(len(modelInfo), nrounds))
        nativeTrainTime = time.time()-time1
        time1=time.time()
        nativePred = nativeModel.predict(data=nativeTrain, ntree_limit=ntrees)
        nativeScoreTime = time.time()-time1

        print("Comparing H2OXGBoost results with native XGBoost result when DMatrix is set to sparse.....")
        pyunit_utils.summarizeResult_multinomial(h2oPredictS, nativePred, h2oTrainTimeS, nativeTrainTime, h2oPredictTimeS,
                                              nativeScoreTime, tolerance=1e-6)
    else:
        print("********  Test skipped.  This test cannot be performed in multinode environment.")

if __name__ == "__main__":
    pyunit_utils.standalone_test(comparison_test)
else:
    comparison_test()
