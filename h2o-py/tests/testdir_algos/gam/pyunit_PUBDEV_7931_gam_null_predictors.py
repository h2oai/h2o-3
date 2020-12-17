from __future__ import division
from __future__ import print_function
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator

# In this test, we check and make sure Gam can run without predictor columns as long as gam column is specified
def test_gam_null_predictors():
    print("Checking null predictor run for binomial")
    h2o_data = h2o.import_file(pyunit_utils.locate("smalldata/gam_test/gamBinomial1Col.csv"))
    buildModelMetricsCheck(h2o_data, 'binomial')

    print("Checking null predictor for gaussian")
    h2o_data = h2o.import_file(
    path=pyunit_utils.locate("smalldata/gam_test/gamGaussian1Col.csv"))
    buildModelMetricsCheck(h2o_data, 'gaussian')

    print("Checking null predictor for multinomial")
    h2o_data = h2o.import_file(
    path=pyunit_utils.locate("smalldata/gam_test/gamMultinomial1Col.csv"))
    buildModelMetricsCheck(h2o_data, 'multinomial')
    
    print("gam modelmetrics test completed successfully")    
    
def buildModelMetricsCheck(train_data, family):
    x = []
    y = "response"
    if not(family == 'gaussian'):
        train_data[y] = train_data[y].asfactor()
    frames = train_data.split_frame(ratios=[0.9], seed=12345)
    
    h2o_model = H2OGeneralizedAdditiveEstimator(family=family, gam_columns=["C1"])
    h2o_model.train(x=x, y=y, training_frame=frames[0], validation_frame=frames[1])

    h2o_model2 = H2OGeneralizedAdditiveEstimator(family=family, gam_columns=["C1"])
    h2o_model2.train(y=y, training_frame=frames[0], validation_frame=frames[1])

    # check and make sure coefficient does not contain predictor column
    coeffNames = h2o_model.coef().keys()
    assert not "C1" in coeffNames, "Not expecting C1 to be a coefficient but it is."
    
    # check and make sure both model produce the same metrics
    if family=='gaussian':
        assert h2o_model.mse() == h2o_model2.mse(), "Expected model MSE: {0}, Actual: {1}".format(h2o_model.mse(), 
                                                                                                  h2o_model2.mse())
    else:
        assert h2o_model.logloss() == h2o_model2.logloss(), "Expected model logloss: {0}, Actual: " \
                                                            "{1}".format(h2o_model.logloss(), h2o_model2.logloss())
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gam_null_predictors)
else:
    test_gam_null_predictors()
