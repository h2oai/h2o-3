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
    if sys.version_info.major == 2:
        print("native XGBoost tests only supported on python3")
        return
    if sys.version_info.major == 3 and sys.version_info.minor >= 9:
        print("native XGBoost tests only doesn't run on Python 3.{0} for now.".format(sys.version_info.minor))
        return
    import xgboost as xgb
    assert H2OXGBoostEstimator.available() is True
    ret = h2o.cluster()
    if len(ret.nodes) == 1:
        runSeed = 1
        dataSeed = 17
        testTol = 1e-6
        ntrees = 17
        maxdepth = 5
        # CPU Backend is forced for the results to be comparable
        h2oParamsD = {"ntrees": ntrees,
                      "max_depth": maxdepth,
                      "seed": runSeed,
                      "learn_rate": 0.7,
                      "col_sample_rate_per_tree": 0.9,
                      "min_rows": 5,
                      "score_tree_interval": ntrees + 1,
                      "dmatrix_type": "dense",
                      "tree_method": "exact",
                      "backend": "cpu"}
        nativeParam = {'colsample_bytree': h2oParamsD["col_sample_rate_per_tree"],
                       'tree_method': 'exact',
                       'seed': h2oParamsD["seed"],
                       'booster': 'gbtree',
                       'objective': 'binary:logistic',
                       'eta': h2oParamsD["learn_rate"],
                       'grow_policy': 'depthwise',
                       'alpha': 0.0,
                       'subsample': 1.0,
                       'colsample_bylevel': 1.0,
                       'max_delta_step': 0.0,
                       'min_child_weight': h2oParamsD["min_rows"],
                       'gamma': 0.0,
                       'max_depth': h2oParamsD["max_depth"],
                       'eval_metric': ['auc', 'aucpr']}

        nrows = 10000
        ncols = 20
        factorL = 20
        numCols = 10
        enumCols = ncols - numCols

        trainFile = pyunit_utils.genTrainFrame(nrows, numCols, enumCols=enumCols, enumFactors=factorL,
                                               miscfrac=0.01, randseed=dataSeed)  # load in dataset and add response column
        myX = trainFile.names
        y = 'response'
        myX.remove(y)
        enumCols = myX[0:enumCols]

        h2oModelD = H2OXGBoostEstimator(**h2oParamsD)
        # gather, print and save performance numbers for h2o model
        h2oModelD.train(x=myX, y=y, training_frame=trainFile)
        h2oTrainTimeD = h2oModelD._model_json["output"]["run_time"]
        t1Array = h2oModelD._model_json["output"]["scoring_history"]
        print("classifier error: {0}".format(t1Array._cell_values[len(t1Array._cell_values) - 1][t1Array._col_header.index("training_classification_error")]))
        time1 = time.time()
        h2oPredictD = h2oModelD.predict(trainFile)
        h2oPredictTimeD = time.time() - time1

        # train the native XGBoost
        nrounds = ntrees
        nativeTrain = pyunit_utils.convertH2OFrameToDMatrix(trainFile, y, enumCols=enumCols)
        evals_result = {}
        watch_list = [(nativeTrain, 'train')]
        nativeModel = xgb.train(params=nativeParam, dtrain=nativeTrain, num_boost_round=nrounds,
                                evals=watch_list, verbose_eval=True, evals_result=evals_result)
        modelInfo = nativeModel.get_dump()
        print(modelInfo)
        print("num_boost_round: {1}, Number of trees built: {0}".format(len(modelInfo), nrounds))

        nativeTrainTime = time.time() - time1
        time1 = time.time()
        nativePred = nativeModel.predict(data=nativeTrain, ntree_limit=ntrees)
        nativeScoreTime = time.time() - time1

        pyunit_utils.summarizeResult_binomial(h2oPredictD, nativePred, h2oTrainTimeD, nativeTrainTime, h2oPredictTimeD,
                                              nativeScoreTime, tolerance=testTol)
        
        print("Comparing H2OXGBoost metrics with native XGBoost metrics when DMatrix is set to large dense.....")
        h2o_metrics = [h2oModelD.training_model_metrics()["AUC"], h2oModelD.training_model_metrics()["pr_auc"]]
        xgboost_metrics = [evals_result['train']['auc'][ntrees-1], evals_result['train']['aucpr'][ntrees-1]]
        pyunit_utils.summarize_metrics_binomial(h2o_metrics, xgboost_metrics, ["auc", "aucpr"])
        
    else:
        print("********  Test skipped.  This test cannot be performed in multinode environment.")

if __name__ == "__main__":
    pyunit_utils.standalone_test(comparison_test_dense)
else:
    comparison_test_dense()
