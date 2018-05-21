import pandas as pd
import xgboost as xgb
import time
import random
from xgboost import plot_tree
import matplotlib.pyplot as plt

from h2o.estimators.xgboost import *
from tests import pyunit_utils

'''
The goal of this test is to compare h2oxgboost and native xgboost performance in terms of
1. training time and performance
2. scoring time and performance

Ideally, the two should yield the same results with the same seeds.  However, we have slightly different
parameter sets between the two.  Need to resolve this, Michalk/Pavel/Nidhi/Megan/whoever, help?
'''
def higgs_compare_test():
    assert H2OXGBoostEstimator.available() is True

    # train H2O XGBoost first
    higgs_h2o_train = h2o.import_file(pyunit_utils.locate('bigdata/laptop/higgs_train_imbalance_100k.csv'))
    higgs_h2o_test = h2o.import_file(pyunit_utils.locate('bigdata/laptop/higgs_test_imbalance_100k.csv'))
    higgs_h2o_train[0] = higgs_h2o_train[0].asfactor()
    higgs_h2o_test[0] = higgs_h2o_test[0].asfactor()
    myX = list(higgs_h2o_train.names)
    y = "response"
    myX.remove(y)

    runSeed = random.randint(1, 1073741824)
    print("Seed used in running {0}.".format(runSeed))
    h2oParams = {"ntrees":100, "max_depth":10, "seed":runSeed, "learn_rate":0.7, "col_sample_rate_per_tree" : 0.9,
                 "min_rows" : 5, "score_tree_interval": 100}
    h2oModel = H2OXGBoostEstimator(**h2oParams)
    # gather, print and save performance numbers for h2o model
    h2oModel.train(x=myX, y=y, training_frame=higgs_h2o_train)
    h2oTrainTime = h2oModel._model_json["output"]["run_time"]
    time1 = time.time()
    h2oPredict = h2oModel.predict(higgs_h2o_test)
    h2oPredictTime = time.time()-time1

    # build native XGBoost model
    nativeTrain = genDMatrix(higgs_h2o_train, myX, y)
    nativeTest = genDMatrix(higgs_h2o_test, myX, y)
    time1 = time.time()
    nativeParam = {'eta': h2oParams["learn_rate"], 'objective': 'binary:logistic', 'booster': 'gbtree',
             'max_depth': h2oParams["max_depth"], 'seed': h2oParams["seed"], 'min_child_weight':h2oParams["min_rows"],
                   'colsample_bytree':h2oParams["col_sample_rate_per_tree"], 'alpha':0.0, 'nrounds':h2oParams["ntrees"]}
    nativeModel = xgb.train(params=nativeParam,
                            dtrain=nativeTrain)
    nativeTrainTime = time.time()-time1
    time1=time.time()
    nativePred = nativeModel.predict(data=nativeTest)
    nativeScoreTime = time.time()-time1

    # Result comparison in terms of time
    print("H2OXGBoost train time is {0}ms.  Native XGBoost train time is {1}s\n.  H2OGBoost scoring time is {2}s."
          "  Native XGBoost scoring time is {3}s.".format(h2oTrainTime, nativeTrainTime, h2oPredictTime,
                                                          nativeScoreTime))
    # Result comparison in terms of accuracy right now, that is the only thing available
    nativeLabels = nativeTest.get_label()
    nativeErr = sum(1 for i in range(len(nativePred)) if int(nativePred[i] > 0.5) != nativeLabels[i])/float(len(nativePred))
    h2oPredictLocal = h2oPredict.as_data_frame(use_pandas=True, header=True)
    h2oErr = sum(1 for i in range(h2oPredict.nrow)
                 if abs(h2oPredictLocal['predict'][i] - nativeLabels[i])>1e-6)/float(len(nativePred))
    print("H2OXGBoost error rate is {0} and native XGBoost error rate is {1}".format(h2oErr, nativeErr))
    assert (h2oErr <= nativeErr) or abs(h2oErr-nativeErr) < 1e-6, \
        "H2OXGBoost predict accuracy {0} and native XGBoost predict accuracy are too different!".format(h2oErr, nativeErr)

    # compare prediction probability and they should agree if they use the same seed
    for ind in range(h2oPredict.nrow):
        assert abs(nativePred[ind]-h2oPredictLocal['p1'][ind])<1e-6, \
            "H2O prediction prob: {0} and XGBoost prediction prob: {1}.  They are very " \
            "different.".format(h2oPredict[ind,'p1'], nativePred[ind])

def genDMatrix(h2oFrame, xlist, yresp):
    pandaFtrain = h2oFrame.as_data_frame(use_pandas=True, header=True)
    # need to change column 0 to categorical
    c0 = pd.get_dummies(pandaFtrain[yresp], prefix=yresp, drop_first=True)
    pandaFtrain.drop([yresp], axis=1, inplace=True)
    pandaF = pd.concat([c0, pandaFtrain], axis=1)
    pandaF.rename(columns={c0.columns[0]:yresp}, inplace=True)
    data = pandaF.as_matrix(xlist)
    label = pandaF.as_matrix([yresp])

    return xgb.DMatrix(data=data, label=label)

if __name__ == "__main__":
    pyunit_utils.standalone_test(higgs_compare_test)
else:
    higgs_compare_test()
