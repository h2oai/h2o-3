import pandas as pd
import sys
sys.path.insert(1,"../../../")
import random

from h2o.estimators.xgboost import *
from tests import pyunit_utils


'''
The goal of this test is to compare h2oxgboost runs with different random seeds and the results should be repeatable
when run with the same random seeds.

The same comparison is done to native XGBoost to make sure its results are repeatable with the same random seeds.
'''
def random_seeds_test():
    if sys.version.startswith("2"):
        print("native XGBoost tests only supported on python3")
        return
    import xgboost as xgb
    assert H2OXGBoostEstimator.available() is True
    ret = h2o.cluster()
    if len(ret.nodes) == 1:
        # train H2O XGBoost first
        higgs_h2o_train = h2o.import_file(pyunit_utils.locate('bigdata/laptop/higgs_train_imbalance_100k.csv'))
        higgs_h2o_train[0] = higgs_h2o_train[0].asfactor()
        higgs_h2o_test = h2o.import_file(pyunit_utils.locate('bigdata/laptop/higgs_test_imbalance_100k.csv'))
        higgs_h2o_test[0] = higgs_h2o_test[0].asfactor()
        myX = list(higgs_h2o_train.names)
        y = "response"
        myX.remove(y)
        # run with old same random seed
        h2oParams = {"ntrees":10, "max_depth":4, "learn_rate":0.7, "col_sample_rate_per_tree" : 0.9,
                     "min_rows" : 5, "score_tree_interval": 100, "seed":-12345}
        print("Model 1 trainged with old seed {0}.".format(h2oParams['seed']))
        # train model 1 with same seed from previous runs
        h2oModel1 = H2OXGBoostEstimator(**h2oParams)
        # gather, print and save performance numbers for h2o model
        h2oModel1.train(x=myX, y=y, training_frame=higgs_h2o_train)
        h2oPredict1 = h2oModel1.predict(higgs_h2o_test)

        h2oModel1_2 = H2OXGBoostEstimator(**h2oParams)
        # gather, print and save performance numbers for h2o model
        h2oModel1_2.train(x=myX, y=y, training_frame=higgs_h2o_train)
        h2oPredict1_2 = h2oModel1_2.predict(higgs_h2o_test)
        # run with new random seed
        seed2 = random.randint(1, 1073741824) # seed cannot be long, must be int size
        h2oParams2 = {"ntrees":100, "max_depth":10, "learn_rate":0.7, "col_sample_rate_per_tree" : 0.9,
                      "min_rows" : 5, "score_tree_interval": 100, "seed":seed2}
        print("Model 2 trainged with new seed {0}.".format(h2oParams2['seed']))
        h2oModel2 = H2OXGBoostEstimator(**h2oParams2)
        # gather, print and save performance numbers for h2o model
        h2oModel2.train(x=myX, y=y, training_frame=higgs_h2o_train)
        h2oPredict2 = h2oModel2.predict(higgs_h2o_test)

        # Result comparison in terms of prediction output.  In theory, h2oModel1 should be the same as saved run
        # compare the logloss
        assert abs(h2oModel1._model_json["output"]["training_metrics"]._metric_json["logloss"]-
                   h2oModel1_2._model_json["output"]["training_metrics"]._metric_json["logloss"])<1e-10, \
            "Model outputs should be the same with same seeds but are not!  Expected: {0}, actual: " \
            "{1}".format(h2oModel1._model_json["output"]["training_metrics"]._metric_json["logloss"],
                         h2oModel1_2._model_json["output"]["training_metrics"]._metric_json["logloss"])
        assert abs(h2oModel1._model_json["output"]["training_metrics"]._metric_json["logloss"]-
                        h2oModel2._model_json["output"]["training_metrics"]._metric_json["logloss"])>1e-10, \
            "Model outputs should be different with same seeds but are not!"

        # compare some prediction probabilities
        model1Pred = [h2oPredict1[0,"p1"], h2oPredict1[1,"p1"], h2oPredict1[2,"p1"], h2oPredict1[3,"p1"]]
        model1_2Pred = [h2oPredict1_2[0,"p1"], h2oPredict1_2[1,"p1"], h2oPredict1_2[2,"p1"], h2oPredict1_2[3,"p1"]]
        assert model1Pred==model1_2Pred, "Model 1 should have same predictions as previous with same seed but do not."
        try:
            pyunit_utils.compare_frames_local(h2oPredict1[['p0', 'p1']], h2oPredict2[['p0', 'p1']], prob=0.1, tol=1e-6) # should fail
            assert False, "Predict frames from two different seeds should be different but is not.  FAIL!"
        except:
            assert True

        # train multiple native XGBoost
        nativeTrain = genDMatrix(higgs_h2o_train, myX, y, xgb)
        nativeTest = genDMatrix(higgs_h2o_test, myX, y, xgb)
        h2o.remove_all()
        nativeParam = {'eta': h2oParams["learn_rate"], 'objective': 'binary:logistic', 'booster': 'gbtree',
                       'max_depth': h2oParams["max_depth"], 'seed': h2oParams["seed"],
                       'min_child_weight':h2oParams["min_rows"],
                       'colsample_bytree':h2oParams["col_sample_rate_per_tree"],'alpha':0.0, 'nrounds':h2oParams["ntrees"]}
        nativeModel1 = xgb.train(params=nativeParam,
                                dtrain=nativeTrain)
        nativePred1 = nativeModel1.predict(data=nativeTest)
        nativeModel1_2 = xgb.train(params=nativeParam,
                                 dtrain=nativeTrain)
        nativePred1_2 = nativeModel1_2.predict(data=nativeTest)

        nativeParam2 = {'eta': h2oParams["learn_rate"], 'objective': 'binary:logistic', 'booster': 'gbtree',
                       'max_depth': h2oParams["max_depth"], 'seed': h2oParams2["seed"],
                        'min_child_weight':h2oParams["min_rows"],
                        'colsample_bytree':h2oParams["col_sample_rate_per_tree"],'alpha':0.0, 'nrounds':h2oParams["ntrees"]}

        nativeModel2 = xgb.train(params=nativeParam2,
                             dtrain=nativeTrain ,
                             num_boost_round=h2oParams["ntrees"])
        nativePred2 = nativeModel2.predict(data=nativeTest)

        # nativeModel1 and nativeModel2 should generate the same results while nativeModel3 should provide different results
        # compare prediction probability and they should agree if they use the same seed
        nativePreds1_2 = [nativePred1_2[0], nativePred1_2[1], nativePred1_2[2], nativePred1_2[3]]
        nativePreds1 = [nativePred1[0], nativePred1[1], nativePred1[2], nativePred1[3]]
    #    plot_tree(nativeModel1,num_trees=4)
    #    plt.show()
        for ind in range(len(nativePreds1)):
            assert abs(nativePreds1_2[ind]-nativePreds1[ind])<1e-7, "Native XGBoost Model 1 should have same predictions" \
                                                                    " as previous with same seed but do not."
        for ind in range(4):
            assert abs(nativePred1[ind]-nativePred2[ind])>=1e-10, \
                "Native XGBoost model 1 prediction prob: {0} and native XGBoost model 3 prediction prob: {1}.  " \
                "They are too similar.".format(nativePred1[ind], nativePred2[ind])
    else:
        print("********  Test skipped.  This test cannot be performed in multinode environment.")

def genDMatrix(h2oFrame, xlist, yresp, xgb):
    pandaFtrain = h2oFrame.as_data_frame(use_pandas=True, header=True)
    # need to change column 0 to categorical
    c0 = pd.get_dummies(pandaFtrain[yresp], prefix=yresp, drop_first=True)
    pandaFtrain.drop([yresp], axis=1, inplace=True)
    pandaF = pd.concat([c0, pandaFtrain], axis=1)
    pandaF.rename(columns={c0.columns[0]:yresp}, inplace=True)
    data = pandaF[xlist].values
    label = pandaF[[yresp]].values

    return xgb.DMatrix(data=data, label=label)

if __name__ == "__main__":
    pyunit_utils.standalone_test(random_seeds_test)
else:
    random_seeds_test()
