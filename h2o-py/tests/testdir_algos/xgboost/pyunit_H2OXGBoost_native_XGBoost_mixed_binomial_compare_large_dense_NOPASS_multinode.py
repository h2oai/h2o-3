import xgboost as xgb
import time
import random

from h2o.estimators.xgboost import *
from tests import pyunit_utils

'''
The goal of this test is to compare the results of H2OXGBoost and natibve XGBoost for binomial classification.
The dataset contains both numerical and enum columns.
'''
def comparison_test_dense():
    assert H2OXGBoostEstimator.available() is True

    runSeed = random.randint(1, 1073741824)
    testTol = 1e-6
    ntrees = 10
    maxdepth = 5
    h2oParamsD = {"ntrees":ntrees, "max_depth":maxdepth, "seed":runSeed, "learn_rate":0.7, "col_sample_rate_per_tree" : 0.9,
                 "min_rows" : 5, "score_tree_interval": ntrees+1, "dmatrix_type":"dense"}
    nativeParam = {'colsample_bytree': h2oParamsD["col_sample_rate_per_tree"],
                   'tree_method': 'auto',
                   'seed': h2oParamsD["seed"],
                   'booster': 'gbtree',
                   'objective': 'binary:logistic',
                   'lambda': 0.0,
                   'eta': h2oParamsD["learn_rate"],
                   'grow_policy': 'depthwise',
                   'alpha': 0.0,
                   'subsample': 1.0,
                   'colsample_bylevel': 1.0,
                   'max_delta_step': 0.0,
                   'min_child_weight': h2oParamsD["min_rows"],
                   'gamma': 0.0,
                   'max_depth': h2oParamsD["max_depth"]}

    nrows = random.randint(10000, 100000)
    ncols = random.randint(5, 20)
    factorL = random.randint(11, 20)
    numCols = random.randint(1, ncols)
    enumCols = ncols-numCols

    trainFile = pyunit_utils.genTrainFrame(nrows, numCols, enumCols=enumCols, enumFactors=factorL, miscfrac=0.01)     # load in dataset and add response column
    myX = trainFile.names
    y='response'
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
    nativeTrain = pyunit_utils.convertH2OFrameToDMatrix(trainFile, y, enumCols=enumCols)
    nativeModel = xgb.train(params=nativeParam,
                            dtrain=nativeTrain)
    nativeTrainTime = time.time()-time1
    time1=time.time()
    nativePred = nativeModel.predict(data=nativeTrain, ntree_limit=ntrees)
    nativeScoreTime = time.time()-time1

    pyunit_utils.summarizeResult_binomial(h2oPredictD, nativePred, h2oTrainTimeD, nativeTrainTime, h2oPredictTimeD,
                                          nativeScoreTime, tolerance=testTol)

if __name__ == "__main__":
    pyunit_utils.standalone_test(comparison_test_dense)
else:
    comparison_test_dense()
