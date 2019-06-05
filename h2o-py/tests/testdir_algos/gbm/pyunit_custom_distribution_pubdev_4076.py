import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from tests.pyunit_utils import CustomDistributionGaussian, CustomDistributionGaussianWrong, \
    CustomDistributionBinomial, CustomDistributionMultinomial
from tests.pyunit_utils import regression_model_distribution, regression_model_default
from tests.pyunit_utils import multinomial_model_default, multinomial_model_distribution
from tests.pyunit_utils import binomial_model_default, binomial_model_distribution

from h2o.estimators.gbm import H2OGradientBoostingEstimator


def custom_distribution_gaussian():
    return h2o.upload_custom_distribution(CustomDistributionGaussian, func_name="custom_gaussian", 
                                          func_file="custom_gaussian.py")


def custom_distribution_gaussian_w():
    return h2o.upload_custom_distribution(CustomDistributionGaussianWrong, func_name="custom_gaussian2", 
                                          func_file="custom_gaussian2.py")


def custom_distribution_binomial():
    return h2o.upload_custom_distribution(CustomDistributionBinomial, func_name="custom_binomial",
                                          func_file="custom_binomial.py")


def custom_distribution_multinomial():
    return h2o.upload_custom_distribution(CustomDistributionMultinomial, func_name="custom_multinomial", 
                                          func_file="custom_multinomial.py")


# Test a custom distribution is computed correctly
def test_custom_metric_computation_regression():
    print("Create default gaussian model")
    (model, f_test) = regression_model_default(H2OGradientBoostingEstimator)
    print("Create custom gaussian model")
    (model2, f_test2) = regression_model_distribution(H2OGradientBoostingEstimator, custom_distribution_gaussian())
    assert model.rmse(valid=False) == model2.rmse(valid=False)

    print("Create custom wrong gaussian model")
    (model3, f_test3) = regression_model_distribution(H2OGradientBoostingEstimator, custom_distribution_gaussian_w())
    assert model2.rmse(valid=False) != model3.rmse(valid=False)

    print("Create default binomial model")
    (model4, f_test4) = binomial_model_default(H2OGradientBoostingEstimator)
    print("Create custom binomial model")
    (model5, f_test5) = binomial_model_distribution(H2OGradientBoostingEstimator, custom_distribution_multinomial())
    print("rmse default:", model4.mse(valid=False), "rmse custom:", model5.mse(valid=False))
    # assert model4.rmse(valid=False) == model5.rmse(valid=False)

    print("Create default multinomial model")
    (model6, f_test6) = multinomial_model_default(H2OGradientBoostingEstimator)
    print("Create custom multinomial model")
    (model7, f_test7) = multinomial_model_distribution(H2OGradientBoostingEstimator, custom_distribution_multinomial())
    print("rmse default:", model6.mse(valid=False), "rmse custom:", model7.mse(valid=False))
    #assert model6.rmse(valid=False) == model7.rmse(valid=False)
    

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_custom_metric_computation_regression)
else:
    test_custom_metric_computation_regression()

