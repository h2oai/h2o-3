import sys
import h2o
sys.path.insert(1,"../../../")
from tests import pyunit_utils
from h2o.estimators.kmeans import H2OKMeansEstimator

#testing default setup of following parameters:
#distribution (available in Deep Learning, XGBoost, GBM):
#stopping_metric (available in: GBM, DRF, Deep Learning, AutoML, XGBoost, Isolation Forest):
#histogram_type (available in: GBM, DRF)
#solver (available in: GLM) already done in hex.glm.GLM.defaultSolver()
#categorical_encoding (available in: GBM, DRF, Deep Learning, K-Means, Aggregator, XGBoost, Isolation Forest)
#fold_assignment (available in: GBM, DRF, Deep Learning, GLM, Naïve-Bayes, K-Means, XGBoost)


def test_k_means_effective_parameters():
    cars = h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
    cars["year"] = cars["year"].asfactor()

    km1 = H2OKMeansEstimator(seed=1234, categorical_encoding="AUTO", nfolds=5)
    km1.train(x=["economy_20mpg", "displacement", "power", "weight", "acceleration", "year"], training_frame=cars)

    km2 = H2OKMeansEstimator(seed=1234, categorical_encoding="Enum", nfolds=5, fold_assignment='Random')
    km2.train(x=["economy_20mpg", "displacement", "power", "weight", "acceleration", "year"], training_frame=cars)

    assert km1.parms['categorical_encoding']['input_value'] == 'AUTO'
    assert km1.parms['categorical_encoding']['actual_value'] == km2.parms['categorical_encoding']['actual_value']
    assert km1.parms['fold_assignment']['input_value'] == 'AUTO'
    assert km1.parms['fold_assignment']['actual_value'] == km2.parms['fold_assignment']['actual_value']

    try:
        h2o.rapids("(setproperty \"{}\" \"{}\")".format("sys.ai.h2o.algos.evaluate_auto_model_parameters", "false"))
        km1 = H2OKMeansEstimator(seed=1234, categorical_encoding="AUTO", nfolds=5)
        km1.train(x=["economy_20mpg", "displacement", "power", "weight", "acceleration", "year"], training_frame=cars)

        assert km1.parms['categorical_encoding']['input_value'] == 'AUTO'
        assert km1.parms['categorical_encoding']['actual_value'] == 'AUTO'
        assert km1.parms['fold_assignment']['input_value'] == 'AUTO'
        assert km1.parms['fold_assignment']['actual_value'] == 'AUTO'
    finally:
        h2o.rapids("(setproperty \"{}\" \"{}\")".format("sys.ai.h2o.algos.evaluate_auto_model_parameters", "true"))


if __name__ == "__main__":
  pyunit_utils.standalone_test(test_k_means_effective_parameters)
else:
    test_k_means_effective_parameters()
