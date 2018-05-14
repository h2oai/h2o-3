import pandas as pd
import xgboost as xgb
import time
import random

from h2o.estimators.xgboost import *
from tests import pyunit_utils

'''
The goal of this test is to compare the results of H2OXGBoost and natibve XGBoost for binomial classification.
The dataset contains both numerical and enum columns.
'''
def comparison_test():
    assert H2OXGBoostEstimator.available() is True
    runSeed = random.randint(1, 1073741824)
    ntrees = 10
    h2oParamsD = {"ntrees":ntrees, "max_depth":4, "seed":runSeed, "learn_rate":0.7, "col_sample_rate_per_tree" : 0.9,
                 "min_rows" : 5, "score_tree_interval": ntrees+1, "dmatrix_type":"dense"}
    h2oParamsS = {"ntrees":ntrees, "max_depth":4, "seed":runSeed, "learn_rate":0.7, "col_sample_rate_per_tree" : 0.9,
                  "min_rows" : 5, "score_tree_interval": ntrees+1, "dmatrix_type":"sparse"}
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

    nrows = 100000
    ncols = 10
    factorL = 10

    trainFile, enumCols = genTrainFiles(nrows, ncols, factorL)     # load in dataset and add response column
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

    h2oModelS = H2OXGBoostEstimator(**h2oParamsS)
    # gather, print and save performance numbers for h2o model
    h2oModelS.train(x=myX, y=y, training_frame=trainFile)
    h2oTrainTimeS = h2oModelS._model_json["output"]["run_time"]
    time1 = time.time()
    h2oPredictS = h2oModelS.predict(trainFile)
    h2oPredictTimeS = time.time()-time1

    print("H2OXGBoost train time with sparse DMatrix is {0}ms.  H2OXGBoost train time with dense DMatrix is {1}ms\n.  H2OGBoost scoring time with sparse DMatrix is {2}s."
          "  H2OGBoost scoring time with dense DMatrix is {3}s..".format(h2oTrainTimeS, h2oTrainTimeD,
                                                                             h2oPredictTimeS, h2oPredictTimeD))

    # train the native XGBoost
    nativeTrain = genDMatrix(trainFile, enumCols, myX, y)
    nativeModel = xgb.train(params=nativeParam,
                            dtrain=nativeTrain)
    nativeTrainTime = time.time()-time1
    time1=time.time()
    nativePred = nativeModel.predict(data=nativeTrain, ntree_limit=ntrees)
    nativeScoreTime = time.time()-time1

    summarizeResult(h2oPredictD, nativePred, h2oTrainTimeD, nativeTrainTime, h2oPredictTimeD, nativeScoreTime, h2oPredictS)

def summarizeResult(h2oPredictD, nativePred, h2oTrainTimeD, nativeTrainTime, h2oPredictTimeD, nativeScoreTime, h2oPredictS):
    # Result comparison in terms of time
    print("H2OXGBoost train time with sparse DMatrix is {0}ms.  Native XGBoost train time with dense DMtraix is {1}s\n.  H2OGBoost scoring time is {2}s."
          "  Native XGBoost scoring time with dense DMatrix is {3}s.".format(h2oTrainTimeD, nativeTrainTime,
                                                                             h2oPredictTimeD, nativeScoreTime))
    # Result comparison in terms of actual prediction value between the two
    h2oPredictD['predict'] = h2oPredictD['predict'].asnumeric()
    h2oPredictLocalD = h2oPredictD.as_data_frame(use_pandas=True, header=True)
    h2oPredictS['predict'] = h2oPredictS['predict'].asnumeric()
    h2oPredictLocalS = h2oPredictS.as_data_frame(use_pandas=True, header=True)

    # compare prediction probability and they should agree if they use the same seed
    for ind in range(h2oPredictD.nrow):
        assert  abs(h2oPredictLocalD['c0.l1'][ind]-nativePred[ind])<1e-6  or \
                abs(h2oPredictLocalS['c0.l1'][ind]-nativePred[ind])<1e-6, \
            "H2O prediction prob: {0} and native XGBoost prediction prob: {1}.  They are very " \
            "different.".format(h2oPredictLocalD['c0.l1'][ind], nativePred[ind])

def genTrainFiles(nrow, ncol, factorL):
    numCols = random.randint(1, ncol)
    enumCols = ncol-numCols
    if enumCols == 0:
        enumCols = 1
        numCols = numCols-1

    trainFrameEnums = pyunit_utils.random_dataset_enums_only(nrow, enumCols, factorL=factorL, misFrac=0)
    trainFrameNumerics = pyunit_utils.random_dataset_numeric_only(nrow, numCols, misFrac=0)
    yresponse = pyunit_utils.random_dataset_enums_only(nrow, 1, factorL=2, misFrac=0)
    yresponse.set_name(0,'response')
    trainFrame = trainFrameEnums.cbind(trainFrameNumerics.cbind(yresponse))
    return trainFrame, enumCols

def genDMatrix(h2oFrame, enumList, xlist, yresp):
    pandaFtrain = h2oFrame.as_data_frame(use_pandas=True, header=True)

    # need to fix categoricals
    for cname in enumList:
        ctemp = pd.get_dummies(pandaFtrain[cname], prefix=cname, drop_first=True)
        pandaFtrain.drop([cname], axis=1, inplace=True)
        pandaFtrain = pd.concat([ctemp, pandaFtrain], axis=1)

    c0 = pd.get_dummies(pandaFtrain[yresp], prefix=yresp, drop_first=True)
    pandaFtrain.drop([yresp], axis=1, inplace=True)
    pandaF = pd.concat([c0, pandaFtrain], axis=1)
    pandaF.rename(columns={c0.columns[0]:yresp}, inplace=True)
    data = pandaF.as_matrix(xlist)
    label = pandaF.as_matrix([yresp])

    return xgb.DMatrix(data=data, label=label)

if __name__ == "__main__":
    pyunit_utils.standalone_test(comparison_test)
else:
    comparison_test()
