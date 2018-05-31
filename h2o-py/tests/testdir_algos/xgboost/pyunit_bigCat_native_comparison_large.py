import pandas as pd
import xgboost as xgb
import time

from h2o.estimators.xgboost import *
from tests import pyunit_utils

'''
The goal of this test is to compare h2oxgboost and native xgboost performance in terms of
1. training time and performance
2. scoring time and performance

The dataset contains categoricals only of different cardinality.

Ideally, the two should yield the same results with the same seeds.  However, we have slightly different
parameter sets between the two.  Need to resolve this, Michalk/Pavel/Nidhi/Megan/whoever, help?
'''
def bigCat_test():
    assert H2OXGBoostEstimator.available() is True
    h2oParams = {"ntrees":10, "max_depth":4, "seed":987654321, "learn_rate":0.7, "col_sample_rate_per_tree" : 0.9,
                 "min_rows" : 5, "score_tree_interval": 10}
    nativeParam = {'colsample_bytree': h2oParams["col_sample_rate_per_tree"],
                   'tree_method': 'auto',
                   'seed': h2oParams["seed"],
                   'max_depth': h2oParams["max_depth"],
                   'booster': 'gbtree',
                   'objective': 'binary:logistic',
                   'lambda': 0.0,
                   'eta': h2oParams["learn_rate"],
                   'grow_policy': 'depthwise',
                   'alpha': 0.0,
                   'subsample': 1.0,
                   'colsample_bylevel': 1.0,
                   'max_delta_setp': 0.0,
                   'min_child_weight': h2oParams["min_rows"],
                   'gamma': 0.0}
    nrows = 100000
    ncols = 10
    factorL = 10

    trainFile = genTrainFiles(nrows, ncols, factorL)     # load in dataset and add response column
    myX = trainFile.names
    y='response'
    myX.remove(y)

    # train the H2OXGBoost
    h2oModel = H2OXGBoostEstimator(**h2oParams)
    # gather, print and save performance numbers for h2o model
    h2oModel.train(x=myX, y=y, training_frame=trainFile)
    h2oTrainTime = h2oModel._model_json["output"]["run_time"]
    time1 = time.time()
    h2oPredict = h2oModel.predict(trainFile)
    h2oPredictTime = time.time()-time1

    # train the native XGBoost
    nativeTrain = genDMatrix(trainFile, myX, y)
    nativeModel = xgb.train(params=nativeParam,
                            dtrain=nativeTrain ,
                            num_boost_round=h2oParams["ntrees"])
    nativeTrainTime = time.time()-time1
    time1=time.time()
    nativePred = nativeModel.predict(data=nativeTrain)
    nativeScoreTime = time.time()-time1

    summarizeResult(h2oPredict, nativePred, h2oTrainTime, h2oPredictTime, nativeTrainTime, nativeScoreTime,
                    nativeTrain.get_label())

def summarizeResult(h2oPredict, nativePred, h2oTrainTime, h2oPredictTime, nativeTrainTime, nativeScoreTime,
                    nativeLabels):
    # Result comparison in terms of time
    print("H2OXGBoost train time is {0}ms.  Native XGBoost train time is {1}s\n.  H2OGBoost scoring time is {2}s."
          "  Native XGBoost scoring time is {3}s.".format(h2oTrainTime, nativeTrainTime, h2oPredictTime,
                                                          nativeScoreTime))
    # Result comparison in terms of accuracy right now, that is the only thing available
    nativeErr = sum(1 for i in range(len(nativePred)) if int(nativePred[i] > 0.5) != nativeLabels[i])/float(len(nativePred))
    h2oPredict['predict'] = h2oPredict['predict'].asnumeric()
    h2oPredictLocal = h2oPredict.as_data_frame(use_pandas=True, header=True)
    h2oErr = sum(1 for i in range(h2oPredict.nrow)
                 if abs(h2oPredictLocal['predict'][i] - nativeLabels[i])>1e-6)/float(len(nativePred))
    print("H2OXGBoost error rate is {0} and native XGBoost error rate is {1}".format(h2oErr, nativeErr))

    # compare prediction probability and they should agree if they use the same seed
    for ind in range(h2oPredict.nrow):
        if abs(nativePred[ind]-h2oPredictLocal['c0.l1'][ind])>1e-6:
            print("H2O prediction prob: {0} and XGBoost prediction prob: {1}.  They are very "
                  "different.".format(h2oPredictLocal['c0.l1'][ind], nativePred[ind]))
            break

def genTrainFiles(nrow, ncol, factorL):
    trainFrame = pyunit_utils.random_dataset_enums_only(nrow, ncol, factorL=factorL, misFrac=0)
    yresponse = pyunit_utils.random_dataset_enums_only(trainFrame.nrow, 1, factorL=2, misFrac=0)
    yresponse.set_name(0,'response')
    trainFrame = trainFrame.cbind(yresponse)
    return trainFrame

def genDMatrix(h2oFrame, xlist, yresp):
    pandaFtrain = h2oFrame.as_data_frame(use_pandas=True, header=True)
    # need to fix categoricals
    for cname in xlist:
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
    pyunit_utils.standalone_test(bigCat_test)
else:
    bigCat_test()
