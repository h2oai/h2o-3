import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def test_AUCPR_early_stop():
    '''
    This pyunit test is written to ensure that the AUCPR can restrict the model training time for all
    h2o algos.  See https://github.com/h2oai/h2o-3/issues/8948.
    '''
    seed = 12345

    # GBM run
    cars = h2o.upload_file(pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    cars = cars[cars["economy_20mpg"].isna() == 0]
    cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()

    gbm = H2OGradientBoostingEstimator(seed=seed)
    gbm.train(x=list(range(2,8)),y="economy_20mpg", training_frame=cars)
    gbmEarlyStop = H2OGradientBoostingEstimator(seed=seed, stopping_metric="AUCPR", stopping_tolerance=0.01, 
                                                stopping_rounds=5)
    gbmEarlyStop.train(x=list(range(2,8)),y="economy_20mpg", training_frame=cars)
    
    numTrees = pyunit_utils.extract_from_twoDimTable(gbm._model_json["output"]["model_summary"],
                                                     "number_of_trees", takeFirst=True)
    numTreesEarlyStop = pyunit_utils.extract_from_twoDimTable(gbmEarlyStop._model_json["output"]["model_summary"],
                                                              "number_of_trees", takeFirst=True)
    print("Number of tress built with early stopping: {0}.  Number of trees built without early stopping: "
          "{1}".format(numTreesEarlyStop[0], numTrees[0]))
    assert numTreesEarlyStop[0] <= numTrees[0], "Early stopping criteria AUCPR is not working."


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_AUCPR_early_stop)
else:
    test_AUCPR_early_stop()
