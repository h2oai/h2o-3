import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator

def test_gbm_mangus():

    train = pyunit_utils.random_dataset("regression")       # generate random dataset
    test = train.drop("response")
    xname = list(set(train.names) - {"response"})


    model_original = H2OGradientBoostingEstimator(ntrees=10,
                                               max_depth=2, col_sample_rate=0.8, sample_rate=0.7,
                                               stopping_rounds=3,
                                               seed=1234, score_tree_interval=10,
                                               learn_rate=0.1,
                                               stopping_metric="rmse")


    model_original.train(x=xname, y="response", training_frame=train)
    score_original_h2o = model_original.model_performance(test)
    print("H2O score on original test frame:")
    try:
        print(score_original_h2o)
    except:
        assert False, "Should not have failed here with empty model metrics message."

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gbm_mangus)
else:
    test_gbm_mangus()
