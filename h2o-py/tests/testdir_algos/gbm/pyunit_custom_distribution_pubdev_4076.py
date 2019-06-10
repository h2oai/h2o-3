import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from tests.pyunit_utils import CustomDistributionGaussian, CustomDistributionGaussianWrong, \
    CustomDistributionBernoulli, CustomDistributionMultinomial, CustomDistributionNull
from tests.pyunit_utils import regression_model_distribution, regression_model_default
from tests.pyunit_utils import multinomial_model_default, multinomial_model_distribution
from tests.pyunit_utils import bernoulli_model_default, bernoulli_model_distribution

from h2o.estimators.gbm import H2OGradientBoostingEstimator


def custom_distribution_gaussian():
    return h2o.upload_custom_distribution(CustomDistributionGaussian, func_name="custom_gaussian", 
                                          func_file="custom_gaussian.py")


def custom_distribution_gaussian_w():
    return h2o.upload_custom_distribution(CustomDistributionGaussianWrong, func_name="custom_gaussian2", 
                                          func_file="custom_gaussian2.py")


def custom_distribution_bernoulli():
    return h2o.upload_custom_distribution(CustomDistributionBernoulli, func_name="custom_bernoulli",
                                          func_file="custom_bernoulli.py")


def custom_distribution_multinomial():
    return h2o.upload_custom_distribution(CustomDistributionMultinomial, func_name="custom_multinomial", 
                                          func_file="custom_multinomial.py")


def custom_distribution_null():
    return h2o.upload_custom_distribution(CustomDistributionNull, func_name="custom_null",
                                          func_file="custom_null.py")


def check_models(default_model, custom_model, model_type):
    shd = default_model.scoring_history()
    shc = custom_model.scoring_history()    
    for metric in shd:
        if metric in ["timestamp", "duration"]: 
            continue
        assert (shd[metric].isnull() == shc[metric].isnull()).all(), \
            "Scoring histroy is not the same for default and custom %s distribution and %s metric" % (model_type, metric)
        assert (shd[metric].dropna() == shc[metric].dropna()).all(), \
            "Scoring histroy is not the same for default and custom %s distribution and %s metric." % (model_type, metric)
    

# Test a custom distribution is computed correctly
def test_custom_metric_computation_regression():
    print("Create default gaussian model")
    (model, f_test) = regression_model_default(H2OGradientBoostingEstimator)
    print("Create custom gaussian model")
    (model2, f_test2) = regression_model_distribution(H2OGradientBoostingEstimator, custom_distribution_gaussian())
    check_models(model, model2, "gaussian")

    print("Create custom wrong gaussian model")
    (model3, f_test3) = regression_model_distribution(H2OGradientBoostingEstimator, custom_distribution_gaussian_w())
    assert model2.rmse(valid=False) != model3.rmse(valid=False), "Training rmse is not different for default and custom gaussian model."
    assert model2.rmse(valid=True) != model3.rmse(valid=True), "Validation rmse is not different for default and custom gaussian model."

    print("Create default bernoulli model")
    (model4, f_test4) = bernoulli_model_default(H2OGradientBoostingEstimator)
    print("Create custom binomial model")
    (model5, f_test5) = bernoulli_model_distribution(H2OGradientBoostingEstimator, custom_distribution_bernoulli())
    check_models(model4, model5, "gaussian")

    print("Create default multinomial model")
    (model6, f_test6) = multinomial_model_default(H2OGradientBoostingEstimator)
    print("Create custom multinomial model")
    (model7, f_test7) = multinomial_model_distribution(H2OGradientBoostingEstimator, custom_distribution_multinomial())
    check_models(model6, model7, "gaussian")
    
    print("Create custom null model")
    (model8, f_test8) = regression_model_distribution(H2OGradientBoostingEstimator, custom_distribution_null())
    print("rmse null model train:", model8.rmse(valid=False))
    print("rmse null model valid:", model8.rmse(valid=True))


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_custom_metric_computation_regression)
else:
    test_custom_metric_computation_regression()

