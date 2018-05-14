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
def bigCat_test_hdfs():
    assert H2OXGBoostEstimator.available() is True

    hadoop_namenode_is_accessible = pyunit_utils.hadoop_namenode_is_accessible()
    if hadoop_namenode_is_accessible:
        hdfs_name_node = pyunit_utils.hadoop_namenode()

    trainFileList = ['/datasets/bigCatFiles/oneMillionCat10C.csv', '/datasets/bigCatFiles/oneMillionCat50C.csv',
                     '/datasets/bigCatFiles/oneMillionCat100C.csv'] # all categorical files
    h2oParams = {"ntrees":100, "max_depth":10, "seed":987654321, "learn_rate":0.7, "col_sample_rate_per_tree" : 0.9,
                 "min_rows" : 5, "score_tree_interval": 100}
    nativeParam = {'eta': h2oParams["learn_rate"], 'objective': 'binary:logistic', 'booster': 'gbtree',
                   'max_depth': h2oParams["max_depth"], 'seed': h2oParams["seed"], 'min_child_weight':h2oParams["min_rows"],
                   'colsample_bytree':h2oParams["col_sample_rate_per_tree"]}

    for fname in trainFileList:
        trainFile = genTrainFiles(fname, hdfs_name_node)     # load in dataset and add response column
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

def genTrainFiles(trainStr, hdfs_name_node):
    print("Import airlines_all.csv from HDFS")
    url = "hdfs://{0}{1}".format(hdfs_name_node, trainStr)
    trainFrame = h2o.import_file(url)
    yresponse = pyunit_utils.random_dataset_enums_only(trainFrame.nrow, 1, factorL=2, misFrac=0)
    yresponse.set_name(0,'response')
    trainFrame.cbind(yresponse)
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
    pyunit_utils.standalone_test(bigCat_test_hdfs)
else:
    bigCat_test_hdfs()
