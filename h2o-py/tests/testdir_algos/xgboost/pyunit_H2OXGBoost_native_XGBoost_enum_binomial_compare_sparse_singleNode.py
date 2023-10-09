import time
import sys
sys.path.insert(1,"../../../")
from h2o.estimators.xgboost import *
from tests import pyunit_utils

'''
The goal of this test is to compare the results of H2OXGBoost and natibve XGBoost for binomial classification.
The dataset contains only enum columns.
'''
def comparison_test():
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
        ntrees = 17
        # CPU Backend is forced for the results to be comparable
        h2oParamsS = {"ntrees": ntrees,
                      "max_depth": 4,
                      "seed": runSeed,
                      "learn_rate": 0.7,
                      "col_sample_rate_per_tree": 0.9,
                      "min_rows": 5,
                      "score_tree_interval": ntrees+1,
                      "dmatrix_type": "sparse",
                      "tree_method": "exact",
                      "backend": "cpu"}
        nativeParam = {'colsample_bytree': h2oParamsS["col_sample_rate_per_tree"],
                       'tree_method': 'exact',
                       'seed': h2oParamsS["seed"],
                       'booster': 'gbtree',
                       'objective': 'binary:logistic',
                       'eta': h2oParamsS["learn_rate"],
                       'grow_policy': 'depthwise',
                       'alpha': 0.0,
                       'subsample': 1.0,
                       'colsample_bylevel': 1.0,
                       'max_delta_step': 0.0,
                       'min_child_weight': h2oParamsS["min_rows"],
                       'gamma': 0.0,
                       'max_depth': h2oParamsS["max_depth"],
                       'eval_metric': ['auc', 'aucpr']}

        nrows = 10000
        ncols = 10
        factorL = 11
        numCols = 0
        enumCols = ncols-numCols

        trainFile = pyunit_utils.genTrainFrame(nrows, 0, enumCols=enumCols, enumFactors=factorL, miscfrac=0.1,
                                               randseed=dataSeed)       # load in dataset and add response column
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
        nrounds=ntrees
        evals_result = {}
        watch_list = [(nativeTrain,'train')]
        nativeModel = xgb.train(params=nativeParam,
                                dtrain=nativeTrain, num_boost_round=nrounds,
                                evals=watch_list, verbose_eval=True, evals_result=evals_result)
        modelInfo = nativeModel.get_dump()
        print(modelInfo)
        print("num_boost_round: {1}, Number of trees built: {0}".format(len(modelInfo), nrounds))
        nativeTrainTime = time.time()-time1
        time1=time.time()
        nativePred = nativeModel.predict(data=nativeTrain, ntree_limit=ntrees)
        nativeScoreTime = time.time()-time1

        print("Comparing H2OXGBoost results with native XGBoost result when DMatrix is set to sparse.....")
        pyunit_utils.summarizeResult_binomial(h2oPredictS, nativePred, h2oTrainTimeS, nativeTrainTime, h2oPredictTimeS,
                                              nativeScoreTime, tolerance=1e-6)

        print("Comparing H2OXGBoost metrics with native XGBoost metrics when DMatrix is set to sparse.....")
        h2o_metrics = [h2oModelS.training_model_metrics()["AUC"], h2oModelS.training_model_metrics()["pr_auc"]]
        xgboost_metrics = [evals_result['train']['auc'][ntrees-1], evals_result['train']['aucpr'][ntrees-1]]
        # TODO: less tolerance ? 
        pyunit_utils.summarize_metrics_binomial(h2o_metrics, xgboost_metrics, ["auc", "aucpr"], tolerance=1e-3)
        
    else:
        print("********  Test skipped.  This test cannot be performed in multinode environment.")


if __name__ == "__main__":
    pyunit_utils.standalone_test(comparison_test)
else:
    comparison_test()
