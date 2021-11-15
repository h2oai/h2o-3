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
    covtype_df = h2o.import_file(pyunit_utils.locate("bigdata/laptop/covtype/covtype.full.csv"))
    #split the data as described above
    train, valid = covtype_df.split_frame([0.7], seed=1234)
    test=valid

    #Prepare predictors and response columns
    covtype_X = covtype_df.col_names[:-1]     #last column is Cover_Type, our desired response variable 
    covtype_y = covtype_df.col_names[-1]
    gam_multi = H2OGeneralizedAdditiveEstimator(family='multinomial', solver='IRLSM', lambda_search=True, nlambdas = 8,
                                                gam_columns=["Slope"], scale = [0.0001], num_knots=[5], standardize=True)
    gam_multi.train(covtype_X, covtype_y, training_frame=train, validation_frame=valid)
    gam_multi.summary()
    gam_tr_metrics = gam_multi._model_json['output']['training_metrics']._metric_json
    gam_va_metrics = gam_multi._model_json['output']['validation_metrics']._metric_json
    gam_te_metrics = gam_multi.model_performance(test_data=test)._metric_json
    glm_multi = H2OGeneralizedLinearEstimator(family='multinomial', solver='IRLSM', lambda_search=True, 
                                              standardize=True, nlambdas = 8)
    glm_multi.train(covtype_X, covtype_y, training_frame=train, validation_frame=valid)
    glm_multi.summary()
    glm_tr_metrics = glm_multi._model_json['output']['training_metrics']._metric_json
    glm_te_metrics = glm_multi.model_performance(test_data=test)._metric_json
    # check various model metrics between GLM and GAM
    print("******* GLM variable importance: ")
    print(glm_multi.varimp())
    print("******* GAM variable importance: ")
    print(gam_multi.varimp())
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
