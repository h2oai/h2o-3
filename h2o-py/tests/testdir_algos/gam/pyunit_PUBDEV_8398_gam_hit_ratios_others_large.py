from __future__ import division
from __future__ import print_function
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


# In this test, we check to make sure model metrics are calculated correctly for GLM multinomial are also done for GAM
def test_gam_model_predict():
    loan_df = h2o.import_file(pyunit_utils.locate("bigdata/laptop/lending-club/loan.csv"))
    #split the data as described above
    train, valid = loan_df.split_frame([0.7], seed=1234)
    test=valid

    #Prepare predictors and response columns
    y = 'bad_loan'
    X = [name for name in train.columns if name != y]
    train[y] = train[y].asfactor()
    test[y] = test[y].asfactor()
    gam_binomial = H2OGeneralizedAdditiveEstimator(family='binomial', solver='IRLSM', lambda_search=True, nlambdas = 8,
                                                gam_columns=["int_rate"], scale = [0.0001], num_knots=[5], standardize=True,
                                                   bs=[2])
    gam_binomial.train(X, y, training_frame=train, validation_frame=valid)
    gam_binomial.summary()
    gam_tr_metrics = gam_binomial._model_json['output']['training_metrics']._metric_json
    gam_va_metrics = gam_binomial._model_json['output']['validation_metrics']._metric_json
    gam_te_metrics = gam_binomial.model_performance(test_data=test)._metric_json
    glm_binomial = H2OGeneralizedLinearEstimator(family='binomial', solver='IRLSM', lambda_search=True, 
                                              standardize=True, nlambdas = 8)
    glm_binomial.train(X, y, training_frame=train, validation_frame=valid)
    glm_binomial.summary()
    glm_tr_metrics = glm_binomial._model_json['output']['training_metrics']._metric_json
    glm_te_metrics = glm_binomial.model_performance(test_data=test)._metric_json
    # check various model metrics between GLM and GAM
    print("******* GLM variable importance: ")
    print(glm_binomial.varimp())
    print("******* GAM variable importance: ")
    print(gam_binomial.varimp())
    assert_va_test_metrics_eq(glm_tr_metrics['logloss'], glm_te_metrics['logloss'], gam_tr_metrics['logloss'], 
                              gam_va_metrics['logloss'], gam_te_metrics['logloss'], 'logloss')
    assert_va_test_metrics_eq(glm_tr_metrics['MSE'], glm_te_metrics['MSE'], gam_tr_metrics['MSE'],
                              gam_va_metrics['MSE'], gam_te_metrics['MSE'], 'MSE')
    assert_va_test_metrics_eq(glm_tr_metrics['r2'], glm_te_metrics['r2'], gam_tr_metrics['r2'],
                              gam_va_metrics['r2'], gam_te_metrics['r2'], 'r2')
    assert_va_test_metrics_eq(glm_tr_metrics['mean_per_class_error'], glm_te_metrics['mean_per_class_error'], 
                              gam_tr_metrics['mean_per_class_error'],gam_va_metrics['mean_per_class_error'], 
                              gam_te_metrics['mean_per_class_error'], 'mean_per_class_error')

def assert_va_test_metrics_eq(glmtrmetrics, glmtemetrics, trmetrics, vmetrics, tmetrics, metrictitle):
    print("GLM training {2}: {0}, GAM training {2}: {1}".format(glmtrmetrics, trmetrics, metrictitle))
    print("GLM test {2}: {0}, GAM test {2}: {1}".format(glmtemetrics, tmetrics, metrictitle))
    assert abs(vmetrics-tmetrics)<1e-6, "Gam validation {0}: {1}, Gam test {0}: {2}".format(metrictitle, vmetrics, tmetrics)
    

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gam_model_predict)
else:
    test_gam_model_predict()
