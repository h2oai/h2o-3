import sys
sys.path.insert(1,"../../../")

from h2o.estimators.xgboost import *
from tests import pyunit_utils

'''
PUBDEV-6117: test H2OXGBoost and native XGBoost comparison.
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
        data= h2o.import_file(pyunit_utils.locate("smalldata/jira/adult_data_modified.csv"))
        data[14] = data[14].asfactor()
        myX = list(range(0, 13)) # use column indices
        print(myX)
        y='income'
        h2oParamsD = {"ntrees":30, "max_depth":4, "seed":2, "learn_rate":0.7,
              "col_sample_rate_per_tree" : 0.9, "min_rows" : 5, "score_tree_interval": 30+1,
              "tree_method": "exact", "backend":"cpu"}

        h2oModelD = H2OXGBoostEstimator(**h2oParamsD)
        # gather, print and save performance numbers for h2o model
        h2oModelD.train(x=myX, y=y, training_frame=data)
        h2oPredictD = h2oModelD.predict(data)

        nativeXGBoostParam = h2oModelD.convert_H2OXGBoostParams_2_XGBoostParams()
        nativeXGBoostInput = data.convert_H2OFrame_2_DMatrix(myX, y, h2oModelD)
        
        nativeModel = xgb.train(params=nativeXGBoostParam[0],
                                dtrain=nativeXGBoostInput, num_boost_round=nativeXGBoostParam[1])
        nativePred = nativeModel.predict(data=nativeXGBoostInput, ntree_limit=nativeXGBoostParam[1])
        pyunit_utils.summarizeResult_binomial(h2oPredictD, nativePred, -1, -1, -1,
                                              -1, tolerance=1e-6)
    else:
        print("********  Test skipped.  This test cannot be performed in multinode environment.")

if __name__ == "__main__":
    pyunit_utils.standalone_test(comparison_test)
else:
    comparison_test()
