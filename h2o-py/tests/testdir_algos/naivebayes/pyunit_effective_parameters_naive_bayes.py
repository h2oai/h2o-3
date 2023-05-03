import sys
import h2o
sys.path.insert(1,"../../../")
from tests import pyunit_utils
from h2o.estimators.naive_bayes import H2ONaiveBayesEstimator

#testing default setup of following parameters:
#distribution (available in Deep Learning, XGBoost, GBM):
#stopping_metric (available in: GBM, DRF, Deep Learning, AutoML, XGBoost, Isolation Forest):
#histogram_type (available in: GBM, DRF)
#solver (available in: GLM) already done in hex.glm.GLM.defaultSolver()
#categorical_encoding (available in: GBM, DRF, Deep Learning, K-Means, Aggregator, XGBoost, Isolation Forest)
#fold_assignment (available in: GBM, DRF, Deep Learning, GLM, Naïve-Bayes, K-Means, XGBoost)

def test_naive_bayes_effective_parameters():
    cars = h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
    cars["year"] = cars["year"].asfactor()
    predictors = ["displacement", "power", "weight", "acceleration", "year"]
    response = "economy_20mpg"

    nb = H2ONaiveBayesEstimator(min_sdev=0.1, eps_sdev=0.5, seed=1234, nfolds=5)
    nb.train(x=predictors, y=response, training_frame=cars)

    assert nb.parms['fold_assignment']['input_value'] == 'AUTO'
    assert nb.parms['fold_assignment']['actual_value'] == 'Random'
    
    try:
        h2o.rapids("(setproperty \"{}\" \"{}\")".format("sys.ai.h2o.algos.evaluate_auto_model_parameters", "false"))
        nb = H2ONaiveBayesEstimator(min_sdev=0.1, eps_sdev=0.5, seed=1234, nfolds=5)
        nb.train(x=predictors, y=response, training_frame=cars)

        assert nb.parms['fold_assignment']['input_value'] == 'AUTO'
        assert nb.parms['fold_assignment']['actual_value'] == 'AUTO'
    finally:
        h2o.rapids("(setproperty \"{}\" \"{}\")".format("sys.ai.h2o.algos.evaluate_auto_model_parameters", "true"))
    
if __name__ == "__main__":
  pyunit_utils.standalone_test(test_naive_bayes_effective_parameters)
else:
    test_naive_bayes_effective_parameters()
